from __future__ import annotations

from typing import Any

from google.adk.tools import ToolContext

from ..firebase_client import get_firestore_client

USERS_COLLECTION = "users"
SETTINGS_COLLECTION = "settings"
FARMER_PROFILE_DOC = "farmer_profile"
REQUIRED_PROFILE_FIELDS = ("name", "village", "tehsil", "district", "totalFarmAcres", "mobileNumber")


def _normalize_mobile_number(value: Any) -> str | None:
    digits = "".join(ch for ch in str(value or "") if ch.isdigit())
    if not digits:
        return None

    normalized = digits[-10:]
    return normalized if len(normalized) == 10 else None


def _normalize_email(value: Any) -> str | None:
    email = str(value or "").strip()
    return email or None


def _pick_positive_number(*values: Any) -> float | int | None:
    for value in values:
        try:
            number = float(value)
        except (TypeError, ValueError):
            continue

        if number <= 0:
            continue
        return int(number) if number.is_integer() else number

    return None


def _normalize_profile(profile: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(profile)
    for key in ("name", "village", "tehsil", "district"):
        value = normalized.get(key)
        if isinstance(value, str):
            normalized[key] = value.strip()

    mobile_number = _normalize_mobile_number(
        normalized.get("mobileNumber") or normalized.get("phoneNumber")
    )
    email = _normalize_email(normalized.get("emailId") or normalized.get("email"))

    normalized["mobileNumber"] = mobile_number
    normalized["emailId"] = email
    normalized["email"] = email
    return normalized


def build_lead_farmer_profile_snapshot(profile: dict[str, Any]) -> dict[str, Any]:
    normalized = _normalize_profile(profile)
    snapshot: dict[str, Any] = {}

    for key in ("name", "village", "tehsil", "district"):
        value = normalized.get(key)
        if isinstance(value, str) and value.strip():
            snapshot[key] = value.strip()

    total_farm_acres = _pick_positive_number(normalized.get("totalFarmAcres"))
    if total_farm_acres is not None:
        snapshot["totalFarmAcres"] = total_farm_acres

    snapshot["mobileNumber"] = normalized.get("mobileNumber")
    snapshot["emailId"] = normalized.get("emailId")
    snapshot["email"] = normalized.get("email")

    return snapshot


def _missing_required_fields(profile: dict[str, Any]) -> list[str]:
    normalized = _normalize_profile(profile)
    missing: list[str] = []
    for field_name in REQUIRED_PROFILE_FIELDS:
        value = normalized.get(field_name)
        if field_name == "totalFarmAcres":
            try:
                if float(value) <= 0:
                    missing.append(field_name)
            except (TypeError, ValueError):
                missing.append(field_name)
            continue
        if field_name == "mobileNumber":
            if not _normalize_mobile_number(value):
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
