from __future__ import annotations

from typing import Any

from google.adk.tools import ToolContext

from ..firebase_client import get_firestore_client

USERS_COLLECTION = "users"
SETTINGS_COLLECTION = "settings"
FARMER_PROFILE_DOC = "farmer_profile"
REQUIRED_PROFILE_FIELDS = ("name", "village", "tehsil", "district", "totalFarmAcres")


def _normalize_profile(profile: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(profile)
    for key in ("name", "village", "tehsil", "district"):
        value = normalized.get(key)
        if isinstance(value, str):
            normalized[key] = value.strip()
    return normalized


def _missing_required_fields(profile: dict[str, Any]) -> list[str]:
    missing: list[str] = []
    for field_name in REQUIRED_PROFILE_FIELDS:
        value = profile.get(field_name)
        if field_name == "totalFarmAcres":
            try:
                if float(value) <= 0:
                    missing.append(field_name)
            except (TypeError, ValueError):
                missing.append(field_name)
            continue
        if not isinstance(value, str) or not value.strip():
            missing.append(field_name)
    return missing


def get_farmer_profile(tool_context: ToolContext) -> dict[str, Any]:
    """Loads the current farmer profile from Firestore.

    Use this tool when you need profile context for agronomy advice, lead creation,
    or to understand whether more user information is required.

    Returns:
        dict: A dictionary with a status field.
        On success, includes `profile` and `missing_fields`.
        On error, includes `error_message`.
    """
    user_id = tool_context.state.get("user:user_id")
    if not user_id:
        return {
            "status": "error",
            "error_message": "Authenticated user context is missing.",
        }

    db = get_firestore_client()
    snapshot = (
        db.collection(USERS_COLLECTION)
        .document(user_id)
        .collection(SETTINGS_COLLECTION)
        .document(FARMER_PROFILE_DOC)
        .get()
    )

    if not snapshot.exists:
        return {
            "status": "success",
            "profile": {},
            "missing_fields": list(REQUIRED_PROFILE_FIELDS),
        }

    profile = _normalize_profile(snapshot.to_dict() or {})
    missing_fields = _missing_required_fields(profile)
    tool_context.state["temp:last_profile_missing_fields"] = missing_fields

    return {
        "status": "success",
        "profile": profile,
        "missing_fields": missing_fields,
    }
