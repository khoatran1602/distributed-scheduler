# Distributed Scheduler & AI Debate Orchestrator

A robust Spring Boot application that serves as both a distributed task scheduler AND a **Multi-Agent AI Orchestrator**.

## 🧠 New Feature: Multi-Agent Debate
The system now includes an intelligent orchestration layer that coordinates debates between different Large Language Models (LLMs).

### capabilities
*   **Orchestration**: Manages state and data flow between 3 agents (Proponent, Opponent, Judge).
*   **Validation**: Secure input validation, XSS prevention, and strict length controls.
*   **Provider Abstraction**: Seamlessly switch between OpenAI (GPT-4), Google Gemini (1.5 Pro), and Mock providers.
*   **Rate Limiting**: In-memory protection against abuse (10 requests/min).
*   **Error Handling**: User-friendly error messages without leaking stack traces.

## 🚀 Setup & Run (Demo Mode)

This project is currently configured for **"No-Docker Demo Mode"**. 
It uses an in-memory H2 database and in-memory rate limiting, so you don't need Redis or PostgreSQL installed.

### 1. Configure API Keys
To use real AI, you need API keys. 
**Recommended:** Use Gemini (Free Tier).

```powershell
# Windows PowerShell
$env:GEMINI_API_KEY = "your-google-api-key"
# Optional: $env:OPENAI_API_KEY = "your-openai-key"
```

### 2. Run the Application
```powershell
mvn spring-boot:run
```
The server will start on `http://localhost:8080`.

## 📚 API Documentation

### POST `/api/debate`
Initiate a new debate.

**Payload:**
```json
{
  "question": "Is Rust better than C++?",
  "agentAProvider": "gemini", 
  "agentBProvider": "gemini",
  "judgeProvider": "gemini"
}
```

**Response:**
Returns a structured `DebateResponse` containing drafts, critiques, and the final judge verdict.

## 🏗️ Architecture
*   **Core**: Java 17, Spring Boot 3.2
*   **Database**: H2 (In-Memory for Demo) / PostgreSQL (Production)
*   **Queue**: Redis/Kafka (Disabled for Demo)
*   **AI SDKs**: 
    *   `spring-ai` (OpenAI)
    *   Google Cloud Vertex AI / Gemini REST API
