package com.demo.scheduler.ai.provider;

import com.demo.scheduler.ai.dto.AgentOutput;
import com.demo.scheduler.ai.dto.CritiqueOutput;
import com.demo.scheduler.ai.dto.JudgeOutput;

/**
 * Abstract interface for AI providers (OpenAI, Gemini, etc.).
 * Enables provider-agnostic orchestration.
 */
public interface AIProvider {
    
    /**
     * Get the provider name (e.g., "openai", "gemini").
     */
    String getProviderName();
    
    /**
     * Get the model being used (e.g., "gpt-4", "gemini-1.5-pro").
     */
    String getModelName();
    
    /**
     * Generate an agent draft proposal for the given question.
     *
     * @param question The user's question
     * @param context Optional context
     * @param agentRole The role of this agent (e.g., "Agent A - Pragmatic", "Agent B - Innovative")
     * @return Structured agent output
     */
    AgentOutput generateDraft(String question, String context, String agentRole);
    
    /**
     * Generate a critique of another agent's proposal.
     *
     * @param originalQuestion The original question
     * @param proposalToCritique The proposal being critiqued
     * @param critiqueRole The role of the critiquing agent
     * @return Structured critique output
     */
    CritiqueOutput generateCritique(String originalQuestion, AgentOutput proposalToCritique, String critiqueRole);
    
    /**
     * Generate the judge's final synthesis.
     *
     * @param originalQuestion The original question
     * @param agentADraft Agent A's proposal
     * @param agentBDraft Agent B's proposal
     * @param agentACritique Agent A's critique of B
     * @param agentBCritique Agent B's critique of A
     * @return Structured judge output with final answer
     */
    JudgeOutput generateJudgeSynthesis(
            String originalQuestion,
            AgentOutput agentADraft,
            AgentOutput agentBDraft,
            CritiqueOutput agentACritique,
            CritiqueOutput agentBCritique
    );
    
    /**
     * Check if the provider is available and configured.
     */
    boolean isAvailable();
}
