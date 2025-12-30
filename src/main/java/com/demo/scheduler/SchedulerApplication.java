package com.demo.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the Distributed Task Scheduler.
 * 
 * Architecture: Producer -> Broker (Redis) -> Consumer (Worker Pool)
 * 
 * - Producer: REST API that accepts tasks and pushes to Redis queue
 * - Broker: Redis List acting as a reliable FIFO queue
 * - Consumer: Background workers polling Redis and processing in parallel
 */
@SpringBootApplication
@EnableScheduling
public class SchedulerApplication {

    public static void main(String[] args) {
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════╗
            ║         DISTRIBUTED TASK SCHEDULER                       ║
            ║         Producer -> Redis -> Consumer (Worker Pool)      ║
            ╚═══════════════════════════════════════════════════════════╝
            """);
        SpringApplication.run(SchedulerApplication.class, args);
    }
}
