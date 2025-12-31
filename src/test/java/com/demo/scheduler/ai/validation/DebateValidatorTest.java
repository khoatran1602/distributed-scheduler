package com.demo.scheduler.ai.validation;

import com.demo.scheduler.ai.dto.DebateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DebateValidator.
 */
class DebateValidatorTest {
    
    private DebateValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new DebateValidator();
        // Use reflection to set default values since @Value won't work in unit tests
        setField(validator, "maxQuestionLength", 10000);
        setField(validator, "maxContextLength", 5000);
        setField(validator, "minQuestionLength", 10);
    }
    
    @Test
    @DisplayName("Valid request passes validation")
    void validRequest_passesValidation() {
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .context("We have a monolithic application that needs modernization.")
                .agentAProvider("openai")
                .agentBProvider("gemini")
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertTrue(errors.isEmpty(), "Expected no validation errors");
        assertTrue(validator.isValid(request));
    }
    
    @Test
    @DisplayName("Null request fails validation")
    void nullRequest_failsValidation() {
        List<String> errors = validator.validate(null);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("null")));
    }
    
    @Test
    @DisplayName("Empty question fails validation")
    void emptyQuestion_failsValidation() {
        DebateRequest request = DebateRequest.builder()
                .question("")
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("empty")));
    }
    
    @Test
    @DisplayName("Question too short fails validation")
    void shortQuestion_failsValidation() {
        DebateRequest request = DebateRequest.builder()
                .question("Hi?")
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("at least")));
    }
    
    @Test
    @DisplayName("Question too long fails validation")
    void longQuestion_failsValidation() {
        String longQuestion = "A".repeat(10001);
        DebateRequest request = DebateRequest.builder()
                .question(longQuestion)
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceed")));
    }
    
    @Test
    @DisplayName("Context too long fails validation")
    void longContext_failsValidation() {
        String longContext = "A".repeat(5001);
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .context(longContext)
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Context")));
    }
    
    @Test
    @DisplayName("Invalid provider fails validation")
    void invalidProvider_failsValidation() {
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .agentAProvider("invalid-provider")
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Invalid provider")));
    }
    
    @Test
    @DisplayName("Script tag in question is blocked")
    void scriptInQuestion_failsValidation() {
        DebateRequest request = DebateRequest.builder()
                .question("What about this? <script>alert('xss')</script>")
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("unsafe")));
    }
    
    @Test
    @DisplayName("JavaScript protocol in context is blocked")
    void javascriptInContext_failsValidation() {
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .context("Check this link: javascript:alert('xss')")
                .build();
        
        List<String> errors = validator.validate(request);
        
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("unsafe")));
    }
    
    @Test
    @DisplayName("Sanitize output escapes HTML")
    void sanitizeOutput_escapesHtml() {
        String input = "<script>alert('xss')</script>";
        String sanitized = validator.sanitizeOutput(input);
        
        assertFalse(sanitized.contains("<"));
        assertFalse(sanitized.contains(">"));
        assertTrue(sanitized.contains("&lt;"));
        assertTrue(sanitized.contains("&gt;"));
    }
    
    @Test
    @DisplayName("Null input sanitization returns null")
    void sanitizeNull_returnsNull() {
        assertNull(validator.sanitizeOutput(null));
    }
    
    // Helper method to set private fields via reflection
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
