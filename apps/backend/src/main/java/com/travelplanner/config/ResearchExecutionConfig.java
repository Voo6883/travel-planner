package com.travelplanner.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The thread pools behind the local research-job platform (tasks/23).
 *
 * <p>Two pools with two jobs, because a run needs a bounded budget that cancels the work:
 * <ul>
 *   <li>the <b>worker</b> pool runs {@code ResearchJobRunner.run}, one slot per concurrent job;</li>
 *   <li>the <b>timeout</b> pool runs the {@code ResearchJobHandler} for a single job so the runner
 *       can {@code Future.get(timeout)} it and {@code cancel(true)} on overrun — interrupting the
 *       handler thread, which one pool could not do to itself without deadlocking when full.</li>
 * </ul>
 *
 * <p>Both are daemon-threaded so they never hold JVM shutdown open, and both are ordinary in-process
 * pools: no Redis, no broker (the task's Do-not list). Replacing this file with a queue-backed
 * executor is the documented upgrade path.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResearchProperties.class)
public class ResearchExecutionConfig {

    /** Bean name of the worker pool that runs whole jobs. */
    public static final String WORKER_EXECUTOR = "researchWorkerExecutor";

    /** Bean name of the pool that runs a single handler under a timeout. */
    public static final String TIMEOUT_EXECUTOR = "researchTimeoutExecutor";

    @Bean(name = WORKER_EXECUTOR)
    public TaskExecutor researchWorkerExecutor(ResearchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerPoolSize());
        executor.setMaxPoolSize(properties.getWorkerPoolSize());
        executor.setQueueCapacity(properties.getWorkerQueueCapacity());
        executor.setThreadNamePrefix("research-worker-");
        executor.setDaemon(true);
        // Give in-flight runs a moment to finish on shutdown; the reconciler recovers anything that
        // does not (a RUNNING job at the next boot is orphaned and is reset then).
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Bean(name = TIMEOUT_EXECUTOR, destroyMethod = "shutdownNow")
    public ExecutorService researchTimeoutExecutor() {
        ThreadFactory factory = daemonThreadFactory("research-handler-");
        // A cached pool: one live handler thread per running job, reused and reaped when idle. The
        // worker pool already bounds how many jobs run at once, so this cannot grow past it.
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 30L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), factory);
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        ThreadFactory delegate = Executors.defaultThreadFactory();
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = delegate.newThread(runnable);
            thread.setName(prefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
