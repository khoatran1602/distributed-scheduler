package com.demo.scheduler.ai.controller;

import com.demo.scheduler.ai.dto.DebateRequest;
import com.demo.scheduler.ai.dto.DebateResponse;
import com.demo.scheduler.ai.security.RateLimiter;
import com.demo.scheduler.ai.service.MultiAgentOrchestrator;
import com.demo.scheduler.ai.validation.DebateValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for multi-agent debate API.
 */
@RestController
@RequestMapping("/api/debate")
@Tag(name = "Multi-Agent Debate", description = "AI-powered multi-agent debate orchestration")
public class DebateController {
    
    private static final Logger log = LoggerFactory.getLogger(DebateController.class);
    
    private final MultiAgentOrchestrator orchestrator;
    private final RateLimiter rateLimiter;
    private final DebateValidator validator;
    
    public DebateController(
            MultiAgentOrchestrator orchestrator, 
            RateLimiter rateLimiter,
            DebateValidator validator) {
        this.orchestrator = orchestrator;
        this.rateLimiter = rateLimiter;
        this.validator = validator;
    }
    
    /**
     * Execute a multi-agent debate.
     */
    @PostMapping
    @Operation(summary = "Execute multi-agent debate", 
               description = "Orchestrates a debate between two AI agents with a judge synthesis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Debate completed successfully",
                    content = @Content(schema = @Schema(implementation = DebateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> executeDebate(
            @Valid @RequestBody DebateRequest request,
            HttpServletRequest httpRequest) {
        
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        
        try {
            String clientId = getClientId(httpRequest);
            log.info("Debate request from client: {}", clientId);
            
            // Rate limiting check
            if (!rateLimiter.isAllowed(clientId)) {
                log.warn("Rate limit exceeded for client: {}", clientId);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("X-RateLimit-Limit", String.valueOf(rateLimiter.getLimit()))
                        .header("X-RateLimit-Remaining", "0")
                        .body(Map.of(
                                "error", "Rate limit exceeded",
                                "message", "Please wait before making another request",
                                "requestId", requestId
                        ));
            }
            
            // Validate request
            List<String> errors = validator.validate(request);
            if (!errors.isEmpty()) {
                log.warn("Validation failed: {}", errors);
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error", "Validation failed",
                                "details", errors,
                                "requestId", requestId
                        ));
            }
            
            // Execute debate
            DebateResponse response = orchestrator.executeDebate(request);
            
            // Add rate limit headers
            int remaining = rateLimiter.getRemainingRequests(clientId);
            
            return ResponseEntity.ok()
                    .header("X-Request-Id", requestId)
                    .header("X-RateLimit-Limit", String.valueOf(rateLimiter.getLimit()))
                    .header("X-RateLimit-Remaining", String.valueOf(remaining))
                    .body(response);
            
        } catch (Exception e) {
            log.error("Debate execution failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Debate execution failed",
                            "message", e.getMessage() != null ? e.getMessage() : "Unknown error occurred",
                            "requestId", requestId
                    ));
        } finally {
            MDC.remove("requestId");
        }
    }
    
    /**
     * Get provider availability status.
     */
    @GetMapping("/providers")
    @Operation(summary = "Get AI provider status", description = "Check which AI providers are available")
    public ResponseEntity<Map<String, Boolean>> getProviderStatus() {
        return ResponseEntity.ok(orchestrator.getProviderStatus());
    }
    
    /**
     * Health check for the debate service.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the debate service is healthy")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Boolean> providers = orchestrator.getProviderStatus();
        boolean anyAvailable = providers.values().stream().anyMatch(Boolean::booleanValue);
        
        return ResponseEntity.ok(Map.of(
                "status", anyAvailable ? "UP" : "DEGRADED",
                "service", "multi-agent-debate",
                "providers", providers,
                "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Extract client identifier for rate limiting.
     */
    private String getClientId(HttpServletRequest request) {
        // Check for forwarded headers (behind proxy/load balancer)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        
        return request.getRemoteAddr();
    }
}
