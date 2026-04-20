package com.erise.ai.backend.common.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncPipelineConfig {

    @Bean("fileParseTaskExecutor")
    public Executor fileParseTaskExecutor() {
        return buildExecutor("file-parse-", 2);
    }

    @Bean("fileIndexTaskExecutor")
    public Executor fileIndexTaskExecutor() {
        return buildExecutor("file-index-", 1);
    }

    private Executor buildExecutor(String prefix, int minThreads) {
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(minThreads, processors);
        int maxPoolSize = Math.max(corePoolSize, processors * 2);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(maxPoolSize * 8);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
