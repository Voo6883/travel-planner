package com.travelplanner.application.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write-use-case transaction template (PLAN §4.0.2-E2). Put this on every service method that
 * changes database state.
 *
 * <p>It composes the two settings that are wrong by default:
 *
 * <ul>
 *   <li><b>{@code rollbackFor = Exception.class}</b> — Spring's default rolls back on unchecked
 *       exceptions only. A checked exception from a mail send or a supplier call would otherwise
 *       <em>commit</em> the partial write on its way out, which is the opposite of what every
 *       caller assumes.</li>
 *   <li><b>{@code @Retryable} on {@link CannotAcquireLockException}</b> — PostgreSQL resolves a
 *       deadlock by killing one transaction with {@code 40P01}. That victim did nothing wrong and
 *       succeeds on a second attempt; without a retry it surfaces to the user as a random 500.
 *       Three attempts with exponential backoff, per PLAN §4.0.2-E2.</li>
 * </ul>
 *
 * <p><b>Order matters.</b> {@code @Retryable} is the outer advice and {@code @Transactional} the
 * inner one, so each attempt runs in a <em>fresh</em> transaction. Retrying inside a transaction
 * that the database has already rolled back would retry nothing and fail again immediately. This is
 * why {@code RetryConfig} pins the retry advisor's order ahead of the transaction advisor rather
 * than leaving it to bean-definition order.
 *
 * <p><b>What must not be inside.</b> No LLM call, no supplier HTTP call, no web search
 * ({@code AGENTS.md}). Those hold a pooled database connection for the length of a network wait,
 * and a retried transaction would repeat the side effect. Do the IO first, then persist the
 * validated result in a short annotated method.
 *
 * <pre>{@code
 * @TransactionalWrite
 * public TripBrief saveBrief(SaveTripBriefCommand command, UserContext user) { ... }
 * }</pre>
 *
 * <p>Read-only queries use {@code @Transactional(readOnly = true)} directly — they need neither
 * rollback rules nor retry.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(rollbackFor = Exception.class)
@Retryable(
        retryFor = CannotAcquireLockException.class,
        maxAttempts = TransactionalWrite.MAX_ATTEMPTS,
        backoff = @Backoff(delay = TransactionalWrite.INITIAL_BACKOFF_MILLIS, multiplier = 2.0))
public @interface TransactionalWrite {

    /** PLAN §4.0.2-E2: three attempts total, not three retries. */
    int MAX_ATTEMPTS = 3;

    /** First backoff in milliseconds; doubles on each subsequent attempt. */
    long INITIAL_BACKOFF_MILLIS = 100L;
}
