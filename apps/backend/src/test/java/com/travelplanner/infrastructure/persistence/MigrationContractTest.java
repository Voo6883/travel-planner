package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.AdminAction;
import com.travelplanner.domain.enums.AdminActionResult;
import com.travelplanner.domain.enums.ConversationScope;
import com.travelplanner.domain.enums.ConversationState;
import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.enums.TripStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Static checks on the migration files. Deliberately reads the SQL as text so that it runs in the
 * unit suite — no Docker, no database. {@code FlywayMigrationIntegrationTest} proves the same files
 * actually apply; this proves they say what the Java says before anyone starts a container.
 */
class MigrationContractTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern FILE_NAME = Pattern.compile("V(\\d+)__[a-z0-9_]+\\.sql");

    @Test
    void everyFileFollowsTheVersionedNamingConventionWithNoGapsOrDuplicates() {
        List<String> names = migrationFileNames();

        assertThat(names).isNotEmpty();
        assertThat(names).allSatisfy(name ->
                assertThat(FILE_NAME.matcher(name).matches())
                        .describedAs("%s must match V{n}__{snake_case}.sql (PLAN §4.0.2-H)", name)
                        .isTrue());

        List<Integer> versions = names.stream().map(MigrationContractTest::versionOf).sorted().toList();
        // Contiguous from 1. A gap means a migration was deleted after being applied somewhere,
        // and a duplicate means two branches picked the same number — Flyway would reject the
        // second one at deploy time, which is far too late to find out.
        assertThat(versions).doesNotHaveDuplicates();
        assertThat(versions).containsExactlyElementsOf(
                Stream.iterate(1, version -> version + 1).limit(versions.size()).toList());
    }

    @Test
    void theTripStatusCheckConstraintListsExactlyTheDomainEnumConstants() {
        String tripMigration = read("V5__create_trip_table.sql");
        String checkBody = between(tripMigration, "CONSTRAINT ck_trip_status CHECK (status IN (", "))");

        List<String> inSql = Stream.of(checkBody.split(","))
                .map(value -> value.replace("'", "").trim())
                .filter(value -> !value.isEmpty())
                .toList();

        assertThat(inSql).containsExactlyInAnyOrderElementsOf(
                Stream.of(TripStatus.values()).map(Enum::name).toList());
    }

    @Test
    void moneyIsNeverStoredAsAFloatingPointType() {
        // PLAN §4.0.2-A. `real`, `double precision`, and `float` are all binary floating point and
        // cannot represent 4000.10 exactly.
        assertThat(allSql())
                .describedAs("money must be numeric, never float (PLAN §4.0.2-A)")
                .doesNotContain("double precision", " real,", " float");
        assertThat(read("V6__create_trip_brief_table.sql")).contains("numeric(19, 4)");
    }

    @Test
    void everyTimestampColumnCarriesATimeZone() {
        // A bare `timestamp` has no offset, so its meaning depends on the session timezone of
        // whoever wrote it. PLAN §4.0.2-H requires timestamptz throughout.
        String sql = allSql();
        Matcher bare = Pattern.compile("\\btimestamp\\b(?!tz)").matcher(sql);

        assertThat(bare.find())
                .describedAs("found a bare `timestamp` column; use timestamptz")
                .isFalse();
        assertThat(sql).contains("timestamptz");
    }

    @Test
    void everyMutableTableHasAuditColumns() {
        // PLAN §4.0.2-H: created_at and updated_at on all mutable tables. user_identity and
        // refresh_token are append-only apart from explicit lifecycle columns, so they carry
        // created_at only.
        assertThat(read("V2__create_user_table.sql")).contains("created_at").contains("updated_at");
        assertThat(read("V5__create_trip_table.sql")).contains("created_at").contains("updated_at");
        assertThat(read("V6__create_trip_brief_table.sql")).contains("created_at").contains("updated_at");
    }

    @Test
    void theRevocationColumnsRequiredByAdr009Exist() {
        String userMigration = read("V2__create_user_table.sql");

        assertThat(userMigration).contains("token_version");
        assertThat(userMigration).contains("sessions_valid_after");
        assertThat(read("V4__create_refresh_token_table.sql"))
                .contains("token_hash")
                .contains("rotated_at")
                .contains("revoked_at");
    }

    @Test
    void theLockoutWindowIsStoredInTheDatabaseAndKeyedOnBothIdentifierAndAddress() {
        // ADR 009 §6 rules out an in-memory counter: it would reset on deploy and count separately
        // on every instance, which breaks the locked stateless/multi-instance goal. Keying on the
        // identifier alone would make the lockout a targeted denial-of-service weapon.
        String lockoutMigration = read("V7__create_login_attempt_table.sql");

        assertThat(lockoutMigration).contains("CREATE TABLE login_attempt");
        assertThat(lockoutMigration).contains("login_identifier").contains("client_ip");
        assertThat(lockoutMigration)
                .describedAs("the lockout read must be one indexed lookup on the composite key")
                .contains("ON login_attempt (login_identifier, client_ip, attempted_at)");
    }

    @Test
    void mailedTokensAreStoredHashedSingleUseAndExpiring() {
        // §4.0.10 and ADR 004. Storing the raw token would make a database dump a list of working
        // password-reset links; without `consumed_at` a link would be redeemable forever.
        String tokenMigration = read("V8__create_account_token_table.sql");

        assertThat(tokenMigration).contains("CREATE TABLE account_token");
        assertThat(tokenMigration).contains("token_hash").contains("expires_at")
                .contains("consumed_at");
        assertThat(tokenMigration)
                .describedAs("the redemption lookup must be one indexed equality match")
                .contains("CREATE UNIQUE INDEX ux_account_token_hash ON account_token (token_hash)");
        assertThat(stripComments(tokenMigration))
                .describedAs("a raw token column would defeat hashing at rest")
                .doesNotContain("token_value")
                .doesNotContain("raw_token");
    }

    @Test
    void theMailRateLimitStoresDigestsRatherThanAddresses() {
        // ADR 009 §6 requires the limit; PLAN §4.0.10 keeps recipient PII out of the system's
        // records. A plaintext address column would put back exactly what the logging rule removes.
        String limitMigration = read("V9__create_mail_rate_limit_table.sql");

        assertThat(limitMigration).contains("CREATE TABLE mail_rate_limit");
        assertThat(limitMigration).contains("subject_hash").contains("scope");
        assertThat(stripComments(limitMigration)).doesNotContain("email varchar");
        assertThat(limitMigration)
                .contains("ON mail_rate_limit (scope, subject_hash, requested_at)");
    }

    @Test
    void aSoftDeletedAccountCanNeverKeepACredential() {
        // UC-A14. The application anonymises; this makes "deleted implies no password" a schema
        // rule, so no later code path can put a hash back on a closed account.
        String softDelete = read("V10__add_user_soft_delete.sql");

        assertThat(softDelete).contains("deleted_at");
        assertThat(softDelete).contains("ck_user_deleted_has_no_password");
        assertThat(softDelete)
                .describedAs("the row must survive: every trip references it (PLAN §8)")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DELETE FROM");
    }

    @Test
    void theAdminAuditTableRecordsWhoDidWhatToWhomAndNothingElse() {
        // PLAN §4.0.6. The columns are the four facts an investigation needs; what is absent is the
        // point, and it is absent by shape rather than by discipline — AdminAuditEvent has no field
        // able to carry a credential, so adding one would mean changing four files including this.
        String auditMigration = read("V12__create_audit_event_table.sql");

        assertThat(auditMigration).contains("CREATE TABLE audit_event");
        assertThat(auditMigration).contains("actor_user_id").contains("target_user_id")
                .contains("action").contains("result").contains("created_at");
        // The column list only. The trailing COMMENT ON statements say the table holds no password,
        // and a naive scan of the whole file would fail on its own promise.
        assertThat(between(stripComments(auditMigration), "CREATE TABLE audit_event (", ");"))
                .describedAs("an audit row must never be able to hold a credential or an address")
                .doesNotContain("password")
                .doesNotContain("email");
        assertThat(auditMigration)
                .describedAs("both investigation queries must be one indexed lookup")
                .contains("ON audit_event (target_user_id, created_at DESC)")
                .contains("ON audit_event (actor_user_id, created_at DESC)");
    }

    @Test
    void theAuditActionAndResultConstraintsListExactlyTheDomainEnumConstants() {
        String auditMigration = read("V12__create_audit_event_table.sql");

        assertThat(constantsIn(auditMigration, "CONSTRAINT ck_audit_event_action\n"
                + "        CHECK (action IN (")).containsExactlyInAnyOrderElementsOf(
                        Stream.of(AdminAction.values()).map(Enum::name).toList());
        assertThat(constantsIn(auditMigration, "CONSTRAINT ck_audit_event_result\n"
                + "        CHECK (result IN (")).containsExactlyInAnyOrderElementsOf(
                        Stream.of(AdminActionResult.values()).map(Enum::name).toList());
    }

    @Test
    void noMigrationSeedsAnAccountOrCarriesACredential() {
        // PLAN §4.0.6 "do not store the plaintext seed password in migration output". The dev admin
        // is created by a @Profile-gated bean instead, which is also why there is no SQL here for a
        // production deployment to run by accident.
        String sql = stripComments(allSql()).toLowerCase(Locale.ROOT);

        assertThat(sql)
                .describedAs("the dev admin seed belongs to DevAdminSeeder, never to a migration")
                .doesNotContain("insert into \"user\"")
                .doesNotContain("123456")
                .doesNotContain("$2a$")
                .doesNotContain("$2b$");
    }

    @Test
    void everyAgentMutableAggregateNamedByAdr008HasAVersionColumn() {
        assertThat(read("V5__create_trip_table.sql")).contains("version");
        assertThat(read("V6__create_trip_brief_table.sql")).contains("version");
    }

    @Test
    void chatOrderingIsASequenceRatherThanATimestamp() {
        // tasks/20 Definition of Done: "Durable chat messages reload in order." A created_at cannot
        // give that — Postgres fixes now() for a whole transaction, so every row one turn writes
        // shares a value and ORDER BY created_at over them is undefined, not merely imprecise.
        // ADR 007 also needs the column as a resume cursor ("Client sends Last-Event-ID; server
        // replays persisted frames after that id").
        String chatMigration = read("V19__create_conversation_tables.sql");

        assertThat(chatMigration).contains("CREATE TABLE message");
        // Column-shape assertions run against whitespace-normalised SQL: the migrations align
        // their column types into visual columns, and a test that depended on that alignment would
        // fail the next time somebody added a longer column name.
        assertThat(normalised(chatMigration)).contains("seq bigint NOT NULL");
        assertThat(chatMigration)
                .describedAs("uniqueness is what makes the ordering a guarantee, not an intention")
                .contains("CONSTRAINT uq_message_conversation_seq UNIQUE (conversation_id, seq)");
        assertThat(normalised(chatMigration))
                .describedAs("the allocator lives on the parent so one row lock serialises appends")
                .contains("next_message_seq bigint NOT NULL DEFAULT 1");
    }

    @Test
    void aRetriedChatSendCannotCommitTwice() {
        // tasks/20 Definition of Done: "Disconnect/retry cannot duplicate committed user messages."
        // Look-then-insert alone cannot promise that — two retries racing after a dropped response
        // would both pass the lookup — so the unique index is the actual guarantee.
        String chatMigration = read("V19__create_conversation_tables.sql");

        assertThat(normalised(chatMigration)).contains("client_message_id varchar(64)");
        assertThat(chatMigration).contains(
                "CONSTRAINT uq_message_conversation_client_id UNIQUE (conversation_id, client_message_id)");
        assertThat(chatMigration)
                .describedAs("only a client-sent message can be retried, so only it carries a key")
                .contains("CONSTRAINT ck_message_client_id_is_user_only");
    }

    @Test
    void aPartialAssistantMessageIsMarkedRatherThanDiscarded() {
        // tasks/20: partial assistant messages must be "explicitly marked or safely discarded".
        // ADR 007 chose marking: "Partial assistant message persisted with status=interrupted;
        // never silently discarded".
        String chatMigration = read("V19__create_conversation_tables.sql");

        assertThat(chatMigration).contains("'INTERRUPTED'");
        assertThat(chatMigration).contains("CONSTRAINT ck_message_completed_at_matches_status");
    }

    @Test
    void theChatSchemaHasNowhereToPutChainOfThought() {
        // tasks/20 Definition of Done: "No hidden chain-of-thought is stored or returned", and its
        // Do-not list: "Do not expose internal reasoning or raw provider events". The guarantee is
        // structural — there is no column able to hold a reasoning trace, so storing one would mean
        // changing the migration, the entity, the mapper and the domain record.
        String columns = between(stripComments(read("V19__create_conversation_tables.sql")),
                "CREATE TABLE message (", ");").toLowerCase(Locale.ROOT);

        assertThat(columns)
                .doesNotContain("reasoning")
                .doesNotContain("thinking")
                .doesNotContain("thought")
                .doesNotContain("scratchpad")
                .doesNotContain("raw_provider")
                .doesNotContain("provider_event");
    }

    @Test
    void everyChatReadCanBeScopedToItsOwner() {
        // PLAN §4.0.2-L. `message` deliberately carries no user_id — the owner lives on
        // `conversation`, and duplicating it would create a second source of truth — so the two
        // parent tables are the ones that must be indexed by owner.
        String chatMigration = read("V19__create_conversation_tables.sql");

        assertThat(chatMigration).contains("ON conversation (user_id, created_at DESC)");
        assertThat(chatMigration).contains("ON planner_session (user_id, created_at DESC)");
        assertThat(between(stripComments(chatMigration), "CREATE TABLE message (", ");"))
                .describedAs("a duplicated owner column could disagree with the conversation's")
                .doesNotContain("user_id");
    }

    @Test
    void aTripHasAtMostOneConversationAndAUserAtMostOneOpenPlannerSession() {
        // PLAN §3.2: "one persistent conversation from create_trip until archived". Both are
        // partial-by-NULL or explicitly partial, so planner threads and closed sessions stay
        // unconstrained — the asymmetry is the product requirement, not an accident.
        String chatMigration = read("V19__create_conversation_tables.sql");

        assertThat(chatMigration).contains("CONSTRAINT uq_conversation_trip_id UNIQUE (trip_id)");
        assertThat(chatMigration).contains(
                "CREATE UNIQUE INDEX uq_planner_session_user_open ON planner_session (user_id) "
                        + "WHERE ended_at IS NULL");
    }

    @Test
    void theChatCheckConstraintsListExactlyTheDomainEnumConstants() {
        String chatMigration = read("V19__create_conversation_tables.sql");

        assertThat(constantsIn(chatMigration, "CONSTRAINT ck_conversation_scope CHECK (scope IN ("))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(ConversationScope.values()).map(Enum::name).toList());
        assertThat(constantsIn(chatMigration, "CONSTRAINT ck_conversation_state CHECK (state IN ("))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(ConversationState.values()).map(Enum::name).toList());
        assertThat(constantsIn(chatMigration, "CONSTRAINT ck_message_role CHECK (role IN ("))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(ChatMessageRole.values()).map(Enum::name).toList());
        assertThat(constantsIn(chatMigration, "CONSTRAINT ck_message_status CHECK (status IN ("))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(ChatMessageStatus.values()).map(Enum::name).toList());
    }

    @Test
    void theBaselineEnablesPgvector() {
        assertThat(read("V1__enable_extensions.sql").toLowerCase(Locale.ROOT))
                .contains("create extension if not exists vector");
    }

    private static List<String> migrationFileNames() {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * Concatenated DDL with {@code --} comments removed. The comments explain <em>why</em> a column
     * is numeric and not float, so leaving them in would make a test for "no float" fail on its own
     * rationale.
     */
    private static String allSql() {
        return migrationFileNames().stream()
                .sorted(Comparator.comparingInt(MigrationContractTest::versionOf))
                .map(MigrationContractTest::read)
                .map(MigrationContractTest::stripComments)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    /**
     * The same SQL with every run of whitespace collapsed to one space. Column declarations are
     * visually aligned in these files, so an assertion on a declaration must not depend on how many
     * spaces the alignment currently needs — that would turn "somebody added a longer column name"
     * into a failing contract test.
     */
    private static String normalised(String sql) {
        return sql.replaceAll("\\s+", " ");
    }

    private static String stripComments(String sql) {
        return sql.lines()
                .map(line -> {
                    int comment = line.indexOf("--");
                    return comment < 0 ? line : line.substring(0, comment);
                })
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static int versionOf(String fileName) {
        Matcher matcher = FILE_NAME.matcher(fileName);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String read(String fileName) {
        try {
            return Files.readString(MIGRATIONS.resolve(fileName));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The quoted constants of a {@code CHECK (col IN ('A', 'B'))} clause, in file order. */
    private static List<String> constantsIn(String sql, String marker) {
        return Stream.of(between(sql, marker, "))").split(","))
                .map(value -> value.replace("'", "").trim())
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).describedAs("marker '%s' not found", start).isNotNegative();
        int to = source.indexOf(end, from + start.length());
        assertThat(to).describedAs("marker '%s' not found", end).isNotNegative();
        return source.substring(from + start.length(), to);
    }
}
