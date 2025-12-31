package com.demo.scheduler.service.broker;

import com.demo.scheduler.model.Task;
import com.demo.scheduler.service.MessageCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
//@Service
@RequiredArgsConstructor
public class RedisTaskBroker implements TaskBroker {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageCaptureService messageCapture;

    @Value("${scheduler.queue.name}")
    private String queueName;

    @Override
    public void submitTask(Task task) {
        redisTemplate.opsForList().rightPush(queueName, task.getId().toString());
        log.debug("Task {} pushed to Redis queue: {}", task.getId(), queueName);
        
        // Capture for inspector
        messageCapture.captureProduced(
            "REDIS",
            queueName,
            task.getId().toString(),
            "Task ID: " + task.getId()
        );
    }

    @Override
    public String getBrokerType() {
        return "redis";
    }
}
