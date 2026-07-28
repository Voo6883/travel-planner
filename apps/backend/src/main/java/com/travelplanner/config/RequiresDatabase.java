package com.travelplanner.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Marks a bean that cannot exist without a datasource.
 *
 * <p>It names, once, the condition already carried by
 * {@code infrastructure/persistence/UserRepositoryAdapter} and its siblings since task 07:
 * {@code ./gradlew test} must start the full application context with no Docker and no database
 * (the {@code test} profile excludes {@code DataSourceAutoConfiguration} outright). Without the
 * condition, every service that injects a repository port would fail context startup there and the
 * unit suite would silently acquire a Docker dependency.
 *
 * <p>Deliberately <em>not</em> meta-annotated with {@code @Component}. It composes with whichever
 * stereotype the class already carries — {@code @Service}, {@code @RestController} — instead of
 * replacing it, so the class still reads as what it is.
 *
 * <p>The counterpart is that a database-less context has no authentication beans at all. That is
 * correct rather than convenient: with no user table there are no accounts, and a filter that
 * cannot look one up has nothing to authenticate.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "spring.datasource.url")
public @interface RequiresDatabase {
}
