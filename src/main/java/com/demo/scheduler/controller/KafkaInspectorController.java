package com.demo.scheduler.controller;

import com.demo.scheduler.service.KafkaAdminService;
import com.demo.scheduler.service.MessageCaptureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@Tag(name = "Kafka Inspector", description = "Endpoints to inspect Kafka internals")
public class KafkaInspectorController {

    private final KafkaAdminService kafkaAdminService;
    private final MessageCaptureService messageCapture;

    @GetMapping("/cluster")
    @Operation(summary = "Get Kafka cluster information", description = "Returns broker list, controller, and cluster ID")
    public ResponseEntity<Map<String, Object>> getClusterInfo() {
        return ResponseEntity.ok(kafkaAdminService.getClusterInfo());
    }

    @GetMapping("/topic")
    @Operation(summary = "Get topic information", description = "Returns partition details and offsets for the configured topic")
    public ResponseEntity<Map<String, Object>> getTopicInfo() {
        return ResponseEntity.ok(kafkaAdminService.getTopicInfo(kafkaAdminService.getTopicName()));
    }

    @GetMapping("/topic/{name}")
    @Operation(summary = "Get specific topic information", description = "Returns partition details and offsets for a specific topic")
    public ResponseEntity<Map<String, Object>> getTopicInfo(@PathVariable String name) {
        return ResponseEntity.ok(kafkaAdminService.getTopicInfo(name));
    }

    @GetMapping("/consumer-group")
    @Operation(summary = "Get consumer group status", description = "Returns lag, offsets, and member info for the configured consumer group")
    public ResponseEntity<Map<String, Object>> getConsumerGroupInfo() {
        return ResponseEntity.ok(kafkaAdminService.getConsumerGroupInfo(kafkaAdminService.getConsumerGroupId()));
    }

    @GetMapping("/consumer-group/{groupId}")
    @Operation(summary = "Get specific consumer group status", description = "Returns lag, offsets, and member info for a specific consumer group")
    public ResponseEntity<Map<String, Object>> getConsumerGroupInfo(@PathVariable String groupId) {
        return ResponseEntity.ok(kafkaAdminService.getConsumerGroupInfo(groupId));
    }

    @GetMapping("/messages/recent")
    @Operation(summary = "Get recent Kafka messages", description = "Returns the last 50 messages that flowed through the topic")
    public ResponseEntity<Map<String, Object>> getRecentMessages() {
        Map<String, Object> result = new HashMap<>();
        result.put("messages", messageCapture.getRecentMessages());
        result.put("stats", messageCapture.getStats());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/messages")
    @Operation(summary = "Clear message buffer", description = "Clears the captured message buffer")
    public ResponseEntity<Map<String, Object>> clearMessages() {
        messageCapture.clear();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Message buffer cleared");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/overview")
    @Operation(summary = "Get Kafka overview", description = "Returns combined cluster, topic, and consumer group info")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("cluster", kafkaAdminService.getClusterInfo());
        overview.put("topic", kafkaAdminService.getTopicInfo(kafkaAdminService.getTopicName()));
        overview.put("consumerGroup", kafkaAdminService.getConsumerGroupInfo(kafkaAdminService.getConsumerGroupId()));
        overview.put("messages", messageCapture.getStats());
        return ResponseEntity.ok(overview);
    }
}
