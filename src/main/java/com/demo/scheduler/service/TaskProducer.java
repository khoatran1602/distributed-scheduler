package com.demo.scheduler.service;

import com.demo.scheduler.model.Task;
import com.demo.scheduler.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task Producer service - pushes tasks to the Redis queue.
 * This is the "Producer" in the Producer-Broker-Consumer pattern.
 */
@Service
public class TaskProducer {

    private static final Logger log = LoggerFactory.getLogger(TaskProducer.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskRepository taskRepository;

    @Value("${scheduler.queue.name:task-queue}")
    private String queueName;

    public TaskProducer(RedisTemplate<String, Object> redisTemplate, TaskRepository taskRepository) {
        this.redisTemplate = redisTemplate;
        this.taskRepository = taskRepository;
    }

    /**
     * Submits a task: saves to PostgreSQL and pushes to Redis queue.
     * 
     * @param payload The task payload/data
     * @return The created Task with its assigned ID
     */
    @Transactional
    public Task submitTask(String payload) {
        // 1. Create and persist the task to PostgreSQL
        Task task = new Task(payload);
        task = taskRepository.save(task);
        
        // 2. Push the task ID to Redis queue (right push for FIFO)
        redisTemplate.opsForList().rightPush(queueName, task.getId());
        
        log.debug("Task {} submitted to queue", task.getId());
        return task;
    }

    /**
     * Gets the current queue depth (number of pending tasks in Redis).
     */
    public long getQueueDepth() {
        Long size = redisTemplate.opsForList().size(queueName);
        return size != null ? size : 0;
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
