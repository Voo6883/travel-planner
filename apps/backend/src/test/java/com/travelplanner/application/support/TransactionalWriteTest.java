package com.travelplanner.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelplanner.config.RetryConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Proves {@link TransactionalWrite} actually behaves as advertised.
 *
 * <p>No database. The transaction manager is a mock, which is what makes the important assertion
 * possible at all: counting {@code getTransaction} calls shows how many <em>distinct</em>
 * transactions the retry produced. A retry nested inside a single transaction would show one, and
 * would be useless in production — PostgreSQL has already aborted that transaction when it raises
 * {@code 40P01}, so every subsequent statement on it fails.
 *
 * <p>Composed annotations are resolved by meta-annotation lookup, which is easy to break by moving
 * the annotation or changing its retention. This test is the reason such a break fails in the unit
 * suite rather than at the first production deadlock.
 */
@SpringJUnitConfig
class TransactionalWriteTest {

    @Autowired
    private FlakyWriteService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetSharedFixtureState() {
        // The Spring context is cached across tests, so the counter and the mock's invocation
        // record carry over. clearInvocations rather than reset: reset would also discard the
        // getTransaction stub.
        service.resetAttempts();
        clearInvocations(transactionManager);
    }

    @Test
    void retriesADeadlockVictimInAFreshTransactionEachTime() {
        String result = service.writeFailingTwice();

        assertThat(result).isEqualTo("committed");
        assertThat(service.attempts()).isEqualTo(3);
        // One transaction per attempt: retry advice wraps transaction advice, not the reverse.
        verify(transactionManager, times(3)).getTransaction(any());
    }

    @Test
    void givesUpAfterTheConfiguredMaximumRatherThanRetryingForever() {
        assertThatThrownBy(() -> service.alwaysDeadlocks())
                .isInstanceOf(CannotAcquireLockException.class);

        assertThat(service.attempts()).isEqualTo(TransactionalWrite.MAX_ATTEMPTS);
    }

    @Test
    void doesNotRetryAFailureThatIsNotALockConflict() {
        assertThatThrownBy(() -> service.failsWithABusinessError())
                .isInstanceOf(IllegalStateException.class);

        // A validation failure is deterministic. Retrying it burns backoff and produces the same
        // error three times over.
        assertThat(service.attempts()).isEqualTo(1);
    }

    @Test
    void rollsBackOnCheckedExceptionsToo() {
        // Spring's default rolls back on unchecked exceptions only, so this attribute is the whole
        // reason the composed annotation exists rather than a bare @Transactional.
        Transactional transactional = TransactionalWrite.class.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).containsExactly(Exception.class);
    }

    @Configuration
    @EnableTransactionManagement
    @Import(RetryConfig.class)
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return manager;
        }

        @Bean
        FlakyWriteService flakyWriteService() {
            return new FlakyWriteService();
        }
    }

    /**
     * Stands in for a real write use-case.
     *
     * <p>Public class, public methods, on purpose: {@code AnnotationTransactionAttributeSource}
     * advises public methods only, so a package-private fixture would silently receive no
     * transaction advice and the test would assert nothing.
     */
    public static class FlakyWriteService {

        private final AtomicInteger attempts = new AtomicInteger();

        public int attempts() {
            return attempts.get();
        }

        public void resetAttempts() {
            attempts.set(0);
        }

        @TransactionalWrite
        public String writeFailingTwice() {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("deadlock detected (40P01)");
            }
            return "committed";
        }

        @TransactionalWrite
        public String alwaysDeadlocks() {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("deadlock detected (40P01)");
        }

        @TransactionalWrite
        public String failsWithABusinessError() {
            attempts.incrementAndGet();
            throw new IllegalStateException("not a lock conflict");
        }
    }
}
