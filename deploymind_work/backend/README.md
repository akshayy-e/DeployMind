# DeployMind — Java Spring Boot Backend

DeployMind is an AI incident-response copilot whose differentiator is persistent operational memory. The backend is **Java 17 + Spring Boot 3.2.5**.

## Requirements
- JDK 17
- Maven 3.9+
- Node.js 18+ for the frontend

## Run backend in IntelliJ
1. Open the `backend` folder as a Maven project.
2. Set Project SDK and Maven Runner JRE to **JDK 17**.
3. Reload Maven.
4. Run `com.deploymind.DeployMindApplication`.
5. API starts at `http://localhost:8000`.

Or:

```bash
cd backend
mvn clean spring-boot:run
```

## Enable the real LLM (Groq)
DeployMind now defaults to Groq's OpenAI-compatible Chat Completions endpoint.
Set **`GROQ_API_KEY`** in the backend run configuration/environment before starting Spring Boot.

```env
GROQ_API_KEY=your_groq_key
LLM_MODEL=openai/gpt-oss-120b
```

The default endpoint is:

```text
https://api.groq.com/openai/v1/chat/completions
```

The backend uses Groq Structured Outputs so the incident analysis is returned as predictable JSON. The model is instructed to distinguish current facts from Hindsight evidence, never invent infrastructure state, and leave the final production decision to a human.

You can override the endpoint/key/model with:

```env
LLM_API_URL=...
LLM_API_KEY=...
LLM_MODEL=...
```

`GROQ_API_KEY` takes precedence when supplied.

### Verify LLM status
Open:

```text
GET http://localhost:8000/api/health
```

Look for:

```json
{
  "llm_enabled": true,
  "llm_provider": "Groq",
  "llm_model": "openai/gpt-oss-120b"
}
```

## Hindsight Cloud
Set:

```env
HINDSIGHT_API_URL=https://api.hindsight.vectorize.io
HINDSIGHT_API_KEY=your_hindsight_key
HINDSIGHT_BANK_ID=deploymind
```

If the Hindsight key is empty, the app uses the local demo memory store so the UI can still be tested. For the actual hackathon submission, use a real Hindsight bank so Retain/Recall are real.

## Agent flow

```text
Current incident
      ↓
Hindsight Recall
      ↓
Historical operational experience
      ↓
Groq LLM reasoning
      ↓
Structured incident analysis
      ↓
Human approval
      ↓
Resolution outcome
      ↓
Hindsight Retain
```

## API
- `GET /api/health`
- `GET /api/incidents`
- `GET /api/metrics`
- `POST /api/seed-demo`
- `POST /api/judge-challenge`
- `POST /api/analyze`
- `POST /api/resolve`
- `POST /api/incidents`

## Frontend
From the project root:

```bash
cd frontend
npm install
npm run dev
```

The frontend defaults to `http://localhost:8000`. Override with `VITE_API_URL` if needed.

## Quick API smoke test

After starting the backend, verify configuration:

```text
GET http://localhost:8000/api/health
```

Then investigate the seeded open incident:

```http
POST http://localhost:8000/api/analyze
Content-Type: application/json

{
  "incident_id": "INC-004",
  "use_memory": true
}
```

With a valid Groq key, the response contains `llm_used: true`, the Groq provider/model, and the structured incident analysis. If Hindsight is configured, `memories` contains recalled operational experience.
