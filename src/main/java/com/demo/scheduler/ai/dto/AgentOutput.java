package com.demo.scheduler.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured output from an AI agent (Agent A or Agent B).
 * Contains a proposal with supporting assumptions, risks, and rationale.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOutput {
    
    /**
     * The agent's proposed answer or solution.
     */
    private String proposal;
    
    /**
     * Key assumptions the agent made while formulating the proposal.
     */
    private List<String> assumptions;
    
    /**
     * Potential risks or downsides of this proposal.
     */
    private List<String> risks;
    
    /**
     * Brief rationale explaining the reasoning (no chain-of-thought, just summary).
     */
    private String shortRationale;
    
    /**
     * Provider and model that generated this output (e.g., "openai/gpt-4").
     */
    private String providerModel;
}
