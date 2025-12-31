package com.demo.scheduler.ai.validation;

import com.demo.scheduler.ai.dto.DebateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates debate requests for security and sanity.
 */
@Component
public class DebateValidator {
    
    @Value("${ai.validation.max-question-length:10000}")
    private int maxQuestionLength;
    
    @Value("${ai.validation.max-context-length:5000}")
    private int maxContextLength;
    
    @Value("${ai.validation.min-question-length:10}")
    private int minQuestionLength;
    
    private static final Set<String> VALID_PROVIDERS = Set.of("openai", "gemini");
    
    private static final Set<String> BLOCKED_PATTERNS = Set.of(
            "<script",
            "javascript:",
            "on[a-z]+\\s*=",
            "data:text/html"
    );
    
    /**
     * Validate a debate request.
     * 
     * @param request The request to validate
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate(DebateRequest request) {
        List<String> errors = new ArrayList<>();
        
        // Null check
        if (request == null) {
            errors.add("Request cannot be null");
            return errors;
        }
        
        // Question validation
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            errors.add("Question cannot be empty");
        } else {
            String question = request.getQuestion().trim();
            
            if (question.length() < minQuestionLength) {
                errors.add("Question must be at least " + minQuestionLength + " characters");
            }
            
            if (question.length() > maxQuestionLength) {
                errors.add("Question cannot exceed " + maxQuestionLength + " characters");
            }
            
            // Check for potential injection patterns
            if (containsBlockedPattern(question)) {
                errors.add("Question contains potentially unsafe content");
            }
        }
        
        // Context validation
        if (request.getContext() != null) {
            if (request.getContext().length() > maxContextLength) {
                errors.add("Context cannot exceed " + maxContextLength + " characters");
            }
            
            if (containsBlockedPattern(request.getContext())) {
                errors.add("Context contains potentially unsafe content");
            }
        }
        
        // Provider validation
        if (request.getAgentAProvider() != null && !request.getAgentAProvider().isBlank()) {
            if (!VALID_PROVIDERS.contains(request.getAgentAProvider().toLowerCase())) {
                errors.add("Invalid provider for Agent A: " + request.getAgentAProvider());
            }
        }
        
        if (request.getAgentBProvider() != null && !request.getAgentBProvider().isBlank()) {
            if (!VALID_PROVIDERS.contains(request.getAgentBProvider().toLowerCase())) {
                errors.add("Invalid provider for Agent B: " + request.getAgentBProvider());
            }
        }
        
        if (request.getJudgeProvider() != null && !request.getJudgeProvider().isBlank()) {
            if (!VALID_PROVIDERS.contains(request.getJudgeProvider().toLowerCase())) {
                errors.add("Invalid provider for Judge: " + request.getJudgeProvider());
            }
        }
        
        return errors;
    }
    
    /**
     * Check if the input contains potentially dangerous patterns.
     */
    private boolean containsBlockedPattern(String input) {
        if (input == null) {
            return false;
        }
        
        String lowercased = input.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lowercased.contains(pattern) || 
                    input.matches("(?i).*" + pattern + ".*")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Sanitize output for safe rendering (basic XSS prevention).
     */
    public String sanitizeOutput(String input) {
        if (input == null) {
            return null;
        }
        
        return input
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }
    
    /**
     * Check if the request is valid (convenience method).
     */
    public boolean isValid(DebateRequest request) {
        return validate(request).isEmpty();
    }
}
