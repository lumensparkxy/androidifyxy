#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$SCRIPT_DIR"
SERVICE_NAME="${AGENT_SERVICE_NAME:-krishi-agent-service}"
PROJECT="${GOOGLE_CLOUD_PROJECT:?GOOGLE_CLOUD_PROJECT must be set}"
REGION="${GOOGLE_CLOUD_LOCATION:-asia-south1}"
IMAGE="gcr.io/${PROJECT}/${SERVICE_NAME}"
APP_NAME="${AGENT_APP_NAME:-krishi_agent_service}"
MODEL="${AGENT_MODEL:-gemini-2.5-flash}"
MAX_IMAGE_BYTES="${AGENT_MAX_IMAGE_BYTES:-5242880}"
IMAGE_TIMEOUT="${AGENT_IMAGE_DOWNLOAD_TIMEOUT_SECONDS:-20}"
PERSIST_SESSIONS="${AGENT_PERSIST_SESSIONS_TO_FIRESTORE:-true}"
SESSION_COLLECTION="${AGENT_SESSION_COLLECTION:-agent_runtime_sessions}"
USER_STATE_COLLECTION="${AGENT_USER_STATE_COLLECTION:-agent_runtime_user_state}"
APP_STATE_COLLECTION="${AGENT_APP_STATE_COLLECTION:-agent_runtime_app_state}"
ALLOW_UNAUTHENTICATED="${ALLOW_UNAUTHENTICATED:-false}"

printf '\n[1/2] Building container image %s\n' "$IMAGE"
gcloud builds submit "$SERVICE_DIR" --tag "$IMAGE"

ENV_VARS=(
  "GOOGLE_CLOUD_PROJECT=${PROJECT}"
  "GOOGLE_CLOUD_LOCATION=${REGION}"
  "AGENT_APP_NAME=${APP_NAME}"
  "AGENT_MODEL=${MODEL}"
  "AGENT_MAX_IMAGE_BYTES=${MAX_IMAGE_BYTES}"
  "AGENT_IMAGE_DOWNLOAD_TIMEOUT_SECONDS=${IMAGE_TIMEOUT}"
  "AGENT_PERSIST_SESSIONS_TO_FIRESTORE=${PERSIST_SESSIONS}"
  "AGENT_SESSION_COLLECTION=${SESSION_COLLECTION}"
  "AGENT_USER_STATE_COLLECTION=${USER_STATE_COLLECTION}"
  "AGENT_APP_STATE_COLLECTION=${APP_STATE_COLLECTION}"
)

if [[ -n "${AGENT_SERVICE_SHARED_SECRET:-}" ]]; then
  ENV_VARS+=("AGENT_SERVICE_SHARED_SECRET=${AGENT_SERVICE_SHARED_SECRET}")
fi

if [[ -n "${GOOGLE_API_KEY:-}" ]]; then
  ENV_VARS+=("GOOGLE_API_KEY=${GOOGLE_API_KEY}")
  ENV_VARS+=("GOOGLE_GENAI_USE_VERTEXAI=False")
else
  ENV_VARS+=("GOOGLE_GENAI_USE_VERTEXAI=True")
fi

ENV_STRING=$(IFS=, ; echo "${ENV_VARS[*]}")

deploy_args=(
  gcloud run deploy "$SERVICE_NAME"
  --project "$PROJECT"
  --region "$REGION"
  --image "$IMAGE"
  --port 8080
  --memory 1Gi
  --timeout 600
  --set-env-vars "$ENV_STRING"
)

if [[ "$ALLOW_UNAUTHENTICATED" == "true" ]]; then
  deploy_args+=(--allow-unauthenticated)
else
  deploy_args+=(--no-allow-unauthenticated)
fi

printf '\n[2/2] Deploying Cloud Run service %s\n' "$SERVICE_NAME"
"${deploy_args[@]}"

printf '\nDeployment complete.\n'
