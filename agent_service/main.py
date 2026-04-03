from __future__ import annotations

from typing import Iterable

from fastapi import FastAPI, Header, HTTPException
from google.adk.runners import Runner
from google.genai import types

from .agent import root_agent
from .app_config import config
from .image_utils import ImageDownloadError, build_image_part
from .models import AgentMetadata, ChatRequest, ChatResponse
from .session_service_factory import build_session_service

app = FastAPI(title="Krishi Agent Service", version="0.1.0")
session_service = build_session_service()
runner = Runner(
    agent=root_agent,
    app_name=config.app_name,
    session_service=session_service,
)


def _format_recent_messages(messages: Iterable[dict]) -> str:
    lines: list[str] = []
    for item in list(messages)[-config.max_recent_messages:]:
        role = item.get("role", "user")
        text = (item.get("text") or "").strip()
        image_url = item.get("imageUrl")
        if not text and not image_url:
            continue
        line = f"{role.upper()}: {text}".strip()
        if image_url:
            line = f"{line}\nIMAGE_URL: {image_url}".strip()
        lines.append(line)
    return "\n".join(lines)


def _build_user_message(request: ChatRequest) -> str:
    parts: list[str] = []
    recent_context = _format_recent_messages(message.model_dump() for message in request.recentMessages)
    if recent_context:
        parts.append("Recent conversation context:\n" + recent_context)
    if request.imageUrl:
        parts.append("The user attached a crop image for diagnosis. Use the image directly in your analysis.")
    if request.message.strip():
        parts.append("User message: " + request.message.strip())
    return "\n\n".join(parts).strip()


def _build_request_parts(request: ChatRequest) -> list[types.Part]:
    parts: list[types.Part] = [types.Part(text=_build_user_message(request))]
    if not request.imageUrl:
        return parts

    try:
        parts.append(build_image_part(request.imageUrl))
    except ImageDownloadError as error:
        parts[0] = types.Part(
            text=(
                _build_user_message(request)
                + f"\n\nNote: The attached image could not be downloaded reliably ({error}). "
                "Ask for a retry or continue with the text-only context if still helpful."
            )
        )
    return parts


def _looks_like_clarification(text: str) -> bool:
    lowered = text.lower()
    return any(
        phrase in lowered
        for phrase in (
            "could you share",
            "please share",
            "which crop",
            "what crop",
            "what quantity",
            "which product",
            "can you tell me",
        )
    ) or text.strip().endswith("?")


async def _ensure_session(request: ChatRequest) -> None:
    existing = await session_service.get_session(
        app_name=config.app_name,
        user_id=request.userId,
        session_id=request.conversationId,
    )
    if existing is not None:
        return

    await session_service.create_session(
        app_name=config.app_name,
        user_id=request.userId,
        session_id=request.conversationId,
        state={
            "user:user_id": request.userId,
            "user:locale": request.locale,
            "conversation_id": request.conversationId,
        },
    )


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    x_agent_service_token: str | None = Header(default=None),
) -> ChatResponse:
    if config.shared_secret and x_agent_service_token != config.shared_secret:
        raise HTTPException(status_code=401, detail="Unauthorized")

    if not request.message.strip() and not request.imageUrl:
        raise HTTPException(status_code=400, detail="Either message or imageUrl is required")

    await _ensure_session(request)

    new_message = types.Content(
        role="user",
        parts=_build_request_parts(request),
    )

    final_text = "I could not generate a response."
    trace_id: str | None = None
    async for event in runner.run_async(
        user_id=request.userId,
        session_id=request.conversationId,
        new_message=new_message,
    ):
        trace_id = getattr(event, "invocation_id", None) or trace_id
        if event.is_final_response() and event.content and event.content.parts:
            final_text = "".join(part.text or "" for part in event.content.parts).strip() or final_text

    session = await session_service.get_session(
        app_name=config.app_name,
        user_id=request.userId,
        session_id=request.conversationId,
    )
    state = session.state if session else {}
    metadata = AgentMetadata(
        leadCreated=bool(state.get("temp:lead_created", False)),
        requestNumber=state.get("temp:lead_request_number"),
        citedDocumentIds=list(state.get("temp:cited_document_ids", []) or []),
        traceId=trace_id,
        askedClarification=_looks_like_clarification(final_text),
    )
    return ChatResponse(text=final_text, metadata=metadata)
