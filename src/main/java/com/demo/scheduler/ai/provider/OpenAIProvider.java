package com.demo.scheduler.ai.provider;

import com.demo.scheduler.ai.dto.AgentOutput;
import com.demo.scheduler.ai.dto.CritiqueOutput;
import com.demo.scheduler.ai.dto.JudgeOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * OpenAI implementation of AIProvider.
 * Uses GPT-4 for high-quality structured outputs.
 */
@Component
public class OpenAIProvider implements AIProvider {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);
    
    @Value("${ai.openai.api-key:}")
    private String apiKey;
    
    @Value("${ai.openai.model:gpt-4}")
    private String model;
    
    @Value("${ai.openai.timeout-seconds:60}")
    private int timeoutSeconds;
    
    private OpenAiService openAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
            log.info("OpenAI provider initialized with model: {}", model);
        } else {
            log.warn("OpenAI API key not configured. Provider will be unavailable.");
        }
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public String getModelName() {
        return model;
    }
    
    @Override
    public boolean isAvailable() {
        return openAiService != null;
    }
    
    @Override
    public AgentOutput generateDraft(String question, String context, String agentRole) {
        String systemPrompt = buildDraftSystemPrompt(agentRole);
        String userPrompt = buildDraftUserPrompt(question, context);
        
        String response = callOpenAI(systemPrompt, userPrompt);
        return parseDraftResponse(response);
    }
    
    @Override
    public CritiqueOutput generateCritique(String originalQuestion, AgentOutput proposalToCritique, String critiqueRole) {
        String systemPrompt = buildCritiqueSystemPrompt(critiqueRole);
        String userPrompt = buildCritiqueUserPrompt(originalQuestion, proposalToCritique);
        
        String response = callOpenAI(systemPrompt, userPrompt);
        return parseCritiqueResponse(response);
    }
    
    @Override
    public JudgeOutput generateJudgeSynthesis(
            String originalQuestion,
            AgentOutput agentADraft,
            AgentOutput agentBDraft,
            CritiqueOutput agentACritique,
            CritiqueOutput agentBCritique) {
        
        String systemPrompt = buildJudgeSystemPrompt();
        String userPrompt = buildJudgeUserPrompt(originalQuestion, agentADraft, agentBDraft, agentACritique, agentBCritique);
        
        String response = callOpenAI(systemPrompt, userPrompt);
        return parseJudgeResponse(response);
    }
    
    private String callOpenAI(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI provider is not configured");
        }
        
        List<ChatMessage> messages = Arrays.asList(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        );
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(0.7)
                .maxTokens(2000)
                .build();
        
        try {
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            return result.getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to get response from OpenAI: " + e.getMessage(), e);
        }
    }
    
    private String buildDraftSystemPrompt(String agentRole) {
        return """
            You are %s, an expert analyst participating in a structured debate.
            
            You must respond with ONLY a valid JSON object in this exact format:
            {
                "proposal": "Your detailed proposal here",
                "assumptions": ["assumption 1", "assumption 2"],
                "risks": ["risk 1", "risk 2"],
                "shortRationale": "Brief summary of your reasoning"
            }
            
            Do not include any text before or after the JSON.
            """.formatted(agentRole);
    }
    
    private String buildDraftUserPrompt(String question, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question: ").append(question);
        if (context != null && !context.isBlank()) {
            sb.append("\n\nContext: ").append(context);
        }
        sb.append("\n\nProvide your structured proposal as JSON.");
        return sb.toString();
    }
    
    private String buildCritiqueSystemPrompt(String critiqueRole) {
        return """
            You are %s, critiquing another agent's proposal.
            
            You must respond with ONLY a valid JSON object in this exact format:
            {
                "strengths": "What the proposal does well",
                "weaknesses": "Gaps or issues in the proposal",
                "suggestions": "How the proposal could be improved",
                "overallAssessment": "strong|moderate|weak"
            }
            
            Be constructive but thorough. Do not include any text before or after the JSON.
            """.formatted(critiqueRole);
    }
    
    private String buildCritiqueUserPrompt(String originalQuestion, AgentOutput proposal) {
        return """
            Original Question: %s
            
            Proposal to Critique:
            - Proposal: %s
            - Assumptions: %s
            - Risks: %s
            - Rationale: %s
            
            Provide your structured critique as JSON.
            """.formatted(
                originalQuestion,
                proposal.getProposal(),
                String.join(", ", proposal.getAssumptions()),
                String.join(", ", proposal.getRisks()),
                proposal.getShortRationale()
        );
    }
    
    private String buildJudgeSystemPrompt() {
        return """
            You are an impartial Judge synthesizing a debate between two agents.
            
            You must respond with ONLY a valid JSON object in this exact format:
            {
                "finalAnswer": "Your synthesized final answer",
                "actionPlan": ["action 1", "action 2"],
                "tradeoffs": ["tradeoff 1", "tradeoff 2"],
                "risks": ["remaining risk 1", "remaining risk 2"],
                "rejectedAndWhy": ["rejected idea 1 - reason", "rejected idea 2 - reason"]
            }
            
            Consider all perspectives fairly. Do not include any text before or after the JSON.
            """;
    }
    
    private String buildJudgeUserPrompt(
            String originalQuestion,
            AgentOutput agentADraft,
            AgentOutput agentBDraft,
            CritiqueOutput agentACritique,
            CritiqueOutput agentBCritique) {
        
        return """
            Original Question: %s
            
            === AGENT A's PROPOSAL ===
            Proposal: %s
            Assumptions: %s
            Risks: %s
            Rationale: %s
            
            === AGENT B's PROPOSAL ===
            Proposal: %s
            Assumptions: %s
            Risks: %s
            Rationale: %s
            
            === AGENT A's CRITIQUE OF B ===
            Strengths: %s
            Weaknesses: %s
            Suggestions: %s
            Assessment: %s
            
            === AGENT B's CRITIQUE OF A ===
            Strengths: %s
            Weaknesses: %s
            Suggestions: %s
            Assessment: %s
            
            Synthesize the best answer considering all perspectives. Provide your judgment as JSON.
            """.formatted(
                originalQuestion,
                agentADraft.getProposal(),
                String.join(", ", agentADraft.getAssumptions()),
                String.join(", ", agentADraft.getRisks()),
                agentADraft.getShortRationale(),
                agentBDraft.getProposal(),
                String.join(", ", agentBDraft.getAssumptions()),
                String.join(", ", agentBDraft.getRisks()),
                agentBDraft.getShortRationale(),
                agentACritique.getStrengths(),
                agentACritique.getWeaknesses(),
                agentACritique.getSuggestions(),
                agentACritique.getOverallAssessment(),
                agentBCritique.getStrengths(),
                agentBCritique.getWeaknesses(),
                agentBCritique.getSuggestions(),
                agentBCritique.getOverallAssessment()
        );
    }
    
    private AgentOutput parseDraftResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            
            return AgentOutput.builder()
                    .proposal(node.path("proposal").asText(""))
                    .assumptions(parseStringList(node.path("assumptions")))
                    .risks(parseStringList(node.path("risks")))
                    .shortRationale(node.path("shortRationale").asText(""))
                    .providerModel(getProviderName() + "/" + getModelName())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse draft response: {}", e.getMessage());
            return AgentOutput.builder()
                    .proposal(response)
                    .assumptions(List.of())
                    .risks(List.of("Parse error - raw response returned"))
                    .shortRationale("Failed to parse structured response")
                    .providerModel(getProviderName() + "/" + getModelName())
                    .build();
        }
    }
    
    private CritiqueOutput parseCritiqueResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            
            return CritiqueOutput.builder()
                    .strengths(node.path("strengths").asText(""))
                    .weaknesses(node.path("weaknesses").asText(""))
                    .suggestions(node.path("suggestions").asText(""))
                    .overallAssessment(node.path("overallAssessment").asText("moderate"))
                    .providerModel(getProviderName() + "/" + getModelName())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse critique response: {}", e.getMessage());
            return CritiqueOutput.builder()
                    .strengths("Parse error")
                    .weaknesses(response)
                    .suggestions("")
                    .overallAssessment("unknown")
                    .providerModel(getProviderName() + "/" + getModelName())
                    .build();
        }
    }
    
    private JudgeOutput parseJudgeResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);
            
            return JudgeOutput.builder()
                    .finalAnswer(node.path("finalAnswer").asText(""))
                    .actionPlan(parseStringList(node.path("actionPlan")))
                    .tradeoffs(parseStringList(node.path("tradeoffs")))
                    .risks(parseStringList(node.path("risks")))
                    .rejectedAndWhy(parseStringList(node.path("rejectedAndWhy")))
                    .providerModel(getProviderName() + "/" + getModelName())
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse judge response: {}", e.getMessage());
            return JudgeOutput.builder()
                    .finalAnswer(response)
                    .actionPlan(List.of())
                    .tradeoffs(List.of())
                    .risks(List.of("Parse error - raw response returned"))
                    .rejectedAndWhy(List.of())
                    .providerModel(getProviderName() + "/" + getModelName())
                    .build();
        }
    }
    
    private String extractJson(String response) {
        // Try to extract JSON from response (handle markdown code blocks)
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
    
    private List<String> parseStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }
}
