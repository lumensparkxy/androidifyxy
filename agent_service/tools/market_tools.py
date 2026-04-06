from __future__ import annotations

from typing import Any

from ..app_config import config
from ..firebase_client import get_firestore_client

MANDI_COLLECTION = "mandi_prices"


def search_mandi_prices(state: str, district: str, commodity: str) -> dict[str, Any]:
    """Looks up recent mandi prices for a commodity in a specific district.

    Use this tool for market-price questions where the farmer has provided a state,
    district, and commodity.

    Args:
        state: The state name, for example `Maharashtra`.
        district: The district name, for example `Pune`.
        commodity: The commodity name, for example `Tomato`.

    Returns:
        dict: On success includes a `prices` array with market and price details.
        On failure includes `error_message`.
    """
    db = get_firestore_client()
    query = (
        db.collection(MANDI_COLLECTION)
        .where("state", "==", state)
        .where("district", "==", district)
        .where("commodity", "==", commodity)
        .limit(config.mandi_results_limit)
    )

    prices: list[dict[str, Any]] = []
    for snapshot in query.stream():
        payload: dict[str, Any] = snapshot.to_dict() or {}
        payload["id"] = snapshot.id
        prices.append(
            {
                "id": payload.get("id"),
                "market": payload.get("market"),
                "arrival_date": payload.get("arrival_date"),
                "min_price": payload.get("min_price"),
                "modal_price": payload.get("modal_price"),
                "max_price": payload.get("max_price"),
            }
        )

    return {
        "status": "success",
        "prices": prices,
    }
