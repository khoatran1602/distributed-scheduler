package com.demo.scheduler.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool configuration for parallel task processing.
 * Uses a fixed thread pool optimized for high-throughput task execution.
 */
@Configuration
public class ThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfig.class);

    @Value("${scheduler.worker.pool-size:10}")
    private int poolSize;

    private ExecutorService executorService;

    /**
     * Creates a fixed thread pool for task processing.
     * Using Virtual Threads (Java 21) could be enabled here for even higher concurrency.
     */
    @Bean(name = "taskExecutor")
    public ExecutorService taskExecutor() {
        log.info("Initializing task executor with {} threads", poolSize);
        
        // Option 1: Traditional Fixed Thread Pool
        this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r);
            t.setName("task-worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        
        // Option 2: Virtual Threads (Java 21) - Uncomment for massive concurrency
        // this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        
        return executorService;
    }

    /**
     * Graceful shutdown of the thread pool.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down task executor...");
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    log.warn("Forced shutdown of task executor");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Task executor shutdown complete");
    }
}
