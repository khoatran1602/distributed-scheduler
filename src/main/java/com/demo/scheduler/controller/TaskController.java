package com.demo.scheduler.controller;

import com.demo.scheduler.model.Task;
import com.demo.scheduler.service.TaskProducer;
import com.demo.scheduler.service.TaskProducer.TaskStats;
import com.demo.scheduler.service.TaskWorker;
import com.demo.scheduler.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST API Controller for task submission (Producer endpoint).
 * Provides endpoints for submitting tasks and checking status.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskProducer taskProducer;
    private final TaskWorker taskWorker;
    private final TaskRepository taskRepository;

    public TaskController(TaskProducer taskProducer, TaskWorker taskWorker, TaskRepository taskRepository) {
        this.taskProducer = taskProducer;
        this.taskWorker = taskWorker;
        this.taskRepository = taskRepository;
    }

    /**
     * POST /api/tasks - Submit a new task
     * Request body: { "payload": "task data here" }
     */
    @PostMapping
    public ResponseEntity<TaskResponse> submitTask(@RequestBody TaskRequest request) {
        if (request.payload == null || request.payload.isBlank()) {
            return ResponseEntity.badRequest().body(
                new TaskResponse(null, "REJECTED", "Payload cannot be empty"));
        }

        Task task = taskProducer.submitTask(request.payload);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TaskResponse(task.getId(), task.getStatus().name(), "Task submitted successfully"));
    }

    /**
     * GET /api/tasks/{id} - Get task status by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTask(@PathVariable Long id) {
        Optional<Task> task = taskRepository.findById(id);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task.get());
    }

    /**
     * GET /api/tasks/stats - Get queue and processing statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        TaskStats stats = taskProducer.getStats();
        StatsResponse response = new StatsResponse();
        response.queueDepth = stats.queueDepth;
        response.totalTasks = stats.totalTasks;
        response.pendingTasks = stats.pendingTasks;
        response.processingTasks = stats.processingTasks;
        response.completedTasks = stats.completedTasks;
        response.failedTasks = stats.failedTasks;
        response.processedByWorker = taskWorker.getProcessedCount();
        response.failedByWorker = taskWorker.getFailedCount();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/tasks/health - Simple health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "distributed-scheduler"));
    }

    // DTO classes
    public static class TaskRequest {
        public String payload;
    }

    public static class TaskResponse {
        public Long id;
        public String status;
        public String message;

        public TaskResponse(Long id, String status, String message) {
            this.id = id;
            this.status = status;
            this.message = message;
        }
    }

    public static class StatsResponse {
        public long queueDepth;
        public long totalTasks;
        public long pendingTasks;
        public long processingTasks;
        public long completedTasks;
        public long failedTasks;
        public long processedByWorker;
        public long failedByWorker;
    }
}
