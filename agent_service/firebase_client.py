from __future__ import annotations

from functools import lru_cache

import firebase_admin
from firebase_admin import firestore


@lru_cache(maxsize=1)
def get_firestore_client() -> firestore.Client:
    if not firebase_admin._apps:
        firebase_admin.initialize_app()
    return firestore.client()
