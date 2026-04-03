from __future__ import annotations

import asyncio
from dataclasses import dataclass

from fastapi import HTTPException
from google.genai import types

from agent_service import main
from agent_service.image_utils import build_inline_image_part
from agent_service.models import ChatRequest


@dataclass
class FakeSession:
    state: dict


class FakeSessionService:
    def __init__(self) -> None:
        self.sessions: dict[tuple[str, str, str], FakeSession] = {}

    async def get_session(self, app_name: str, user_id: str, session_id: str):
        return self.sessions.get((app_name, user_id, session_id))

    async def create_session(self, app_name: str, user_id: str, session_id: str, state: dict):
        session = FakeSession(state=dict(state))
        self.sessions[(app_name, user_id, session_id)] = session
        return session


class FakeEvent:
    def __init__(self, text: str, invocation_id: str = "inv-smoke") -> None:
        self.content = types.Content(role="model", parts=[types.Part(text=text)])
        self.invocation_id = invocation_id

    def is_final_response(self) -> bool:
        return True


class FakeRunner:
    def __init__(self, session_service: FakeSessionService, expected_parts: int) -> None:
        self.session_service = session_service
        self.expected_parts = expected_parts

    async def run_async(self, user_id: str, session_id: str, new_message: types.Content):
        assert len(new_message.parts) == self.expected_parts
        session = await self.session_service.get_session(
            app_name=main.config.app_name,
            user_id=user_id,
            session_id=session_id,
        )
        assert session is not None
        session.state["temp:lead_created"] = True
        session.state["temp:lead_request_number"] = "KR-20260101-ABC123"
        session.state["temp:cited_document_ids"] = ["doc-1"]
        yield FakeEvent("Diagnosis complete")


async def _invoke_chat(request: ChatRequest, expected_parts: int, include_image_part: bool) -> None:
    original_runner = main.runner
    original_session_service = main.session_service
    original_build_image_part = main.build_image_part
    fake_session_service = FakeSessionService()
    fake_runner = FakeRunner(session_service=fake_session_service, expected_parts=expected_parts)

    try:
        main.session_service = fake_session_service
        main.runner = fake_runner
        if include_image_part:
            main.build_image_part = lambda _url: build_inline_image_part(b"image-bytes", "image/png")

        response = await main.chat(
            request,
            x_agent_service_token=main.config.shared_secret or None,
        )
        assert response.text == "Diagnosis complete"
        assert response.metadata.leadCreated is True
        assert response.metadata.requestNumber == "KR-20260101-ABC123"
        assert response.metadata.citedDocumentIds == ["doc-1"]
    finally:
        main.runner = original_runner
        main.session_service = original_session_service
        main.build_image_part = original_build_image_part


def test_chat_text_smoke() -> None:
    asyncio.run(
        _invoke_chat(
            ChatRequest(
                userId="user-1",
                conversationId="conversation-1",
                message="My tomato leaves have spots.",
                locale="en",
            ),
            expected_parts=1,
            include_image_part=False,
        )
    )


def test_chat_image_smoke() -> None:
    asyncio.run(
        _invoke_chat(
            ChatRequest(
                userId="user-1",
                conversationId="conversation-2",
                message="Please diagnose this crop image.",
                locale="en",
                imageUrl="https://example.com/crop.png",
            ),
            expected_parts=2,
            include_image_part=True,
        )
    )


def test_chat_rejects_empty_payload() -> None:
    try:
        asyncio.run(
            main.chat(
                ChatRequest(
                    userId="user-1",
                    conversationId="conversation-3",
                    message="",
                    locale="en",
                ),
                x_agent_service_token=main.config.shared_secret or None,
            )
        )
    except HTTPException as exc:
        assert exc.status_code == 400
        assert exc.detail == "Either message or imageUrl is required"
    else:
        raise AssertionError("Expected HTTPException for empty chat payload")
