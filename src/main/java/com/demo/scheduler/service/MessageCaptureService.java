package com.demo.scheduler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Captures recent messages for visualization in the inspector panel.
 * Uses a ring buffer (deque) to store the last N messages.
 * Supports both Kafka and Redis.
 */
@Slf4j
@Service
public class MessageCaptureService {

    private static final int MAX_MESSAGES = 50;
    private static final int MAX_PAYLOAD_LENGTH = 200;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final Deque<Map<String, Object>> messageBuffer = new ConcurrentLinkedDeque<>();

    /**
     * Capture a message that was produced
     */
    public void captureProduced(String source, String topicOrQueue, String id, String payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("source", source);
        message.put("direction", "PRODUCED");
        message.put("timestamp", FORMATTER.format(Instant.now()));
        message.put("epochMs", System.currentTimeMillis());
        message.put("target", topicOrQueue);
        message.put("id", id);
        message.put("payload", truncatePayload(payload));
        message.put("payloadSize", payload != null ? payload.length() : 0);

        addMessage(message);
        log.debug("Captured produced message: source={}, target={}", source, topicOrQueue);
    }

    /**
     * Capture a message that was consumed
     */
    public void captureConsumed(String source, String topicOrQueue, String id, String payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("source", source);
        message.put("direction", "CONSUMED");
        message.put("timestamp", FORMATTER.format(Instant.now()));
        message.put("epochMs", System.currentTimeMillis());
        message.put("target", topicOrQueue);
        message.put("id", id);
        message.put("payload", truncatePayload(payload));
        message.put("payloadSize", payload != null ? payload.length() : 0);

        addMessage(message);
        log.debug("Captured consumed message: source={}, target={}", source, topicOrQueue);
    }

    private void addMessage(Map<String, Object> message) {
        messageBuffer.addFirst(message);
        while (messageBuffer.size() > MAX_MESSAGES) {
            messageBuffer.removeLast();
        }
    }

    private String truncatePayload(String payload) {
        if (payload == null) return null;
        if (payload.length() <= MAX_PAYLOAD_LENGTH) return payload;
        return payload.substring(0, MAX_PAYLOAD_LENGTH) + "...";
    }

    /**
     * Get all captured messages (most recent first)
     */
    public List<Map<String, Object>> getRecentMessages() {
        return new ArrayList<>(messageBuffer);
    }

    /**
     * Get statistics about captured messages
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCaptured", messageBuffer.size());
        stats.put("maxCapacity", MAX_MESSAGES);

        long producedCount = messageBuffer.stream()
                .filter(m -> "PRODUCED".equals(m.get("direction")))
                .count();
        long consumedCount = messageBuffer.stream()
                .filter(m -> "CONSUMED".equals(m.get("direction")))
                .count();

        stats.put("producedCount", producedCount);
        stats.put("consumedCount", consumedCount);
        
        return stats;
    }

    /**
     * Clear all captured messages
     */
    public void clear() {
        messageBuffer.clear();
    }
}
