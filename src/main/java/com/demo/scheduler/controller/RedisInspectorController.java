package com.demo.scheduler.controller;

import com.demo.scheduler.service.MessageCaptureService;
import com.demo.scheduler.service.RedisAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

//@RestController
@RequestMapping("/api/redis")
@RequiredArgsConstructor
@Tag(name = "Redis Inspector", description = "Endpoints to inspect Redis internals")
public class RedisInspectorController {

    private final RedisAdminService redisAdminService;
    private final MessageCaptureService messageCapture;

    @GetMapping("/info")
    @Operation(summary = "Get Redis server info", description = "Returns Redis version, memory usage, and uptime")
    public ResponseEntity<Map<String, Object>> getRedisInfo() {
        return ResponseEntity.ok(redisAdminService.getRedisInfo());
    }

    @GetMapping("/queue")
    @Operation(summary = "Get queue stats", description = "Returns current size of the task queue")
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        return ResponseEntity.ok(redisAdminService.getQueueStats());
    }

    @GetMapping("/messages/recent")
    @Operation(summary = "Get recent Redis messages", description = "Returns the last 50 messages related to Redis operations")
    public ResponseEntity<Map<String, Object>> getRecentMessages() {
        Map<String, Object> result = new HashMap<>();
        result.put("messages", messageCapture.getRecentMessages());
        result.put("stats", messageCapture.getStats());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/overview")
    @Operation(summary = "Get Redis overview", description = "Returns combined server info and queue stats")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("server", redisAdminService.getRedisInfo());
        overview.put("queue", redisAdminService.getQueueStats());
        overview.put("messages", messageCapture.getStats());
        return ResponseEntity.ok(overview);
    }
}
