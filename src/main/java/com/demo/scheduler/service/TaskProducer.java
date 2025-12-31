package com.demo.scheduler.service;

import com.demo.scheduler.config.BrokerConfigManager;
import com.demo.scheduler.model.Task;
import com.demo.scheduler.repository.TaskRepository;
import com.demo.scheduler.service.broker.TaskBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Task Producer service - pushes tasks to the configured broker (Redis or Kafka).
 * This is the "Producer" in the Producer-Broker-Consumer pattern.
 */
//@Service
public class TaskProducer {

    private static final Logger log = LoggerFactory.getLogger(TaskProducer.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskRepository taskRepository;
    private final List<TaskBroker> brokers;
    private final BrokerConfigManager brokerConfigManager;

    @Value("${scheduler.queue.name:task-queue}")
    private String queueName;

    public TaskProducer(RedisTemplate<String, Object> redisTemplate, 
                        TaskRepository taskRepository,
                        List<TaskBroker> brokers,
                        BrokerConfigManager brokerConfigManager) {
        this.redisTemplate = redisTemplate;
        this.taskRepository = taskRepository;
        this.brokers = brokers;
        this.brokerConfigManager = brokerConfigManager;
    }

    /**
     * Submits a task: saves to PostgreSQL and pushes to configured broker.
     * 
     * @param payload The task payload/data
     * @return The created Task with its assigned ID
     */
    @Transactional
    public Task submitTask(String payload) {
        // 1. Create and persist the task to PostgreSQL
        Task task = new Task(payload);
        task = taskRepository.save(task);
        
        String currentBroker = brokerConfigManager.getBrokerType();

        // 2. Route to configured broker
        TaskBroker broker = brokers.stream()
            .filter(b -> b.getBrokerType().equalsIgnoreCase(currentBroker))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown broker type: " + currentBroker));

        broker.submitTask(task);
        return task;
    }

    /**
     * Gets the current queue depth (number of pending tasks in Redis).
     * Note: This is specific to Redis; for Kafka, we might return 0 or implement a Lag checker.
     */
    public long getQueueDepth() {
        if ("redis".equalsIgnoreCase(brokerConfigManager.getBrokerType())) {
            Long size = redisTemplate.opsForList().size(queueName);
            return size != null ? size : 0;
        }
        return -1; // Not supported for others yet
    }

    /**
     * Gets task statistics from the database.
     */
    public TaskStats getStats() {
        TaskStats stats = new TaskStats();
        stats.queueDepth = getQueueDepth();
        stats.totalTasks = taskRepository.count();
        stats.pendingTasks = taskRepository.countByStatus(Task.TaskStatus.PENDING);
        stats.processingTasks = taskRepository.countByStatus(Task.TaskStatus.PROCESSING);
        stats.completedTasks = taskRepository.countByStatus(Task.TaskStatus.COMPLETED);
        stats.failedTasks = taskRepository.countByStatus(Task.TaskStatus.FAILED);
        return stats;
    }

    /**
     * Task statistics DTO.
     */
    public static class TaskStats {
        public long queueDepth;
        public long totalTasks;
        public long pendingTasks;
        public long processingTasks;
        public long completedTasks;
        public long failedTasks;
    }
}
