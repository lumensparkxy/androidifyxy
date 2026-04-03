from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


def _env_flag(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class AgentServiceConfig:
    app_name: str = os.getenv("AGENT_APP_NAME", "krishi_agent_service")
    default_model: str = os.getenv("AGENT_MODEL", "gemini-2.5-flash")
    shared_secret: str = os.getenv("AGENT_SERVICE_SHARED_SECRET", "")
    max_recent_messages: int = int(os.getenv("AGENT_MAX_RECENT_MESSAGES", "8"))
    knowledge_results_limit: int = int(os.getenv("AGENT_KNOWLEDGE_LIMIT", "5"))
    mandi_results_limit: int = int(os.getenv("AGENT_MANDI_LIMIT", "5"))
    max_image_bytes: int = int(os.getenv("AGENT_MAX_IMAGE_BYTES", str(5 * 1024 * 1024)))
    image_download_timeout_seconds: int = int(os.getenv("AGENT_IMAGE_DOWNLOAD_TIMEOUT_SECONDS", "20"))
    persist_sessions_to_firestore: bool = _env_flag("AGENT_PERSIST_SESSIONS_TO_FIRESTORE", True)
    session_collection: str = os.getenv("AGENT_SESSION_COLLECTION", "agent_runtime_sessions")
    user_state_collection: str = os.getenv("AGENT_USER_STATE_COLLECTION", "agent_runtime_user_state")
    app_state_collection: str = os.getenv("AGENT_APP_STATE_COLLECTION", "agent_runtime_app_state")


config = AgentServiceConfig()
