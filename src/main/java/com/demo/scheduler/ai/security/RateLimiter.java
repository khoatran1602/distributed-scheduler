package com.demo.scheduler.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter for debate API.
 * Uses a simple sliding window approach.
 * 
 * For production at scale, recommend upgrading to Redis-based rate limiting.
 */
@Component
public class RateLimiter {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    
    @Value("${ai.rate-limit.requests-per-minute:10}")
    private int requestsPerMinute;
    
    @Value("${ai.rate-limit.enabled:true}")
    private boolean enabled;
    
    // Map of client identifier -> request timestamps
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    
    /**
     * Check if a request is allowed for the given client.
     * 
     * @param clientId Client identifier (e.g., IP address)
     * @return true if request is allowed, false if rate limited
     */
    public boolean isAllowed(String clientId) {
        if (!enabled) {
            return true;
        }
        
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000; // 1 minute window
        
        RateLimitBucket bucket = buckets.computeIfAbsent(clientId, k -> new RateLimitBucket());
        
        // Clean up old requests
        bucket.cleanOldRequests(windowStart);
        
        // Check if under limit
        if (bucket.getRequestCount() >= requestsPerMinute) {
            log.warn("Rate limit exceeded for client: {}", clientId);
            return false;
        }
        
        // Record this request
        bucket.recordRequest(now);
        return true;
    }
    
    /**
     * Get remaining requests for a client.
     */
    public int getRemainingRequests(String clientId) {
        if (!enabled) {
            return Integer.MAX_VALUE;
        }
        
        RateLimitBucket bucket = buckets.get(clientId);
        if (bucket == null) {
            return requestsPerMinute;
        }
        
        long windowStart = System.currentTimeMillis() - 60_000;
        bucket.cleanOldRequests(windowStart);
        return Math.max(0, requestsPerMinute - bucket.getRequestCount());
    }
    
    /**
     * Get the configured limit.
     */
    public int getLimit() {
        return requestsPerMinute;
    }
    
    /**
     * Internal bucket for tracking requests per client.
     */
    private static class RateLimitBucket {
        private final java.util.concurrent.ConcurrentLinkedQueue<Long> requestTimestamps = 
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final AtomicInteger count = new AtomicInteger(0);
        
        void recordRequest(long timestamp) {
            requestTimestamps.offer(timestamp);
            count.incrementAndGet();
        }
        
        void cleanOldRequests(long windowStart) {
            while (!requestTimestamps.isEmpty()) {
                Long oldest = requestTimestamps.peek();
                if (oldest != null && oldest < windowStart) {
                    requestTimestamps.poll();
                    count.decrementAndGet();
                } else {
                    break;
                }
            }
        }
        
        int getRequestCount() {
            return count.get();
        }
    }
    
    /**
     * Cleanup old buckets periodically (call from scheduled task).
     */
    public void cleanupStaleClients() {
        long now = System.currentTimeMillis();
        long staleThreshold = now - 300_000; // 5 minutes
        
        buckets.entrySet().removeIf(entry -> {
            entry.getValue().cleanOldRequests(now - 60_000);
            return entry.getValue().getRequestCount() == 0;
        });
        
        log.debug("Cleaned up rate limit buckets. Active clients: {}", buckets.size());
    }
}
