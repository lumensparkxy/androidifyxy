from __future__ import annotations

import hashlib
import secrets
from datetime import datetime, timezone
from typing import Any

from firebase_admin import firestore
from google.adk.tools import ToolContext

from ..firebase_client import get_firestore_client
from .profile_tools import REQUIRED_PROFILE_FIELDS, _missing_required_fields, _normalize_profile

SALES_PIPELINE_COLLECTION = "sales_pipeline"
USERS_COLLECTION = "users"
SETTINGS_COLLECTION = "settings"
FARMER_PROFILE_DOC = "farmer_profile"
SALES_PIPELINE_STATUS_INITIATED = "initiated"


def _normalize_product_name(product_name: str) -> str:
    return " ".join((product_name or "").strip().lower().split())


def _build_doc_id(user_id: str, conversation_id: str, product_name: str) -> str:
    normalized = _normalize_product_name(product_name)
    digest = hashlib.sha256(f"{user_id}|{conversation_id}|{normalized}".encode("utf-8")).hexdigest()
    return digest[:32]


def _generate_request_number(now: datetime | None = None) -> str:
    now = now or datetime.now(timezone.utc)
    suffix = secrets.token_hex(3).upper()
    return f"KR-{now:%Y%m%d}-{suffix}"


def _normalize_location_part(value: str | None) -> str:
    return " ".join((value or "").strip().lower().split())


def _infer_lead_category(product_name: str, chat_message_text: str = "") -> str:
    haystack = f"{product_name} {chat_message_text}".strip().lower()
    if any(token in haystack for token in ("fertili", "urea", "dap", "npk", "potash", "micronutrient", "manure")):
        return "fertilizer"
    if any(token in haystack for token in ("pesticide", "fungicide", "herbicide", "insecticide", "spray", "weedicide")):
        return "pesticide"
    if any(token in haystack for token in ("seed", "seeds", "hybrid", "variety", "nursery", "sapling")):
        return "seed"
    return "other"


def _build_initial_routing_fields(profile: dict[str, Any], product_name: str, chat_message_text: str) -> dict[str, Any]:
    district = str(profile.get("district") or "").strip()
    tehsil = str(profile.get("tehsil") or "").strip()
    village = str(profile.get("village") or "").strip()
    lead_category = _infer_lead_category(product_name, chat_message_text)
    return {
        "leadCategory": lead_category,
        "leadLocation": {
            "district": district,
            "districtKey": _normalize_location_part(district),
            "tehsil": tehsil,
            "tehsilKey": _normalize_location_part(tehsil),
            "village": village,
            "villageKey": _normalize_location_part(village),
        },
        "routingStatus": "initiated",
        "reviewStatus": "pending_recommendation",
        "recommendationStatus": "pending",
        "supplierVisibility": "hidden",
        "suggestedSupplier": None,
        "selectedSupplier": None,
        "assignedSupplier": None,
        "commissionPreview": {
            "category": lead_category,
            "amount": None,
            "currency": "INR",
            "ruleId": None,
        },
        "suggestionGeneratedAt": None,
        "assignmentPublishedAt": None,
        "supplierResponseDeadlineAt": None,
        "supplierRespondedAt": None,
        "supplierRejectedReason": None,
        "adminFallbackReason": None,
        "lastRoutingUpdatedAt": None,
    }


def create_sales_lead(
    product_name: str,
    chat_message_text: str,
    quantity: str | None = None,
    unit: str | None = None,
    source: str = "agentic_chat",
    tool_context: ToolContext | None = None,
) -> dict[str, Any]:
    """Creates or reuses a sales lead for the current user and conversation.

    Use this tool only when the user clearly wants supplier follow-up or wants to
    buy an agricultural input. Do not use it for general advice or exploratory
    browsing.

    Args:
        product_name: The specific product or input requested by the user.
        chat_message_text: A concise summary of the user's purchase intent.
        quantity: Optional quantity string such as `2` or `500`.
        unit: Optional unit such as `kg`, `L`, or `ml`.
        source: Source label for analytics/debugging.

    Returns:
        dict: On success includes `request_number`, `created`, and `status`.
        If more farmer profile data is required, returns `status='needs_clarification'`
        and a `missing_fields` array.
        On failure includes `error_message`.
    """
    if tool_context is None:
        return {"status": "error", "error_message": "Tool context is required."}

    user_id = tool_context.state.get("user:user_id")
    conversation_id = tool_context.state.get("conversation_id")
    if not user_id or not conversation_id:
        return {
            "status": "error",
            "error_message": "Conversation context is missing.",
        }

    if not product_name.strip() or not chat_message_text.strip():
        return {
            "status": "error",
            "error_message": "Product name and purchase context are required.",
        }

    db = get_firestore_client()
    farmer_profile_ref = (
        db.collection(USERS_COLLECTION)
        .document(user_id)
        .collection(SETTINGS_COLLECTION)
        .document(FARMER_PROFILE_DOC)
    )
    farmer_profile_snapshot = farmer_profile_ref.get()
    profile = _normalize_profile(farmer_profile_snapshot.to_dict() or {})
    missing_fields = _missing_required_fields(profile)
    if missing_fields:
        tool_context.state["temp:last_profile_missing_fields"] = missing_fields
        return {
            "status": "needs_clarification",
            "missing_fields": missing_fields,
            "message": "Farmer profile is incomplete for lead creation.",
        }

    lead_ref = db.collection(SALES_PIPELINE_COLLECTION).document(
        _build_doc_id(user_id, conversation_id, product_name)
    )
    transaction = db.transaction()

    @firestore.transactional
    def _create_or_reuse(txn: firestore.Transaction) -> dict[str, Any]:
        existing_snapshot = lead_ref.get(transaction=txn)
        if existing_snapshot.exists:
            existing = existing_snapshot.to_dict() or {}
            return {
                "status": existing.get("status", SALES_PIPELINE_STATUS_INITIATED),
                "request_number": existing.get("requestNumber"),
                "created": False,
            }

        request_number = _generate_request_number()
        now = firestore.SERVER_TIMESTAMP
        txn.set(
            lead_ref,
            {
                "userId": user_id,
                "conversationId": conversation_id,
                "requestNumber": request_number,
                "status": SALES_PIPELINE_STATUS_INITIATED,
                "source": source,
                "dedupeKey": lead_ref.id,
                "productName": product_name.strip(),
                "normalizedProductName": _normalize_product_name(product_name),
                "quantity": (quantity or "").strip() or None,
                "unit": (unit or "").strip() or None,
                "chatMessageText": chat_message_text.strip(),
                "farmerProfileSnapshot": profile,
                **_build_initial_routing_fields(profile, product_name, chat_message_text),
                "createdAt": now,
                "updatedAt": now,
            },
        )
        return {
            "status": SALES_PIPELINE_STATUS_INITIATED,
            "request_number": request_number,
            "created": True,
        }

    result = _create_or_reuse(transaction)
    tool_context.state["temp:lead_created"] = result.get("created", False)
    tool_context.state["temp:lead_request_number"] = result.get("request_number")

    return result
