package com.demo.scheduler.service;

import com.demo.scheduler.model.Task;
import com.demo.scheduler.model.Task.TaskStatus;
import com.demo.scheduler.repository.TaskRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task Worker service - consumes tasks from Redis and processes them.
 * This is the "Consumer" in the Producer-Broker-Consumer pattern.
 * 
 * Uses a fixed thread pool for parallel task processing.
 */
@Service
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskRepository taskRepository;
    private final ExecutorService taskExecutor;

    @Value("${scheduler.queue.name:task-queue}")
    private String queueName;

    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    public TaskWorker(
            RedisTemplate<String, Object> redisTemplate,
            TaskRepository taskRepository,
            @Qualifier("taskExecutor") ExecutorService taskExecutor) {
        this.redisTemplate = redisTemplate;
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    public void init() {
        log.info("TaskWorker initialized, polling queue: {}", queueName);
    }

    /**
     * Polls Redis queue at fixed intervals and dispatches tasks to the thread pool.
     * Uses leftPop (LPOP) for FIFO queue semantics.
     */
    @Scheduled(fixedDelayString = "${scheduler.worker.poll-interval-ms:100}")
    public void pollAndProcess() {
        // Try to pop a task ID from the queue
        Object taskIdObj = redisTemplate.opsForList().leftPop(queueName);
        
        if (taskIdObj != null) {
            Long taskId = convertToLong(taskIdObj);
            if (taskId != null) {
                // Submit to thread pool for parallel processing
                taskExecutor.submit(() -> processTask(taskId));
            }
        }
    }

    /**
     * Processes a single task by ID.
     * Updates task status in PostgreSQL throughout the lifecycle.
     */
    private void processTask(Long taskId) {
        try {
            // 1. Fetch task from database
            Optional<Task> optTask = taskRepository.findById(taskId);
            if (optTask.isEmpty()) {
                log.warn("Task {} not found in database", taskId);
                return;
            }

            Task task = optTask.get();
            
            // 2. Mark as PROCESSING
            task.setStatus(TaskStatus.PROCESSING);
            task.setProcessedAt(LocalDateTime.now());
            taskRepository.save(task);

            // 3. Execute the task (simulate work)
            executeTask(task);

            // 4. Mark as COMPLETED
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

            processedCount.incrementAndGet();
            
            if (processedCount.get() % 10000 == 0) {
                log.info("Processed {} tasks so far", processedCount.get());
            }

        } catch (Exception e) {
            log.error("Error processing task {}: {}", taskId, e.getMessage());
            failedCount.incrementAndGet();
            
            // Mark task as FAILED
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setStatus(TaskStatus.FAILED);
                task.setErrorMessage(e.getMessage());
                task.setCompletedAt(LocalDateTime.now());
                taskRepository.save(task);
            });
        }
    }

    /**
     * Executes the actual task logic.
     * In a real system, this would parse the payload and perform business logic.
     * Here we simulate a small amount of work.
     */
    private void executeTask(Task task) {
        // Simulate some processing time (1-5ms)
        try {
            Thread.sleep((long) (Math.random() * 4 + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task interrupted", e);
        }
        
        // In a real system, you would:
        // - Parse task.getPayload() as JSON
        // - Execute business logic based on task type
        // - Call external services, update databases, etc.
    }

    /**
     * Converts various number types to Long.
     */
    private Long convertToLong(Object obj) {
        if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        } else if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                log.error("Failed to parse task ID: {}", obj);
                return null;
            }
        }
        log.error("Unknown task ID type: {}", obj.getClass());
        return null;
    }

    /**
     * Returns the count of successfully processed tasks.
     */
    public long getProcessedCount() {
        return processedCount.get();
    }

    /**
     * Returns the count of failed tasks.
     */
    public long getFailedCount() {
        return failedCount.get();
    }
}
