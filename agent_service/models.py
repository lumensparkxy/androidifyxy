from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class ChatHistoryMessage(BaseModel):
    role: Literal["user", "model", "assistant"]
    text: str = ""
    imageUrl: str | None = None


class ChatRequest(BaseModel):
    userId: str
    conversationId: str
    message: str = ""
    locale: str = "en"
    imageUrl: str | None = None
    recentMessages: list[ChatHistoryMessage] = Field(default_factory=list)


class AgentMetadata(BaseModel):
    leadCreated: bool = False
    requestNumber: str | None = None
    citedDocumentIds: list[str] = Field(default_factory=list)
    traceId: str | None = None
    askedClarification: bool = False


class ChatResponse(BaseModel):
    text: str
    metadata: AgentMetadata = Field(default_factory=AgentMetadata)
