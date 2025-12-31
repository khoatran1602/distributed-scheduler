package com.demo.scheduler.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured output from the Judge agent.
 * Synthesizes debate results into final answer with action plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeOutput {
    
    /**
     * The final synthesized answer after considering all perspectives.
     */
    private String finalAnswer;
    
    /**
     * Concrete action items or next steps.
     */
    private List<String> actionPlan;
    
    /**
     * Trade-offs considered in reaching the final answer.
     */
    private List<String> tradeoffs;
    
    /**
     * Risks that remain even with the chosen approach.
     */
    private List<String> risks;
    
    /**
     * Ideas or proposals that were rejected with explanations.
     */
    private List<String> rejectedAndWhy;
    
    /**
     * Provider and model that generated this output (e.g., "gemini/gemini-1.5-pro").
     */
    private String providerModel;
}
