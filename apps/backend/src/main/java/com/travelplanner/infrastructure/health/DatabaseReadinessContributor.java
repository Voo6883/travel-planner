package com.travelplanner.infrastructure.health;

import com.travelplanner.application.health.ReadinessCheck;
import com.travelplanner.application.health.ReadinessContributor;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reports whether PostgreSQL is reachable.
 *
 * <p>Registered only when {@code spring.datasource.url} is configured, so the {@code test}
 * profile keeps {@code ./gradlew test} runnable with no Docker and no Postgres. The condition is
 * on the property rather than on the bean: {@code @ConditionalOnBean} against a component scan is
 * evaluated before auto-configured beans are registered and would be order-dependent.
 *
 * <p>Uses {@link Connection#isValid} rather than a query so it stays correct once
 * tasks/07 introduces JPA and Flyway: this checks connectivity, not schema.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class DatabaseReadinessContributor implements ReadinessContributor {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public DatabaseReadinessContributor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public ReadinessCheck check() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS)
                    ? ReadinessCheck.up()
                    : ReadinessCheck.down("connection not valid");
        } catch (SQLException exception) {
            // Deliberately not the exception message: it can carry host, port, and user.
            return ReadinessCheck.down("unreachable");
        }
    }
}
