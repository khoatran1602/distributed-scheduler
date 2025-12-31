package com.demo.scheduler.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Full response DTO containing the complete debate transcript.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateResponse {
    
    /**
     * Unique request ID for tracing.
     */
    private String requestId;
    
    /**
     * The original question asked.
     */
    private String question;
    
    /**
     * Agent A's initial draft proposal.
     */
    private AgentOutput agentADraft;
    
    /**
     * Agent B's initial draft proposal.
     */
    private AgentOutput agentBDraft;
    
    /**
     * Agent A's critique of Agent B's proposal.
     */
    private CritiqueOutput agentACritique;
    
    /**
     * Agent B's critique of Agent A's proposal.
     */
    private CritiqueOutput agentBCritique;
    
    /**
     * Judge's final synthesis and answer.
     */
    private JudgeOutput judgeSynthesis;
    
    /**
     * Total processing time in milliseconds.
     */
    private long processingTimeMs;
    
    /**
     * Timestamp when the debate was completed.
     */
    private Instant completedAt;
    
    /**
     * Whether the debate completed successfully.
     */
    private boolean success;
    
    /**
     * Error message if the debate failed.
     */
    private String errorMessage;
}
