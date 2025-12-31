package com.demo.scheduler.ai.service;

import com.demo.scheduler.ai.dto.*;
import com.demo.scheduler.ai.provider.AIProvider;
import com.demo.scheduler.ai.validation.DebateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MultiAgentOrchestrator.
 */
class MultiAgentOrchestratorTest {
    
    @Mock
    private AIProvider openAiProvider;
    
    @Mock
    private AIProvider geminiProvider;
    
    private DebateValidator validator;
    private MultiAgentOrchestrator orchestrator;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up validator
        validator = new DebateValidator();
        setField(validator, "maxQuestionLength", 10000);
        setField(validator, "maxContextLength", 5000);
        setField(validator, "minQuestionLength", 10);
        
        // Set up mock providers
        when(openAiProvider.getProviderName()).thenReturn("openai");
        when(openAiProvider.getModelName()).thenReturn("gpt-4");
        when(openAiProvider.isAvailable()).thenReturn(true);
        
        when(geminiProvider.getProviderName()).thenReturn("gemini");
        when(geminiProvider.getModelName()).thenReturn("gemini-1.5-pro");
        when(geminiProvider.isAvailable()).thenReturn(true);
        
        // Create orchestrator with mock providers
        orchestrator = new MultiAgentOrchestrator(
                List.of(openAiProvider, geminiProvider),
                validator
        );
        
        // Set default providers via reflection
        setField(orchestrator, "orchestratorTimeoutSeconds", 120);
        setField(orchestrator, "defaultAgentAProvider", "openai");
        setField(orchestrator, "defaultAgentBProvider", "gemini");
        setField(orchestrator, "defaultJudgeProvider", "openai");
    }
    
    @Test
    @DisplayName("Successful debate execution with all phases")
    void successfulDebate_allPhasesComplete() {
        // Set up mock responses
        AgentOutput agentADraft = AgentOutput.builder()
                .proposal("Agent A's pragmatic proposal")
                .assumptions(List.of("Assumption 1"))
                .risks(List.of("Risk 1"))
                .shortRationale("Pragmatic approach")
                .providerModel("openai/gpt-4")
                .build();
        
        AgentOutput agentBDraft = AgentOutput.builder()
                .proposal("Agent B's innovative proposal")
                .assumptions(List.of("Assumption 2"))
                .risks(List.of("Risk 2"))
                .shortRationale("Innovative approach")
                .providerModel("gemini/gemini-1.5-pro")
                .build();
        
        CritiqueOutput critiqueA = CritiqueOutput.builder()
                .strengths("Good points")
                .weaknesses("Some gaps")
                .suggestions("Consider X")
                .overallAssessment("moderate")
                .providerModel("openai/gpt-4")
                .build();
        
        CritiqueOutput critiqueB = CritiqueOutput.builder()
                .strengths("Solid foundation")
                .weaknesses("Needs refinement")
                .suggestions("Add Y")
                .overallAssessment("strong")
                .providerModel("gemini/gemini-1.5-pro")
                .build();
        
        JudgeOutput judgeSynthesis = JudgeOutput.builder()
                .finalAnswer("Synthesized answer combining both approaches")
                .actionPlan(List.of("Step 1", "Step 2"))
                .tradeoffs(List.of("Speed vs Quality"))
                .risks(List.of("Remaining risk"))
                .rejectedAndWhy(List.of("Rejected idea - reason"))
                .providerModel("openai/gpt-4")
                .build();
        
        // Configure mocks
        when(openAiProvider.generateDraft(anyString(), any(), anyString())).thenReturn(agentADraft);
        when(geminiProvider.generateDraft(anyString(), any(), anyString())).thenReturn(agentBDraft);
        when(openAiProvider.generateCritique(anyString(), any(), anyString())).thenReturn(critiqueA);
        when(geminiProvider.generateCritique(anyString(), any(), anyString())).thenReturn(critiqueB);
        when(openAiProvider.generateJudgeSynthesis(anyString(), any(), any(), any(), any())).thenReturn(judgeSynthesis);
        
        // Execute
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .context("We have a monolithic application")
                .build();
        
        DebateResponse response = orchestrator.executeDebate(request);
        
        // Verify
        assertTrue(response.isSuccess());
        assertNotNull(response.getRequestId());
        assertNotNull(response.getAgentADraft());
        assertNotNull(response.getAgentBDraft());
        assertNotNull(response.getAgentACritique());
        assertNotNull(response.getAgentBCritique());
        assertNotNull(response.getJudgeSynthesis());
        assertTrue(response.getProcessingTimeMs() >= 0);
        
        // Verify all phases were called
        verify(openAiProvider).generateDraft(anyString(), any(), anyString());
        verify(geminiProvider).generateDraft(anyString(), any(), anyString());
        verify(openAiProvider).generateCritique(anyString(), any(), anyString());
        verify(geminiProvider).generateCritique(anyString(), any(), anyString());
        verify(openAiProvider).generateJudgeSynthesis(anyString(), any(), any(), any(), any());
    }
    
    @Test
    @DisplayName("Invalid request returns error response")
    void invalidRequest_returnsErrorResponse() {
        DebateRequest request = DebateRequest.builder()
                .question("Hi") // Too short
                .build();
        
        DebateResponse response = orchestrator.executeDebate(request);
        
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("Validation failed"));
    }
    
    @Test
    @DisplayName("Provider failure returns error response")
    void providerFailure_returnsErrorResponse() {
        when(openAiProvider.generateDraft(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("API Error"));
        
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .build();
        
        DebateResponse response = orchestrator.executeDebate(request);
        
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
    }
    
    @Test
    @DisplayName("Get provider status returns all providers")
    void getProviderStatus_returnsAllProviders() {
        var status = orchestrator.getProviderStatus();
        
        assertTrue(status.containsKey("openai"));
        assertTrue(status.containsKey("gemini"));
        assertTrue(status.get("openai"));
        assertTrue(status.get("gemini"));
    }
    
    @Test
    @DisplayName("Unavailable provider triggers fallback")
    void unavailableProvider_usesFallback() {
        when(geminiProvider.isAvailable()).thenReturn(false);
        
        AgentOutput mockDraft = AgentOutput.builder()
                .proposal("Test")
                .assumptions(List.of())
                .risks(List.of())
                .shortRationale("Test")
                .providerModel("openai/gpt-4")
                .build();
        
        CritiqueOutput mockCritique = CritiqueOutput.builder()
                .strengths("Test")
                .weaknesses("Test")
                .suggestions("Test")
                .overallAssessment("moderate")
                .providerModel("openai/gpt-4")
                .build();
        
        JudgeOutput mockJudge = JudgeOutput.builder()
                .finalAnswer("Test")
                .actionPlan(List.of())
                .tradeoffs(List.of())
                .risks(List.of())
                .rejectedAndWhy(List.of())
                .providerModel("openai/gpt-4")
                .build();
        
        when(openAiProvider.generateDraft(anyString(), any(), anyString())).thenReturn(mockDraft);
        when(openAiProvider.generateCritique(anyString(), any(), anyString())).thenReturn(mockCritique);
        when(openAiProvider.generateJudgeSynthesis(anyString(), any(), any(), any(), any())).thenReturn(mockJudge);
        
        DebateRequest request = DebateRequest.builder()
                .question("What is the best approach to implement microservices?")
                .agentBProvider("gemini") // Request unavailable provider
                .build();
        
        DebateResponse response = orchestrator.executeDebate(request);
        
        // Should still succeed by falling back to available provider
        assertTrue(response.isSuccess());
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
