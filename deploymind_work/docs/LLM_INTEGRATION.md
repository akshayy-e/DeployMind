# DeployMind LLM Integration

## Runtime flow

```text
Current incident
      |
      v
Hindsight Recall (optional for baseline)
      |
      v
Historical operational evidence
      |
      +-------- Current incident facts
      |
      v
Groq LLM (Structured Outputs)
      |
      v
Typed JSON incident analysis
      |
      v
Human approval
      |
      v
Resolution outcome -> Hindsight Retain
```

## Why this design

- **Hindsight** supplies long-term operational experience.
- **Groq** supplies language reasoning and explanation.
- **Spring Boot** orchestrates the agent and validates the application flow.
- The current incident remains the primary source of truth.
- Historical memory is treated as evidence, not an automatic command.
- The LLM never executes production changes.

## Structured response

The Groq call requests a strict JSON schema containing:

- category
- recommendation
- confidence
- rationale
- investigation_steps
- evidence_to_check
- memory_relevance
- historical_insights
- risk_flags

This keeps the React contract predictable and makes the agent output easy to audit.

## Before vs after memory

When `POST /api/analyze` is called with `use_memory=true`, the backend also runs the same LLM prompt with an empty memory list to produce a baseline. The UI can then compare the same model with and without Hindsight context.

When `use_memory=false`, the endpoint runs the same LLM without Hindsight context. This is the fair baseline for the demo.
