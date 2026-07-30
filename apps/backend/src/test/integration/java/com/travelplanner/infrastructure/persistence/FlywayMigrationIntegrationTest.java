package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the migrations apply to an empty database and that the resulting schema is the one the
 * ADRs and the entities assume.
 *
 * <p>The context has already started by the time these run, which means Flyway has already migrated
 * and Hibernate has already passed {@code ddl-auto=validate} against the result. Both of those are
 * assertions in their own right — a broken migration or an entity that disagrees with a column
 * fails before a single test method executes.
 */
class FlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void everyMigrationAppliedInOrderAndValidatesAgainstItsChecksum() {
        List<MigrationInfo> applied = List.of(flyway.info().applied());

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(info ->
                assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).isEmpty();

        // `validate` recomputes every checksum. It throws rather than returning a value, so the
        // absence of an exception is the assertion.
        flyway.validate();
    }

    @Test
    void thePgvectorExtensionIsInstalled() {
        assertThat(scalar("select count(*) from pg_extension where extname = 'vector'"))
                .isEqualTo("1");
    }

    @Test
    void createsExactlyTheTablesThisTaskOwnsAndNoneBelongingToLaterFeatures() {
        List<String> tables = query(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' "
                        + "order by table_name");

        assertThat(tables).contains(
                "refresh_token", "trip", "trip_brief", "user", "user_identity");
        // The brief forbids creating a schema before its own task. This list has SHRUNK twice, and
        // both times legitimately: task 16 landed the knowledge tables (V13–V18) and task 20 the chat
        // ones (V19), so `destination`, `poi`, `conversation` and `message` moved from "must not
        // exist" to "exists, owned by that task".
        //
        // It was not shrunk at the time, so this assertion had been failing since task 16 — invisibly,
        // because the Testcontainers suite needs Docker and this project's machine has none. What is
        // left is what genuinely has no migration yet; when task 23 or 28 lands, remove its entry
        // here in the same change rather than discovering this again.
        assertThat(tables).doesNotContain(
                "booking", "itinerary_day", "itinerary_item", "ranked_recommendation",
                "research_job");
    }

    @Test
    void everyTimestampColumnIsTimestamptz() {
        // flyway_schema_history is excluded: its shape is Flyway's, not ours, and `installed_on`
        // is a bare timestamp we cannot change.
        List<String> withoutTimeZone = query(
                "select table_name || '.' || column_name from information_schema.columns "
                        + "where table_schema = 'public' "
                        + "and table_name <> 'flyway_schema_history' "
                        + "and data_type = 'timestamp without time zone'");

        assertThat(withoutTimeZone)
                .describedAs("PLAN §4.0.2-H requires timestamptz; a bare timestamp means whatever "
                        + "the writing session's timezone happened to be")
                .isEmpty();
    }

    @Test
    void moneyColumnsAreNumericRatherThanFloatingPoint() {
        List<String> budgetTypes = query(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and column_name like 'budget_amount'");

        assertThat(budgetTypes).isNotEmpty().allSatisfy(type -> assertThat(type).isEqualTo("numeric"));
    }

    @Test
    void theRevocationColumnsFromAdr009Exist() {
        assertThat(columnsOf("user")).contains("token_version", "sessions_valid_after");
        assertThat(columnsOf("refresh_token"))
                .contains("token_hash", "expires_at", "rotated_at", "revoked_at");
    }

    @Test
    void theAgentMutableAggregatesFromAdr008CarryAVersionColumn() {
        assertThat(columnsOf("trip")).contains("version");
        assertThat(columnsOf("trip_brief")).contains("version");
    }

    @Test
    void identityAndCredentialUniquenessIsCaseInsensitive() {
        // ADR 009 §4. A plain UNIQUE(email) would let Alice@x.com and alice@x.com both register.
        List<String> indexes = query(
                "select indexdef from pg_indexes where schemaname = 'public' and tablename = 'user'");

        // Postgres re-renders the expression with an explicit cast, e.g. `lower((email)::text)`,
        // so the assertion matches on the function and the column rather than on exact text.
        assertThat(indexes).anySatisfy(definition ->
                assertThat(definition).contains("lower(").contains("email"));
        assertThat(indexes).anySatisfy(definition ->
                assertThat(definition).contains("lower(").contains("username"));
    }

    private List<String> columnsOf(String table) {
        return query("select column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name = '" + table + "'");
    }

    private String scalar(String sql) {
        List<String> rows = query(sql);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> query(String sql) {
        try (Connection connection = dataSource.getConnection();
                ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            List<String> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(resultSet.getString(1));
            }
            return rows;
        } catch (SQLException failure) {
            throw new IllegalStateException("query failed: " + sql, failure);
        }
    }
}
