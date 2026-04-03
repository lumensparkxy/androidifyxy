from __future__ import annotations

import asyncio
import os
import time
import uuid

import pytest
from google.adk.events import Event, EventActions
from google.adk.sessions.base_session_service import GetSessionConfig
from google.genai import types

from agent_service.firestore_session_service import FirestoreSessionService


async def run_live_session_service_smoke() -> None:
    suffix = uuid.uuid4().hex[:8]
    app_name = f"agent-test-app-{suffix}"
    user_id = f"agent-test-user-{suffix}"
    session_id = f"agent-test-session-{suffix}"
    service = FirestoreSessionService(
        sessions_collection=f"agent_runtime_sessions_test_{suffix}",
        user_state_collection=f"agent_runtime_user_state_test_{suffix}",
        app_state_collection=f"agent_runtime_app_state_test_{suffix}",
    )

    session = await service.create_session(
        app_name=app_name,
        user_id=user_id,
        session_id=session_id,
        state={
            "app:backend_version": "v1",
            "user:locale": "en",
            "conversation_id": session_id,
        },
    )

    event = Event(
        author="test_agent",
        content=types.Content(role="model", parts=[types.Part(text="hello")]),
        actions=EventActions(
            state_delta={
                "session_key": "session_value",
                "user:preferred_crop": "soybean",
            }
        ),
        timestamp=time.time(),
    )

    await service.append_event(session, event)

    loaded = await service.get_session(
        app_name=app_name,
        user_id=user_id,
        session_id=session_id,
        config=GetSessionConfig(num_recent_events=5),
    )
    assert loaded is not None
    assert loaded.state["conversation_id"] == session_id
    assert loaded.state["session_key"] == "session_value"
    assert loaded.state["user:locale"] == "en"
    assert loaded.state["user:preferred_crop"] == "soybean"
    assert loaded.state["app:backend_version"] == "v1"
    assert len(loaded.events) == 1
    assert loaded.events[0].content.parts[0].text == "hello"

    listed = await service.list_sessions(app_name=app_name, user_id=user_id)
    assert len(listed.sessions) == 1

    await service.delete_session(app_name=app_name, user_id=user_id, session_id=session_id)
    deleted = await service.get_session(app_name=app_name, user_id=user_id, session_id=session_id)
    assert deleted is None


def test_run_live_session_service_smoke() -> None:
    google_cloud_project = (os.getenv("GOOGLE_CLOUD_PROJECT") or "").strip()
    if not google_cloud_project or google_cloud_project == "your-gcp-project-id":
        pytest.skip("Set GOOGLE_CLOUD_PROJECT to a real Firebase project to run the live Firestore smoke test.")

    asyncio.run(run_live_session_service_smoke())
