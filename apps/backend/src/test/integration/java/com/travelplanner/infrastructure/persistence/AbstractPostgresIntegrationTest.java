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
 */
@SpringBootTest
@ActiveProfiles("integration-test")
abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("travel_planner")
            .withUsername("travel_planner")
            .withPassword("travel_planner");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
