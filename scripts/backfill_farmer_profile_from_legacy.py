#!/usr/bin/env python3
"""
Legacy farmer profile backfill utility.

Scans users/{uid}/settings/mandi_preferences and merges any missing location /
commodity fields into users/{uid}/settings/farmer_profile.

Dry-run by default. Pass --apply to persist changes.

Examples:
    python backfill_farmer_profile_from_legacy.py
    python backfill_farmer_profile_from_legacy.py --apply
    python backfill_farmer_profile_from_legacy.py --user-id someUid --apply
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from dataclasses import dataclass
from typing import Any

import firebase_admin
from firebase_admin import credentials, firestore

USERS_COLLECTION = "users"
SETTINGS_COLLECTION = "settings"
LEGACY_DOC = "mandi_preferences"
PROFILE_DOC = "farmer_profile"
LEGACY_FIELDS = ("state", "district", "market", "lastCommodity")
BATCH_SIZE = 400
LOG_PROGRESS_EVERY = 100

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)


@dataclass
class BackfillStats:
    users_scanned: int = 0
    users_with_legacy: int = 0
    users_with_profile: int = 0
    invalid_legacy_docs: int = 0
    already_migrated: int = 0
    patches_needed: int = 0
    patches_applied: int = 0
    mismatched_fields: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Backfill users/{uid}/settings/farmer_profile from legacy "
            "users/{uid}/settings/mandi_preferences."
        )
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Persist the computed patches. Without this flag the script runs in dry-run mode.",
    )
    parser.add_argument(
        "--user-id",
        help="Only inspect a single user ID instead of scanning the whole users collection.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Maximum number of users to scan (0 means no limit).",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Enable verbose logging.",
    )
    return parser.parse_args()


def configure_logging(verbose: bool) -> None:
    if verbose:
        logger.setLevel(logging.DEBUG)


def pick_credential_path() -> str:
    cred_path = (
        os.getenv("FIREBASE_APPLICATION_CREDENTIALS")
        or os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    )
    if cred_path and os.path.exists(cred_path):
        return cred_path

    default_path = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")
    if os.path.exists(default_path):
        return default_path

    raise ValueError(
        "No Firebase service account key found. Set FIREBASE_APPLICATION_CREDENTIALS or "
        "GOOGLE_APPLICATION_CREDENTIALS, or place serviceAccountKey.json in scripts/."
    )


def initialize_firebase() -> firestore.Client:
    cred_path = pick_credential_path()
    logger.info("Using service account key from: %s", cred_path)

    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(cred_path))
        logger.info("Firebase initialized successfully")

    return firestore.client()


def profile_document(db: firestore.Client, user_id: str):
    return (
        db.collection(USERS_COLLECTION)
        .document(user_id)
        .collection(SETTINGS_COLLECTION)
        .document(PROFILE_DOC)
    )


def legacy_document(db: firestore.Client, user_id: str):
    return (
        db.collection(USERS_COLLECTION)
        .document(user_id)
        .collection(SETTINGS_COLLECTION)
        .document(LEGACY_DOC)
    )


def normalize_optional_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def is_valid_legacy_preferences(legacy_data: dict[str, Any]) -> bool:
    return bool(
        normalize_optional_string(legacy_data.get("state"))
        and normalize_optional_string(legacy_data.get("district"))
    )


def build_profile_patch(
    legacy_data: dict[str, Any],
    profile_data: dict[str, Any],
) -> tuple[dict[str, Any], list[str]]:
    patch: dict[str, Any] = {}
    mismatched_fields: list[str] = []

    for field_name in LEGACY_FIELDS:
        legacy_value = normalize_optional_string(legacy_data.get(field_name))
        if not legacy_value:
            continue

        current_value = normalize_optional_string(profile_data.get(field_name))
        if not current_value:
            patch[field_name] = legacy_value
        elif current_value != legacy_value:
            mismatched_fields.append(field_name)

    return patch, mismatched_fields


def iter_legacy_snapshots(
    db: firestore.Client,
    user_id: str | None,
    limit: int,
) -> list[firestore.DocumentSnapshot]:
    if user_id:
        snapshot = legacy_document(db, user_id).get()
        return [snapshot] if snapshot.exists else []

    legacy_snapshots: list[firestore.DocumentSnapshot] = []
    for snapshot in db.collection_group(SETTINGS_COLLECTION).stream():
        if snapshot.id != LEGACY_DOC:
            continue
        legacy_snapshots.append(snapshot)
        if limit > 0 and len(legacy_snapshots) >= limit:
            break
    return legacy_snapshots


def run_backfill(db: firestore.Client, args: argparse.Namespace) -> BackfillStats:
    stats = BackfillStats()
    legacy_snapshots = iter_legacy_snapshots(db, args.user_id, args.limit)
    logger.info(
        "Starting %s scan for %d legacy user(s)",
        "apply" if args.apply else "dry-run",
        len(legacy_snapshots),
    )

    batch = db.batch()
    pending_writes = 0

    for index, legacy_snapshot in enumerate(legacy_snapshots, start=1):
        user_doc = legacy_snapshot.reference.parent.parent
        if user_doc is None:
            logger.warning("Skipping unexpected legacy document path: %s", legacy_snapshot.reference.path)
            continue

        user_id = user_doc.id
        stats.users_scanned += 1

        stats.users_with_legacy += 1
        legacy_data = legacy_snapshot.to_dict() or {}
        if not is_valid_legacy_preferences(legacy_data):
            stats.invalid_legacy_docs += 1
            logger.warning("Skipping invalid legacy mandi_preferences for user %s", user_id)
            continue

        profile_snapshot = profile_document(db, user_id).get()
        profile_data = profile_snapshot.to_dict() or {}
        if profile_snapshot.exists:
            stats.users_with_profile += 1

        patch, mismatched_fields = build_profile_patch(legacy_data, profile_data)
        if mismatched_fields:
            stats.mismatched_fields += len(mismatched_fields)
            logger.warning(
                "User %s has profile fields differing from legacy mandi_preferences: %s",
                user_id,
                ", ".join(mismatched_fields),
            )

        if not patch:
            stats.already_migrated += 1
        else:
            stats.patches_needed += 1
            logger.info("%s patch needed for user %s: %s", "Apply" if args.apply else "Dry-run", user_id, patch)
            if args.apply:
                patch["updatedAt"] = firestore.SERVER_TIMESTAMP
                batch.set(profile_document(db, user_id), patch, merge=True)
                pending_writes += 1
                stats.patches_applied += 1
                if pending_writes >= BATCH_SIZE:
                    batch.commit()
                    batch = db.batch()
                    pending_writes = 0

        if index % LOG_PROGRESS_EVERY == 0:
            logger.info(
                "Scanned %d user(s) so far — legacy docs: %d, patches needed: %d",
                stats.users_scanned,
                stats.users_with_legacy,
                stats.patches_needed,
            )

    if args.apply and pending_writes > 0:
        batch.commit()

    return stats


def log_summary(stats: BackfillStats, apply_mode: bool) -> None:
    logger.info("%s complete", "Backfill" if apply_mode else "Dry-run")
    logger.info("Users scanned: %d", stats.users_scanned)
    logger.info("Users with legacy docs: %d", stats.users_with_legacy)
    logger.info("Users with existing profiles: %d", stats.users_with_profile)
    logger.info("Invalid legacy docs: %d", stats.invalid_legacy_docs)
    logger.info("Already migrated users: %d", stats.already_migrated)
    logger.info("Users needing patch: %d", stats.patches_needed)
    logger.info("Patches applied: %d", stats.patches_applied)
    logger.info("Mismatched fields observed: %d", stats.mismatched_fields)


def main() -> int:
    args = parse_args()
    configure_logging(args.verbose)

    try:
        db = initialize_firebase()
        stats = run_backfill(db, args)
        log_summary(stats, args.apply)
        if not args.apply:
            logger.info("Dry-run only. Re-run with --apply to persist changes.")
        return 0
    except Exception as exc:  # pragma: no cover - defensive CLI wrapper
        logger.error("Backfill failed: %s", exc)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
