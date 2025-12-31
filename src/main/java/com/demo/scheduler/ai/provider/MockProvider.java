package com.demo.scheduler.ai.provider;

import com.demo.scheduler.ai.dto.AgentOutput;
import com.demo.scheduler.ai.dto.CritiqueOutput;
import com.demo.scheduler.ai.dto.JudgeOutput;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Mock implementation of AIProvider for testing/demo purposes.
 * Returns pre-canned responses to avoid API costs/errors.
 */
@Component
public class MockProvider implements AIProvider {

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return "sim-v1";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AgentOutput generateDraft(String question, String context, String agentRole) {
        // Simulate thinking delay
        sleep(1000);
        
        return AgentOutput.builder()
                .proposal("The optimal solution involves a hybrid microservices architecture using event-driven patterns. We should decompose the monolith by domain boundaries, starting with the least dependent modules (User, Notification) while keeping the core transaction engine in the legacy system during the transition.")
                .assumptions(Arrays.asList(
                        "Network latency between services is negligible",
                        "Team has sufficient DevOps maturity for Kubernetes",
                        "Data consistency can be eventually consistent for non-financial operations"
                ))
                .risks(Arrays.asList(
                        "Distributed transactions complexity (Saga pattern required)",
                        "Operational overhead of managing multiple services",
                        "Potential data duplication across bounded contexts"
                ))
                .shortRationale("Minimizes risk while enabling independent scaling and deployment for high-velocity teams.")
                .providerModel(getProviderName() + "/" + getModelName())
                .build();
    }

    @Override
    public CritiqueOutput generateCritique(String originalQuestion, AgentOutput proposalToCritique, String critiqueRole) {
        sleep(1000);
        
        return CritiqueOutput.builder()
                .strengths("The hybrid approach correctly identifies the need for a gradual migration, reducing the 'big bang' rewrite risk.")
                .weaknesses("The proposal underestimates the complexity of 'eventually consistent' data in a financial system. It also fails to address how the legacy monolith will communicate with new services (ACL layer).")
                .suggestions("Introduce an Anti-Corruption Layer (ACL) explicitly. Prioritize extracting the 'Auth' service first rather than 'Notification' to secure the new perimeter.")
                .overallAssessment("moderate")
                .providerModel(getProviderName() + "/" + getModelName())
                .build();
    }

    @Override
    public JudgeOutput generateJudgeSynthesis(String originalQuestion, AgentOutput agentADraft, AgentOutput agentBDraft, CritiqueOutput agentACritique, CritiqueOutput agentBCritique) {
        sleep(1500);
        
        return JudgeOutput.builder()
                .finalAnswer("We will adopt a **Strangler Fig Pattern** strategies. We proceed with the hybrid microservices approach but strictly enforce an Anti-Corruption Layer (ACL) between the monolith and new services. We will NOT migrate the core transaction engine until the peripheral services (User, Auth) are stable.")
                .actionPlan(Arrays.asList(
                        "Implement an API Gateway to route traffic",
                        "Build Anti-Corruption Layer (ACL) around the legacy Monolith",
                        "Extract 'User' service first to establish identity management",
                        "Implement centralized logging and distributed tracing (OpenTelemetry)"
                ))
                .tradeoffs(Arrays.asList(
                        "Initial velocity will be slower due to ACL setup",
                        "Duplicate data storage required during transition phase"
                ))
                .risks(Arrays.asList(
                        "ACL becoming a 'god service' if not strictly scoped",
                        "Latency increase due to extra hops through Gateway and ACL"
                ))
                .rejectedAndWhy(Arrays.asList(
                        "Big Bang Rewrite - Too high risk of business continuity failure",
                        "Serverless-First - Too big of a paradigm shift for the current team"
                ))
                .providerModel(getProviderName() + "/" + getModelName())
                .build();
    }
    
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
