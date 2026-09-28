# DeployMind

### An AI Incident Response Agent That Remembers What Production Taught You

**Core loop:** Incident → Hindsight Recall → Groq LLM Reasoning → Human Approval → Resolution → Hindsight Retain → Better future response.

DeployMind is a human-in-the-loop SRE/incident-response copilot. It remembers symptoms, actions tried, failed remediations, successful resolutions, root causes, and deployment/configuration changes. On a later similar incident, that operational memory is supplied to the LLM so the response can be more specific instead of repeating a generic checklist.

## Stack
- Frontend: React + Vite
- Backend: **Java 17 + Spring Boot 3.2.5**
- Memory: Hindsight Cloud (REST API) with local demo fallback
- LLM: **Groq OpenAI-compatible Chat Completions**
- Default model: **`openai/gpt-oss-120b`**

## Run
### Backend
Open `backend` in IntelliJ as a Maven project and select **JDK 17**.

Set these environment variables before starting Spring Boot:

```env
GROQ_API_KEY=your_groq_key
HINDSIGHT_API_KEY=your_hindsight_key
HINDSIGHT_BANK_ID=deploymind
```

Then:

```bash
cd backend
mvn clean spring-boot:run
```

Backend: `http://localhost:8000`

### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Verify the LLM is enabled
Open:

`http://localhost:8000/api/health`

Expected after setting the key:

```json
{
  "llm_enabled": true,
  "llm_provider": "Groq",
  "llm_model": "openai/gpt-oss-120b"
}
```

The React header also shows **Groq LLM enabled** when the backend is configured.

## Hindsight
Create/configure a Hindsight bank and set:

```env
HINDSIGHT_API_URL=https://api.hindsight.vectorize.io
HINDSIGHT_API_KEY=your_key
HINDSIGHT_BANK_ID=deploymind
```

Without a Hindsight key, the project uses local demo memory. For the real competition demo, use Hindsight Cloud so the judges can see authentic Retain/Recall behavior.

## Judge demo
1. Start the Java backend with **GROQ_API_KEY** and **HINDSIGHT_API_KEY** configured.
2. Open the React frontend.
3. Click **Seed demo memory**.
4. Select the open Payment API incident.
5. Click **Compare without memory** to show the baseline LLM response without historical memory.
6. Click **Investigate with memory** to retrieve Hindsight evidence and ask Groq to reason with it.
7. Compare the recommendation and historical evidence.
8. Approve & resolve. The outcome is retained for future recall.

The important proof is not that the model can write a troubleshooting paragraph. It is that **historical Hindsight experience changes the agent's response to a later similar incident**.

## Fallback behavior
If Groq is not configured or temporarily fails, DeployMind falls back to its deterministic evidence-first investigation plans. The UI and `/api/health` expose whether the real LLM path is active.

Do not claim benchmark accuracy or downtime savings unless measured from your own evaluation set.
