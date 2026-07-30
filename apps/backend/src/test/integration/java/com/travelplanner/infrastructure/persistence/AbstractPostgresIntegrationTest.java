package com.travelplanner.infrastructure.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for every test that needs a real PostgreSQL.
 *
 * <p><b>The image is {@code pgvector/pgvector:pg16} — the same one {@code docker-compose.yml} runs.</b>
 * Testing against plain {@code postgres:16} would be cheaper and would pass, right up until V1
 * reached an environment where {@code CREATE EXTENSION vector} was the difference between a working
 * deployment and a failed one. {@code asCompatibleSubstituteFor} is what lets Testcontainers accept
 * a non-official image for its Postgres module.
 *
 * <p>The container is a plain static field started in a static initialiser rather than a
 * {@code @Container} field. A {@code @Container} static field is started and stopped per test
 * <em>class</em>; this way one container serves the whole suite and Ryuk reaps it when the JVM
 * exits. Migrations then run once per Spring context instead of once per class, which is also what
 * makes "applies from an empty database" a real assertion — the first context to start meets a
 * genuinely empty database.
 *
 * <p>Public since task 08 so the API-level auth suite in {@code com.travelplanner.api.auth} can
 * extend it. Sharing this class rather than declaring a second container is the point: two
 * container definitions would mean two PostgreSQL instances and two migration runs per build.
 *
 * <h2>Running without Docker</h2>
 *
 * <p>Set {@code INTEGRATION_TEST_JDBC_URL} (with {@code _USERNAME} and {@code _PASSWORD}) and this
 * suite talks to that database instead of starting a container. {@code npm run test:integration}
 * does it for you against a locally-installed PostgreSQL.
 *
 * <p><b>Why this escape hatch exists, and what it is not.</b> Docker Desktop is unavailable on some
 * development machines — {@code docs/HANDOFF-REMAINING-WORK.md} §4.8 records the specific failure on
 * this project's — and the alternative was that the entire Testcontainers suite went unrun there. A
 * migration that "compiles" is not a migration that applies, and V21 shipped with exactly that
 * caveat attached. An unrun test is worth less than a test run against a database somebody set up by
 * hand.
 *
 * <p>It is <b>not</b> a replacement for the container in CI. CI has Docker and keeps using it, because
 * a container guarantees a clean database of a pinned version with a pinned pgvector and an
 * externally-supplied one guarantees nothing. Testcontainers stays the default and the contract; this
 * is the local fallback, and the environment variable makes each run's choice explicit rather than
 * dependent on what happens to be installed.
 *
 * <p><b>The "clean database" half is the script's job, not this class's.</b>
 * {@code scripts/integration-db.mjs} creates a throwaway database per run and drops it afterwards,
 * which is a stronger guarantee than any check here could make: a Java-side "refuse if tables exist"
 * would still be pointed at somebody's development database by whoever set the variable by hand, and
 * would then refuse forever rather than protecting anything. Tests write freely; the database they
 * write to is disposable.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
public abstract class AbstractPostgresIntegrationTest {

    /**
     * Points the suite at an already-running database.
     *
     * <p>Read from the environment rather than a property so it cannot be committed by accident: a
     * checked-in {@code application-integration-test.yml} pointing at a developer's machine would
     * silently disable the container for everybody.
     */
    private static final String EXTERNAL_URL_ENV = "INTEGRATION_TEST_JDBC_URL";
    private static final String EXTERNAL_USERNAME_ENV = "INTEGRATION_TEST_USERNAME";
    private static final String EXTERNAL_PASSWORD_ENV = "INTEGRATION_TEST_PASSWORD";

    private static final String EXTERNAL_URL = System.getenv(EXTERNAL_URL_ENV);

    /**
     * Null when an external database was supplied — the container is then never constructed, let
     * alone started, so a machine with no Docker never pays the connection timeout.
     */
    static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_URL == null ? newContainer() : null;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    private static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("travel_planner")
                .withUsername("travel_planner")
                .withPassword("travel_planner");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv(EXTERNAL_USERNAME_ENV));
            registry.add("spring.datasource.password", () -> System.getenv(EXTERNAL_PASSWORD_ENV));
            return;
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Which database this run used, for a test that needs to say so in its own output. */
    protected static String databaseDescription() {
        return POSTGRES == null ? "external: " + EXTERNAL_URL : "testcontainer: " + POSTGRES.getJdbcUrl();
    }
}
