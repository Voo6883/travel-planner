package com.travelplanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Turns on {@code @Retryable} processing for
 * {@link com.travelplanner.application.support.TransactionalWrite}.
 *
 * <p>{@code order} is the whole point of this class. Spring's transaction advisor defaults to
 * {@link Ordered#LOWEST_PRECEDENCE}, and so does the retry advisor; with equal order the nesting
 * depends on bean-definition order, which is not a contract. Pinning retry to one step higher
 * precedence guarantees it wraps the transaction rather than the reverse — a retry <em>inside</em>
 * a transaction the database has already aborted repeats the same doomed work and cannot succeed.
 */
@Configuration
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
public class RetryConfig {
}
