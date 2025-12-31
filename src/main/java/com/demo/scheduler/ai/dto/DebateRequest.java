package com.demo.scheduler.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for initiating a multi-agent debate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateRequest {
    
    /**
     * The user's question or topic for debate.
     */
    @NotBlank(message = "Question cannot be empty")
    @Size(min = 10, max = 10000, message = "Question must be between 10 and 10000 characters")
    private String question;
    
    /**
     * Optional context or background information.
     */
    @Size(max = 5000, message = "Context cannot exceed 5000 characters")
    private String context;
    
    /**
     * Preferred provider for Agent A (optional). Default: openai
     */
    private String agentAProvider;
    
    /**
     * Preferred provider for Agent B (optional). Default: gemini
     */
    private String agentBProvider;
    
    /**
     * Preferred provider for Judge (optional). Default: openai
     */
    private String judgeProvider;
}
