# Agent Service

Initial Python ADK service scaffold for the Krishi AI agentic chat refactor.

## Current scope
- Root coordinator agent with sub-agents for advice, image diagnosis, clarification, and lead creation.
- Firestore-backed tools for farmer profile, knowledge metadata, mandi prices, and direct sales lead creation.
- FastAPI `/chat` endpoint suitable for a Firebase Functions proxy.
- Guardrail callbacks for empty turns and premature lead creation.
- Multimodal request assembly that downloads Firebase Storage image URLs and sends image bytes to the model.

## Important notes
- Session persistence now defaults to Firestore in Cloud Run via `FirestoreSessionService`, with in-memory fallback available for local/dev use.
- The Android product recommendation parser contract should be preserved when agent prompts start recommending products.

## Local run

From `agent_service/`:

- `uvicorn agent_service.main:app --host 0.0.0.0 --port 8080`

## Cloud Run container build

The included `Dockerfile` is Cloud Run ready. Build and run locally from the repository root:

- `docker build -f agent_service/Dockerfile -t krishi-agent-service ./agent_service`
- `docker run --rm -p 8080:8080 --env-file .env krishi-agent-service`

## Deployment notes

- Set `AGENT_SERVICE_SHARED_SECRET` and configure the same value in Firebase Functions.
- Set `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_LOCATION`, and either Vertex AI auth or `GOOGLE_API_KEY`.
- Firestore session persistence can be controlled with `AGENT_PERSIST_SESSIONS_TO_FIRESTORE` and the collection-name env vars.
- Keep the Cloud Run service non-public and call it only through the Firebase Functions proxy.
