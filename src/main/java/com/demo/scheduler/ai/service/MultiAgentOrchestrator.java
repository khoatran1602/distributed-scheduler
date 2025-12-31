package com.demo.scheduler.ai.service;

import com.demo.scheduler.ai.dto.*;
import com.demo.scheduler.ai.provider.AIProvider;
import com.demo.scheduler.ai.validation.DebateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Multi-Agent Debate Orchestrator.
 * 
 * Coordinates the debate flow:
 * 1. Agent A generates draft
 * 2. Agent B generates draft (parallel with A)
 * 3. Agent A critiques B's draft
 * 4. Agent B critiques A's draft (parallel with A's critique)
 * 5. Judge synthesizes final answer
 */
@Service
public class MultiAgentOrchestrator {
    
    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestrator.class);
    
    private static final String AGENT_A_ROLE = "Agent A - Pragmatic Analyst";
    private static final String AGENT_B_ROLE = "Agent B - Creative Innovator";
    private static final String AGENT_A_CRITIQUE_ROLE = "Critical Reviewer (evaluating Agent B)";
    private static final String AGENT_B_CRITIQUE_ROLE = "Critical Reviewer (evaluating Agent A)";
    
    private final Map<String, AIProvider> providers;
    private final DebateValidator validator;
    private final ExecutorService executor;
    
    @Value("${ai.orchestrator.timeout-seconds:120}")
    private int orchestratorTimeoutSeconds;
    
    @Value("${ai.orchestrator.default-agent-a-provider:openai}")
    private String defaultAgentAProvider;
    
    @Value("${ai.orchestrator.default-agent-b-provider:gemini}")
    private String defaultAgentBProvider;
    
    @Value("${ai.orchestrator.default-judge-provider:openai}")
    private String defaultJudgeProvider;
    
    public MultiAgentOrchestrator(List<AIProvider> providerList, DebateValidator validator) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        AIProvider::getProviderName,
                        Function.identity()
                ));
        this.validator = validator;
        this.executor = Executors.newFixedThreadPool(4);
        
        log.info("MultiAgentOrchestrator initialized with providers: {}", providers.keySet());
    }
    
    /**
     * Execute a full multi-agent debate.
     * 
     * @param request The debate request
     * @return Complete debate response with all drafts, critiques, and judge synthesis
     */
    public DebateResponse executeDebate(DebateRequest request) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        
        long startTime = System.currentTimeMillis();
        log.info("Starting debate for question: {}", truncate(request.getQuestion(), 100));
        
        try {
            // Validate request
            List<String> errors = validator.validate(request);
            if (!errors.isEmpty()) {
                log.warn("Validation failed: {}", errors);
                return buildErrorResponse(requestId, request.getQuestion(), 
                        "Validation failed: " + String.join("; ", errors), startTime);
            }
            
            // Resolve providers
            AIProvider agentAProvider = resolveProvider(request.getAgentAProvider(), defaultAgentAProvider);
            AIProvider agentBProvider = resolveProvider(request.getAgentBProvider(), defaultAgentBProvider);
            AIProvider judgeProvider = resolveProvider(request.getJudgeProvider(), defaultJudgeProvider);
            
            // Phase 1: Generate drafts (parallel)
            log.info("Phase 1: Generating drafts...");
            CompletableFuture<AgentOutput> draftAFuture = CompletableFuture.supplyAsync(() -> 
                    agentAProvider.generateDraft(request.getQuestion(), request.getContext(), AGENT_A_ROLE),
                    executor);
            
            CompletableFuture<AgentOutput> draftBFuture = CompletableFuture.supplyAsync(() -> 
                    agentBProvider.generateDraft(request.getQuestion(), request.getContext(), AGENT_B_ROLE),
                    executor);
            
            AgentOutput agentADraft = draftAFuture.get(orchestratorTimeoutSeconds, TimeUnit.SECONDS);
            AgentOutput agentBDraft = draftBFuture.get(orchestratorTimeoutSeconds, TimeUnit.SECONDS);
            
            log.info("Phase 1 complete. Agent A: {}, Agent B: {}", 
                    agentADraft.getProviderModel(), agentBDraft.getProviderModel());
            
            // Phase 2: Generate critiques (parallel)
            log.info("Phase 2: Generating critiques...");
            CompletableFuture<CritiqueOutput> critiqueAFuture = CompletableFuture.supplyAsync(() -> 
                    agentAProvider.generateCritique(request.getQuestion(), agentBDraft, AGENT_A_CRITIQUE_ROLE),
                    executor);
            
            CompletableFuture<CritiqueOutput> critiqueBFuture = CompletableFuture.supplyAsync(() -> 
                    agentBProvider.generateCritique(request.getQuestion(), agentADraft, AGENT_B_CRITIQUE_ROLE),
                    executor);
            
            CritiqueOutput agentACritique = critiqueAFuture.get(orchestratorTimeoutSeconds, TimeUnit.SECONDS);
            CritiqueOutput agentBCritique = critiqueBFuture.get(orchestratorTimeoutSeconds, TimeUnit.SECONDS);
            
            log.info("Phase 2 complete. Critique A: {}, Critique B: {}", 
                    agentACritique.getProviderModel(), agentBCritique.getProviderModel());
            
            // Phase 3: Judge synthesis
            log.info("Phase 3: Judge synthesizing final answer...");
            JudgeOutput judgeSynthesis = judgeProvider.generateJudgeSynthesis(
                    request.getQuestion(),
                    agentADraft,
                    agentBDraft,
                    agentACritique,
                    agentBCritique
            );
            
            log.info("Phase 3 complete. Judge: {}", judgeSynthesis.getProviderModel());
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Debate completed in {}ms", processingTime);
            
            return DebateResponse.builder()
                    .requestId(requestId)
                    .question(request.getQuestion())
                    .agentADraft(agentADraft)
                    .agentBDraft(agentBDraft)
                    .agentACritique(agentACritique)
                    .agentBCritique(agentBCritique)
                    .judgeSynthesis(judgeSynthesis)
                    .processingTimeMs(processingTime)
                    .completedAt(Instant.now())
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Debate execution failed: {}", e.getMessage(), e);
            return buildErrorResponse(requestId, request.getQuestion(), 
                    "Debate failed: " + sanitizeErrorMessage(e.getMessage()), startTime);
        } finally {
            MDC.remove("requestId");
        }
    }
    
    private AIProvider resolveProvider(String requested, String defaultProvider) {
        String providerName = (requested != null && !requested.isBlank()) 
                ? requested.toLowerCase() 
                : defaultProvider;
        
        AIProvider provider = providers.get(providerName);
        if (provider == null || !provider.isAvailable()) {
            // Fallback to any available provider
            provider = providers.values().stream()
                    .filter(AIProvider::isAvailable)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No AI providers are available"));
            log.warn("Requested provider '{}' not available. Using fallback: {}", 
                    providerName, provider.getProviderName());
        }
        return provider;
    }
    
    private DebateResponse buildErrorResponse(String requestId, String question, String error, long startTime) {
        return DebateResponse.builder()
                .requestId(requestId)
                .question(question)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .completedAt(Instant.now())
                .success(false)
                .errorMessage(error)
                .build();
    }
    
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "Unknown error";
        }
        // Remove sensitive information from error messages
        return message
                .replaceAll("(?i)api[_-]?key[=:]?\\s*\\S+", "[REDACTED]")
                .replaceAll("(?i)bearer\\s+\\S+", "[REDACTED]")
                .replaceAll("(?i)authorization[=:]?\\s*\\S+", "[REDACTED]");
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    
    /**
     * Check which providers are available.
     */
    public Map<String, Boolean> getProviderStatus() {
        return providers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().isAvailable()
                ));
    }
}
