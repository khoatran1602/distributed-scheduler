package com.demo.scheduler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisAdminService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${scheduler.queue.name:task-queue}")
    private String queueName;

    public Map<String, Object> getRedisInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            Properties props = redisTemplate.getRequiredConnectionFactory().getConnection().info();
            if (props != null) {
                info.put("version", props.getProperty("redis_version"));
                info.put("os", props.getProperty("os"));
                info.put("uptime_days", props.getProperty("uptime_in_days"));
                info.put("connected_clients", props.getProperty("connected_clients"));
                info.put("used_memory_human", props.getProperty("used_memory_human"));
                info.put("status", "CONNECTED");
            } else {
                info.put("status", "UNKNOWN");
            }
        } catch (Exception e) {
            log.error("Failed to get Redis info", e);
            info.put("status", "ERROR");
            info.put("error", e.getMessage());
        }
        return info;
    }

    public Map<String, Object> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            Long size = redisTemplate.opsForList().size(queueName);
            stats.put("queueName", queueName);
            stats.put("size", size != null ? size : 0);
        } catch (Exception e) {
            log.error("Failed to get queue stats", e);
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    public String getQueueName() {
        return queueName;
    }
}
