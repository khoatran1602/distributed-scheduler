package com.demo.scheduler.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Critique output when one agent critiques another's proposal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CritiqueOutput {
    
    /**
     * Strengths identified in the original proposal.
     */
    private String strengths;
    
    /**
     * Weaknesses or gaps in the original proposal.
     */
    private String weaknesses;
    
    /**
     * Suggested improvements or alternatives.
     */
    private String suggestions;
    
    /**
     * Overall assessment (e.g., "strong", "moderate", "weak").
     */
    private String overallAssessment;
    
    /**
     * Provider and model that generated this critique.
     */
    private String providerModel;
}
