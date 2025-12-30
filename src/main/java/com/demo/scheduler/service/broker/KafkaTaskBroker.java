package com.demo.scheduler.service.broker;

import com.demo.scheduler.model.TaskEvent;
import com.demo.scheduler.model.Task;
import com.demo.scheduler.service.MessageCaptureService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaTaskBroker implements TaskBroker {

    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final MessageCaptureService messageCapture;
    private final ObjectMapper objectMapper;

    @Value("${scheduler.broker.topic}")
    private String topicName;

    public KafkaTaskBroker(KafkaTemplate<String, TaskEvent> kafkaTemplate, 
                           MessageCaptureService messageCapture,
                           ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageCapture = messageCapture;
        this.objectMapper = objectMapper;
    }

    @Override
    public void submitTask(Task task) {
        TaskEvent event = new TaskEvent(
            task.getId(),
            task.getPayload(),
            task.getCreatedAt().toString()
        );
        
        kafkaTemplate.send(topicName, task.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Task {} sent to Kafka topic: {} [offset={}]", 
                            task.getId(), topicName, result.getRecordMetadata().offset());
                        
                        // Capture for inspector
                        String payload = serializeEvent(event);
                        String id = String.format("P-%d/O-%d", 
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                            
                        messageCapture.captureProduced(
                            "KAFKA",
                            topicName,
                            id,
                            payload
                        );
                    } else {
                        log.error("Failed to send task {} to Kafka", task.getId(), ex);
                    }
                });
    }

    private String serializeEvent(TaskEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return event.toString();
        }
    }

    @Override
    public String getBrokerType() {
        return "kafka";
    }
}
