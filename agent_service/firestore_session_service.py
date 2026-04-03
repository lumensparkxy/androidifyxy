from __future__ import annotations

import asyncio
import json
import time
import uuid
from copy import deepcopy
from typing import Any

from google.adk.errors.already_exists_error import AlreadyExistsError
from google.adk.events import Event
from google.adk.sessions import Session
from google.adk.sessions import _session_util
from google.adk.sessions.base_session_service import (
    BaseSessionService,
    GetSessionConfig,
    ListSessionsResponse,
)
from google.adk.sessions.state import State

from .firebase_client import get_firestore_client

EVENTS_SUBCOLLECTION = "events"


class FirestoreSessionService(BaseSessionService):
    """Firestore-backed ADK session service for Cloud Run deployments.

    This service persists:
    - session-local state and events per conversation
    - `user:` state shared across sessions for a user
    - `app:` state shared across the whole agent app
    """

    def __init__(
        self,
        *,
        sessions_collection: str = "agent_runtime_sessions",
        user_state_collection: str = "agent_runtime_user_state",
        app_state_collection: str = "agent_runtime_app_state",
    ) -> None:
        self.db = get_firestore_client()
        self.sessions_collection = sessions_collection
        self.user_state_collection = user_state_collection
        self.app_state_collection = app_state_collection

    async def create_session(
        self,
        *,
        app_name: str,
        user_id: str,
        state: dict[str, Any] | None = None,
        session_id: str | None = None,
    ) -> Session:
        return await asyncio.to_thread(
            self._create_session_impl,
            app_name=app_name,
            user_id=user_id,
            state=state,
            session_id=session_id,
        )

    async def get_session(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        config: GetSessionConfig | None = None,
    ) -> Session | None:
        return await asyncio.to_thread(
            self._get_session_impl,
            app_name=app_name,
            user_id=user_id,
            session_id=session_id,
            config=config,
        )

    async def list_sessions(
        self,
        *,
        app_name: str,
        user_id: str | None = None,
    ) -> ListSessionsResponse:
        return await asyncio.to_thread(
            self._list_sessions_impl,
            app_name=app_name,
            user_id=user_id,
        )

    async def delete_session(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> None:
        await asyncio.to_thread(
            self._delete_session_impl,
            app_name=app_name,
            user_id=user_id,
            session_id=session_id,
        )

    async def append_event(self, session: Session, event: Event) -> Event:
        if event.partial:
            return event

        persisted_event = event if event.id else event.model_copy(update={"id": str(uuid.uuid4())})
        await super().append_event(session=session, event=persisted_event)
        session.last_update_time = persisted_event.timestamp
        await asyncio.to_thread(self._append_event_impl, session, persisted_event)
        return persisted_event

    def _create_session_impl(
        self,
        *,
        app_name: str,
        user_id: str,
        state: dict[str, Any] | None = None,
        session_id: str | None = None,
    ) -> Session:
        session_id = session_id.strip() if session_id and session_id.strip() else str(uuid.uuid4())
        session_ref = self._session_ref(app_name, user_id, session_id)
        if session_ref.get().exists:
            raise AlreadyExistsError(f"Session with id {session_id} already exists.")

        state_deltas = _session_util.extract_state_delta(state or {})
        app_state = self._read_state_doc(self._app_state_ref(app_name))
        user_state = self._read_state_doc(self._user_state_ref(app_name, user_id))
        app_state.update(state_deltas["app"])
        user_state.update(state_deltas["user"])

        now = time.time()
        session = Session(
            app_name=app_name,
            user_id=user_id,
            id=session_id,
            state=state_deltas["session"] or {},
            last_update_time=now,
        )

        batch = self.db.batch()
        batch.set(
            session_ref,
            {
                "id": session_id,
                "app_name": app_name,
                "user_id": user_id,
                "state": session.state,
                "last_update_time": now,
                "created_at": now,
            },
        )
        batch.set(
            self._app_state_ref(app_name),
            {
                "app_name": app_name,
                "state": app_state,
                "last_update_time": now,
            },
            merge=True,
        )
        batch.set(
            self._user_state_ref(app_name, user_id),
            {
                "app_name": app_name,
                "user_id": user_id,
                "state": user_state,
                "last_update_time": now,
            },
            merge=True,
        )
        batch.commit()

        return self._merge_state(app_name=app_name, user_id=user_id, session=deepcopy(session))

    def _get_session_impl(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        config: GetSessionConfig | None = None,
    ) -> Session | None:
        session_snapshot = self._session_ref(app_name, user_id, session_id).get()
        if not session_snapshot.exists:
            return None

        payload = session_snapshot.to_dict() or {}
        events = self._read_events(app_name=app_name, user_id=user_id, session_id=session_id, config=config)
        session = Session(
            app_name=payload.get("app_name", app_name),
            user_id=payload.get("user_id", user_id),
            id=payload.get("id", session_id),
            state=payload.get("state", {}) or {},
            events=events,
            last_update_time=payload.get("last_update_time", time.time()),
        )
        return self._merge_state(app_name=app_name, user_id=user_id, session=session)

    def _list_sessions_impl(
        self,
        *,
        app_name: str,
        user_id: str | None = None,
    ) -> ListSessionsResponse:
        query = self.db.collection(self.sessions_collection).where("app_name", "==", app_name)
        if user_id is not None:
            query = query.where("user_id", "==", user_id)

        sessions = []
        for snapshot in query.stream():
            payload = snapshot.to_dict() or {}
            current_user_id = payload.get("user_id")
            session = Session(
                app_name=payload.get("app_name", app_name),
                user_id=current_user_id,
                id=payload.get("id", snapshot.id),
                state=payload.get("state", {}) or {},
                events=[],
                last_update_time=payload.get("last_update_time", time.time()),
            )
            sessions.append(self._merge_state(app_name=app_name, user_id=current_user_id, session=session))

        return ListSessionsResponse(sessions=sessions)

    def _delete_session_impl(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
    ) -> None:
        session_ref = self._session_ref(app_name, user_id, session_id)
        if not session_ref.get().exists:
            return

        self._delete_event_docs(session_ref)
        session_ref.delete()

    def _append_event_impl(self, session: Session, event: Event) -> None:
        app_name = session.app_name
        user_id = session.user_id
        session_id = session.id
        session_ref = self._session_ref(app_name, user_id, session_id)
        if not session_ref.get().exists:
            return

        app_state_ref = self._app_state_ref(app_name)
        user_state_ref = self._user_state_ref(app_name, user_id)
        app_state = self._read_state_doc(app_state_ref)
        user_state = self._read_state_doc(user_state_ref)

        if event.actions and event.actions.state_delta:
            state_deltas = _session_util.extract_state_delta(event.actions.state_delta)
            app_state.update(state_deltas["app"])
            user_state.update(state_deltas["user"])
        session_state = _session_util.extract_state_delta(session.state)["session"]

        batch = self.db.batch()
        batch.set(
            session_ref,
            {
                "state": session_state,
                "last_update_time": event.timestamp,
            },
            merge=True,
        )
        batch.set(
            session_ref.collection(EVENTS_SUBCOLLECTION).document(event.id),
            {
                "timestamp": event.timestamp,
                "event_json": json.dumps(
                    self._serialize_event_for_storage(event),
                    ensure_ascii=False,
                    separators=(",", ":"),
                ),
            },
        )
        batch.set(
            app_state_ref,
            {
                "app_name": app_name,
                "state": app_state,
                "last_update_time": event.timestamp,
            },
            merge=True,
        )
        batch.set(
            user_state_ref,
            {
                "app_name": app_name,
                "user_id": user_id,
                "state": user_state,
                "last_update_time": event.timestamp,
            },
            merge=True,
        )
        batch.commit()

    def _read_events(
        self,
        *,
        app_name: str,
        user_id: str,
        session_id: str,
        config: GetSessionConfig | None,
    ) -> list[Event]:
        events_ref = self._session_ref(app_name, user_id, session_id).collection(EVENTS_SUBCOLLECTION)

        if config and config.num_recent_events:
            query = events_ref.order_by("timestamp", direction="DESCENDING").limit(config.num_recent_events)
            docs = list(query.stream())
            docs.reverse()
        else:
            query = events_ref.order_by("timestamp")
            if config and config.after_timestamp is not None:
                query = query.where("timestamp", ">", config.after_timestamp)
            docs = list(query.stream())

        return [
            Event.model_validate(json.loads((doc.to_dict() or {}).get("event_json", "{}")))
            for doc in docs
            if (doc.to_dict() or {}).get("event_json")
        ]

    def _serialize_event_for_storage(self, event: Event) -> dict[str, Any]:
        payload = event.model_dump(mode="json", by_alias=True, exclude_none=True)
        sanitized = self._sanitize_json_value(payload)

        content = sanitized.get("content")
        if isinstance(content, dict):
            parts = content.get("parts")
            if isinstance(parts, list):
                for part in parts:
                    if not isinstance(part, dict):
                        continue

                    inline_data = part.get("inlineData") or part.get("inline_data")
                    if inline_data:
                        mime_type = "image/*"
                        if isinstance(inline_data, dict):
                            mime_type = (
                                inline_data.get("mimeType")
                                or inline_data.get("mime_type")
                                or mime_type
                            )
                        if not part.get("text"):
                            part["text"] = f"[inline image omitted from persisted session: {mime_type}]"
                        part.pop("inlineData", None)
                        part.pop("inline_data", None)

        return sanitized

    def _sanitize_json_value(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {str(key): self._sanitize_json_value(val) for key, val in value.items()}
        if isinstance(value, list):
            return [self._sanitize_json_value(item) for item in value]
        if isinstance(value, bytes):
            return "[binary omitted]"
        return value

    def _merge_state(self, *, app_name: str, user_id: str, session: Session) -> Session:
        merged = deepcopy(session)
        app_state = self._read_state_doc(self._app_state_ref(app_name))
        for key, value in app_state.items():
            merged.state[f"{State.APP_PREFIX}{key}"] = value

        user_state = self._read_state_doc(self._user_state_ref(app_name, user_id))
        for key, value in user_state.items():
            merged.state[f"{State.USER_PREFIX}{key}"] = value

        return merged

    def _delete_event_docs(self, session_ref) -> None:
        while True:
            docs = list(session_ref.collection(EVENTS_SUBCOLLECTION).limit(500).stream())
            if not docs:
                return
            batch = self.db.batch()
            for doc in docs:
                batch.delete(doc.reference)
            batch.commit()

    def _read_state_doc(self, doc_ref) -> dict[str, Any]:
        snapshot = doc_ref.get()
        if not snapshot.exists:
            return {}
        payload = snapshot.to_dict() or {}
        return dict(payload.get("state", {}) or {})

    def _session_doc_id(self, app_name: str, user_id: str, session_id: str) -> str:
        return f"{app_name}__{user_id}__{session_id}"

    def _session_ref(self, app_name: str, user_id: str, session_id: str):
        return self.db.collection(self.sessions_collection).document(
            self._session_doc_id(app_name, user_id, session_id)
        )

    def _user_state_ref(self, app_name: str, user_id: str):
        return self.db.collection(self.user_state_collection).document(f"{app_name}__{user_id}")

    def _app_state_ref(self, app_name: str):
        return self.db.collection(self.app_state_collection).document(app_name)
