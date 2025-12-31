package com.demo.scheduler.ai.provider;

import com.demo.scheduler.ai.dto.AgentOutput;
import com.demo.scheduler.ai.dto.CritiqueOutput;
import com.demo.scheduler.ai.dto.JudgeOutput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of AIProvider.
 * Uses Gemini 1.5 Pro via the Generative Language API.
 */
@Component
public class GeminiProvider implements AIProvider {
    
    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    
    @Value("${ai.gemini.api-key:}")
    private String apiKey;
    
    @Value("${ai.gemini.model:gemini-1.5-pro}")
    private String model;
    
    @Value("${ai.gemini.timeout-seconds:60}")
    private int timeoutSeconds;
    
    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean available = false;
    
    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            this.webClient = WebClient.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build();
            this.available = true;
            log.info("Gemini provider initialized with model: {}", model);
        } else {
            log.warn("Gemini API key not configured. Provider will be unavailable.");
        }
    }
    
    @Override
    public String getProviderName() {
        return "gemini";
    }
    
    @Override
    public String getModelName() {
        return model;
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public AgentOutput generateDraft(String question, String context, String agentRole) {
        String prompt = buildDraftPrompt(agentRole, question, context);
        String response = callGemini(prompt);
        return parseDraftResponse(response);
    }
    
    @Override
    public CritiqueOutput generateCritique(String originalQuestion, AgentOutput proposalToCritique, String critiqueRole) {
        String prompt = buildCritiquePrompt(critiqueRole, originalQuestion, proposalToCritique);
        String response = callGemini(prompt);
        return parseCritiqueResponse(response);
    }
    
    @Override
    public JudgeOutput generateJudgeSynthesis(
            String originalQuestion,
            AgentOutput agentADraft,
            AgentOutput agentBDraft,
            CritiqueOutput agentACritique,
            CritiqueOutput agentBCritique) {
        
        String prompt = buildJudgePrompt(originalQuestion, agentADraft, agentBDraft, agentACritique, agentBCritique);
        String response = callGemini(prompt);
        return parseJudgeResponse(response);
    }
    
    private String callGemini(String prompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("Gemini provider is not configured");
        }
        
        String url = String.format(GEMINI_API_URL, model, apiKey);
        
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 2048,
                        "topP", 0.95
                )
        );
        
        try {
            String responseBody = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
            
            // Parse Gemini response format
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            
            log.error("Unexpected Gemini response format: {}", responseBody);
            throw new RuntimeException("Failed to parse Gemini response");
            
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to get response from Gemini: " + e.getMessage(), e);
        }
    }
    
    private String buildDraftPrompt(String agentRole, String question, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(agentRole).append(", an expert analyst participating in a structured debate.\n\n");
        sb.append("You must respond with ONLY a valid JSON object in this exact format:\n");
        sb.append("{\n");
        sb.append("    \"proposal\": \"Your detailed proposal here\",\n");
        sb.append("    \"assumptions\": [\"assumption 1\", \"assumption 2\"],\n");
        sb.append("    \"risks\": [\"risk 1\", \"risk 2\"],\n");
        sb.append("    \"shortRationale\": \"Brief summary of your reasoning\"\n");
        sb.append("}\n\n");
        sb.append("Do not include any text before or after the JSON.\n\n");
        sb.append("Question: ").append(question);
        if (context != null && !context.isBlank()) {
            sb.append("\n\nContext: ").append(context);
        }
        sb.append("\n\nProvide your structured proposal as JSON.");
        return sb.toString();
    }
    
    private String buildCritiquePrompt(String critiqueRole, String originalQuestion, AgentOutput proposal) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(critiqueRole).append(", critiquing another agent's proposal.\n\n");
        sb.append("You must respond with ONLY a valid JSON object in this exact format:\n");
        sb.append("{\n");
        sb.append("    \"strengths\": \"What the proposal does well\",\n");
        sb.append("    \"weaknesses\": \"Gaps or issues in the proposal\",\n");
        sb.append("    \"suggestions\": \"How the proposal could be improved\",\n");
        sb.append("    \"overallAssessment\": \"strong|moderate|weak\"\n");
        sb.append("}\n\n");
        sb.append("Be constructive but thorough. Do not include any text before or after the JSON.\n\n");
        sb.append("Original Question: ").append(originalQuestion).append("\n\n");
        sb.append("Proposal to Critique:\n");
        sb.append("- Proposal: ").append(proposal.getProposal()).append("\n");
        sb.append("- Assumptions: ").append(String.join(", ", proposal.getAssumptions())).append("\n");
        sb.append("- Risks: ").append(String.join(", ", proposal.getRisks())).append("\n");
        sb.append("- Rationale: ").append(proposal.getShortRationale()).append("\n\n");
        sb.append("Provide your structured critique as JSON.");
        return sb.toString();
    }
    
    private String buildJudgePrompt(
            String originalQuestion,
            AgentOutput agentADraft,
            AgentOutput agentBDraft,
            CritiqueOutput agentACritique,
            CritiqueOutput agentBCritique) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("You are an impartial Judge synthesizing a debate between two agents.\n\n");
        sb.append("You must respond with ONLY a valid JSON object in this exact format:\n");
        sb.append("{\n");
        sb.append("    \"finalAnswer\": \"Your synthesized final answer\",\n");
        sb.append("    \"actionPlan\": [\"action 1\", \"action 2\"],\n");
        sb.append("    \"tradeoffs\": [\"tradeoff 1\", \"tradeoff 2\"],\n");
        sb.append("    \"risks\": [\"remaining risk 1\", \"remaining risk 2\"],\n");
        sb.append("    \"rejectedAndWhy\": [\"rejected idea 1 - reason\", \"rejected idea 2 - reason\"]\n");
        sb.append("}\n\n");
        sb.append("Consider all perspectives fairly. Do not include any text before or after the JSON.\n\n");
        
        sb.append("Original Question: ").append(originalQuestion).append("\n\n");
        
        sb.append("=== AGENT A's PROPOSAL ===\n");
        sb.append("Proposal: ").append(agentADraft.getProposal()).append("\n");
        sb.append("Assumptions: ").append(String.join(", ", agentADraft.getAssumptions())).append("\n");
        sb.append("Risks: ").append(String.join(", ", agentADraft.getRisks())).append("\n");
        sb.append("Rationale: ").append(agentADraft.getShortRationale()).append("\n\n");
        
        sb.append("=== AGENT B's PROPOSAL ===\n");
        sb.append("Proposal: ").append(agentBDraft.getProposal()).append("\n");
        sb.append("Assumptions: ").append(String.join(", ", agentBDraft.getAssumptions())).append("\n");
        sb.append("Risks: ").append(String.join(", ", agentBDraft.getRisks())).append("\n");
        sb.append("Rationale: ").append(agentBDraft.getShortRationale()).append("\n\n");
        
        sb.append("=== AGENT A's CRITIQUE OF B ===\n");
        sb.append("Strengths: ").append(agentACritique.getStrengths()).append("\n");
        sb.append("Weaknesses: ").append(agentACritique.getWeaknesses()).append("\n");
        sb.append("Suggestions: ").append(agentACritique.getSuggestions()).append("\n");
        sb.append("Assessment: ").append(agentACritique.getOverallAssessment()).append("\n\n");
        
        sb.append("=== AGENT B's CRITIQUE OF A ===\n");
        sb.append("Strengths: ").append(agentBCritique.getStrengths()).append("\n");
        sb.append("Weaknesses: ").append(agentBCritique.getWeaknesses()).append("\n");
        sb.append("Suggestions: ").append(agentBCritique.getSuggestions()).append("\n");
        sb.append("Assessment: ").append(agentBCritique.getOverallAssessment()).append("\n\n");
        
        sb.append("Synthesize the best answer considering all perspectives. Provide your judgment as JSON.");
        return sb.toString();
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
