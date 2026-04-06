from __future__ import annotations

from typing import Any

from google.adk.agents.callback_context import CallbackContext
from google.adk.models.llm_request import LlmRequest
from google.adk.models.llm_response import LlmResponse
from google.adk.tools import ToolContext
from google.adk.tools.base_tool import BaseTool
from google.genai import types


PURCHASE_INTENT_KEYWORDS = (
    "buy",
    "purchase",
    "supplier",
    "contact supplier",
    "lead",
    "quote",
    "order",
)


def _latest_user_text(llm_request: LlmRequest) -> str:
    for content in reversed(llm_request.contents or []):
        if content.role == "user" and content.parts:
            for part in content.parts:
                if part.text:
                    return part.text
    return ""


def block_empty_turns(
    callback_context: CallbackContext,
    llm_request: LlmRequest,
) -> LlmResponse | None:
    """Blocks empty user turns before they reach the model."""
    latest_text = _latest_user_text(llm_request).strip()
    if latest_text:
        return None

    return LlmResponse(
        content=types.Content(
            role="model",
            parts=[
                types.Part(
                    text="Please share your farming question or describe what you need help with."
                )
            ],
        )
    )


def validate_lead_tool_arguments(
    tool: BaseTool,
    args: dict[str, Any],
    tool_context: ToolContext,
) -> dict[str, Any] | None:
    """Prevents premature lead creation when the product context is too weak."""
    if tool.name != "create_sales_lead":
        return None

    product_name = str(args.get("product_name") or "").strip()
    chat_message_text = str(args.get("chat_message_text") or "").strip().lower()
    has_purchase_intent = any(keyword in chat_message_text for keyword in PURCHASE_INTENT_KEYWORDS)

    if len(product_name) < 3 or not has_purchase_intent:
        tool_context.state["temp:lead_created"] = False
        return {
            "status": "needs_clarification",
            "missing_fields": ["product_name"],
            "message": "Lead creation was blocked because the purchase intent or product detail is insufficient.",
        }

    return None
