package com.demo.scheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages the active broker configuration (Redis vs Kafka).
 * Allows dynamic switching at runtime.
 */
@Service
public class BrokerConfigManager {

    private String currentBrokerType;

    public BrokerConfigManager(@Value("${scheduler.broker.type:redis}") String initialBrokerType) {
        this.currentBrokerType = initialBrokerType;
    }

    public synchronized String getBrokerType() {
        return currentBrokerType;
    }

    public synchronized void setBrokerType(String brokerType) {
        if ("redis".equalsIgnoreCase(brokerType) || "kafka".equalsIgnoreCase(brokerType)) {
            this.currentBrokerType = brokerType.toLowerCase();
        } else {
            throw new IllegalArgumentException("Invalid broker type: " + brokerType);
        }
    }
}
