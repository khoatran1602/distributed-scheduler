package com.demo.scheduler.service;

import com.demo.scheduler.model.Task;
import com.demo.scheduler.model.Task.TaskStatus;
import com.demo.scheduler.repository.TaskRepository;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.demo.scheduler.config.BrokerConfigManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Task Worker service - consumes tasks from configured broker and processes them.
 * This is the "Consumer" in the Producer-Broker-Consumer pattern.
 * 
 * Supports both Redis (poll) and Kafka (push) modes.
 */
@Service
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskRepository taskRepository;
    private final ExecutorService taskExecutor;
    private final MessageCaptureService messageCapture;
    private final ObjectMapper objectMapper;
    private final BrokerConfigManager brokerConfigManager;

    @Value("${scheduler.queue.name:task-queue}")
    private String queueName;

    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    public TaskWorker(
            RedisTemplate<String, Object> redisTemplate,
            TaskRepository taskRepository,
            @Qualifier("taskExecutor") ExecutorService taskExecutor,
            MessageCaptureService messageCapture,
            ObjectMapper objectMapper,
            BrokerConfigManager brokerConfigManager) {
        this.redisTemplate = redisTemplate;
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
        this.messageCapture = messageCapture;
        this.objectMapper = objectMapper;
        this.brokerConfigManager = brokerConfigManager;
    }

    @PostConstruct
    public void init() {
        log.info("TaskWorker initialized. Broker Type: {}", brokerConfigManager.getBrokerType());
    }

    /**
     * Polls Redis queue at fixed intervals.
     * Only runs if scheduler.broker.type = redis
     */
    @Scheduled(fixedDelayString = "${scheduler.worker.poll-interval-ms:100}")
    public void pollRedis() {
        if (!"redis".equalsIgnoreCase(brokerConfigManager.getBrokerType())) {
            return;
        }

        // Try to pop a task ID from the queue
        Object taskIdObj = redisTemplate.opsForList().leftPop(queueName);
        
        if (taskIdObj != null) {
            Long taskId = convertToLong(taskIdObj);
            if (taskId != null) {
                // Capture for inspector
                messageCapture.captureConsumed(
                    "REDIS",
                    queueName,
                    taskId.toString(),
                    "Task ID: " + taskId
                );

                // Submit to thread pool for parallel processing
                taskExecutor.submit(() -> processTask(taskId));
            }
        }
    }

    /**
     * Listens to Kafka topic.
     * Uses JSON Deserializer to convert payload to TaskEvent
     */
    @KafkaListener(topics = "${scheduler.broker.topic}", groupId = "${spring.kafka.consumer.group-id}", autoStartup = "${scheduler.broker.kafka-enabled:true}")
    public void listenKafka(ConsumerRecord<String, com.demo.scheduler.model.TaskEvent> record) {
        if (!"kafka".equalsIgnoreCase(brokerConfigManager.getBrokerType())) {
            return;
        }
        
        log.debug("Received task from Kafka: {}", record.value());
        
        // Capture for inspector
        String payload = serializeEvent(record.value());
        String id = String.format("P-%d/O-%d", record.partition(), record.offset());
        
        messageCapture.captureConsumed(
            "KAFKA",
            record.topic(),
            id,
            payload
        );
        
        Long taskId = record.value().getTaskId();
        taskExecutor.submit(() -> processTask(taskId));
    }

    private String serializeEvent(com.demo.scheduler.model.TaskEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return event.toString();
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
