from __future__ import annotations

import logging

from google.adk.sessions import InMemorySessionService

from .app_config import config
from .firestore_session_service import FirestoreSessionService

logger = logging.getLogger(__name__)


def build_session_service():
    if config.persist_sessions_to_firestore:
        try:
            logger.info(
                "Using FirestoreSessionService with collections %s / %s / %s",
                config.session_collection,
                config.user_state_collection,
                config.app_state_collection,
            )
            return FirestoreSessionService(
                sessions_collection=config.session_collection,
                user_state_collection=config.user_state_collection,
                app_state_collection=config.app_state_collection,
            )
        except Exception as error:  # pragma: no cover - defensive fallback
            logger.warning(
                "Falling back to InMemorySessionService because Firestore session setup failed: %s",
                error,
            )
    else:
        logger.info("Using InMemorySessionService because Firestore persistence is disabled.")

    return InMemorySessionService()
