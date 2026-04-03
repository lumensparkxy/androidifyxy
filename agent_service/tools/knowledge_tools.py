from __future__ import annotations

from typing import Any

from google.adk.tools import ToolContext

from ..app_config import config
from ..firebase_client import get_firestore_client

DOCUMENTS_COLLECTION = "knowledge_documents"


def _score_document(doc: dict[str, Any], query_terms: list[str]) -> int:
    haystacks = [
        str(doc.get("title", "")).lower(),
        str(doc.get("description", "")).lower(),
        str(doc.get("cropId", "")).lower(),
    ]
    return sum(term in haystack for term in query_terms for haystack in haystacks)


def search_knowledge_documents(
    query: str,
    crop_id: str | None = None,
    tool_context: ToolContext | None = None,
) -> dict[str, Any]:
    """Searches curated agricultural knowledge documents.

    Use this tool when the user asks for crop guidance, best practices, or wants
    grounded advice based on the curated knowledge base.

    Args:
        query: The farmer's question or search intent.
        crop_id: Optional crop identifier such as `tomato`, `wheat`, or `corn`.

    Returns:
        dict: On success includes `documents`, each with `id`, `title`, `description`,
        `cropId`, and `storagePath`. On failure includes `error_message`.
    """
    db = get_firestore_client()
    collection = db.collection(DOCUMENTS_COLLECTION)

    if crop_id:
        snapshots = collection.where("cropId", "==", crop_id).limit(25).stream()
    else:
        snapshots = collection.limit(50).stream()

    query_terms = [term for term in query.lower().split() if len(term) > 2]
    docs: list[dict[str, Any]] = []
    for snapshot in snapshots:
        payload = snapshot.to_dict() or {}
        payload["id"] = snapshot.id
        docs.append(payload)

    ranked = sorted(
        docs,
        key=lambda item: (
            -_score_document(item, query_terms),
            int(item.get("displayOrder", 9999)),
        ),
    )[: config.knowledge_results_limit]

    documents = [
        {
            "id": item.get("id"),
            "title": item.get("title"),
            "description": item.get("description"),
            "cropId": item.get("cropId"),
            "storagePath": item.get("storagePath"),
        }
        for item in ranked
    ]

    if tool_context is not None:
        tool_context.state["temp:cited_document_ids"] = [doc["id"] for doc in documents if doc.get("id")]

    return {
        "status": "success",
        "documents": documents,
    }
