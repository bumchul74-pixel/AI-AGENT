package com.hanwha.ai.securecoding.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SecureCodingWorkerConfig {
    @Bean("secureCodingCoordinatorExecutor")
    Executor coordinator(SecureCodingWorkerProperties p) {
        return executor(1, 1, p.coordinatorQueueCapacity(), "secure-coding-coordinator-", false);
    }

    @Bean("secureCodingFileExecutor")
    Executor workers(SecureCodingWorkerProperties p) {
        return executor(p.coreSize(), p.maxSize(), p.queueCapacity(), "secure-coding-worker-", true);
    }

    private Executor executor(int core, int max, int capacity, String prefix, boolean callerRuns) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(capacity);
        executor.setThreadNamePrefix(prefix);
        if (callerRuns) executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
