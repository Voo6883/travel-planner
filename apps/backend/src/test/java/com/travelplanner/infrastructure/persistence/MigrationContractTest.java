package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
    void everyAgentMutableAggregateNamedByAdr008HasAVersionColumn() {
        assertThat(read("V5__create_trip_table.sql")).contains("version");
        assertThat(read("V6__create_trip_brief_table.sql")).contains("version");
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

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).describedAs("marker '%s' not found", start).isNotNegative();
        int to = source.indexOf(end, from + start.length());
        assertThat(to).describedAs("marker '%s' not found", end).isNotNegative();
        return source.substring(from + start.length(), to);
    }
}
