# AGENTS.md

## Repo map
- `app/` is the primary product: a Kotlin/Compose Android app (`MainActivity`, `ui/navigation/AppNavigation.kt`, `ChatViewModel.kt`).
- `functions/` is the Firebase backend in Node 22. It owns Firestore-triggered lead routing, callable APIs, and hosting/index/rules deployment.
- `agent_service/` is a separate FastAPI + Google ADK service meant to stay behind `functions/index.js:agentChatProxy` and Cloud Run.
- `website/` is a static-export Next.js 15 app for marketing plus admin/supplier operational screens.
- `scripts/` contains an alternate local mandi sync path because `data.gov.in` may block GCP IPs; read `scripts/README.md` before assuming Cloud Functions is the only sync path.

## Big-picture flows
- Android chat currently has **two paths** in `app/src/main/java/com/maswadkar/developers/androidify/ChatViewModel.kt`: legacy Firebase AI chat and the new agentic path. `AppConfigManager.shouldUseAgenticChat(userId)` gates rollout via Remote Config (`agentic_chat_enabled`, rollout %, allowlist).
- Agentic chat flow is: Android uploads image to Storage -> Android calls callable `agentChatProxy` in `functions/index.js` -> proxy calls Cloud Run `POST /chat` in `agent_service/main.py` -> ADK sub-agents/tools read Firestore and return text + metadata.
- Preserve the response contract across `agent_service/models.py` and `app/.../data/AgenticChatRepository.kt` (`text`, `metadata.leadCreated`, `requestNumber`, `citedDocumentIds`, `traceId`, `askedClarification`).
- If the model recommends products, keep the fenced block contract exactly as documented in `app/src/main/java/com/maswadkar/developers/androidify/AppConstants.kt` and parsed by `util/ProductRecommendationParser.kt`: it must be a trailing ```krishi_products` JSON block.

## Sales lead pipeline
- Lead creation is deliberately duplicated across Android callable flow and ADK tool flow: see `functions/index.js:createSalesPipelineLead` and `agent_service/tools/lead_tools.py:create_sales_lead`. If you change fields, update both.
- `sales_pipeline` docs are deduped by deterministic doc ID (`buildSalesPipelineDocId` / `_build_doc_id`) and start with `status=initiated`, `routingStatus=initiated`, `recommendationStatus=pending`.
- Firestore trigger `recommendSupplierForLead` in `functions/index.js` enriches new leads with `suggestedSupplier`, `commissionPreview`, and routing status. Admin recovery paths are `retryLeadRecommendation` and `backfillPendingLeadRecommendations`.
- Website admin flow mixes direct Firestore edits and callable functions on purpose: `website/app/admin/leads/AdminLeadsClient.tsx` updates ops notes/status directly, but recommendation retry, bulk assignment, and workflow advancement go through callables.
- If you add/change lead fields, sync `functions/index.js`, `website/lib/types.ts`, admin/supplier clients, and any Kotlin lead models/repositories together.

## Auth, rules, and region assumptions
- Everything is pinned to Firebase region `asia-south1` (`FirebaseFunctions.getInstance("asia-south1")`, website `getFunctions(app, 'asia-south1')`, functions deployment region).
- Admin access is granted by privileged emails or `admin_users/{uid}` in `functions/firestore.rules`; supplier auto-approval is tied to the allowlisted phone number there too.
- Firestore rules are strict around `sales_pipeline`, `offers`, and `suppliers`; prefer server-side callable changes for workflow/state transitions instead of broadening client writes.

## Commands you will actually use
- Android debug/release from repo root: `./gradlew assembleDebug`, `./gradlew bundleRelease`, `./gradlew assembleRelease` (see `docs/BUILD_RELEASE_BUNDLE.md`). Signing comes from `local.properties`.
- Functions: `cd functions && npm install && npm test`; deploy with `firebase deploy --only functions,firestore:indexes`.
- Agent service local run: `cd agent_service && uvicorn agent_service.main:app --host 0.0.0.0 --port 8080`.
- Agent service tests are `pytest`-style under `agent_service/tests/`; `test_firestore_session_service_live.py` only runs when `GOOGLE_CLOUD_PROJECT` points to a real project.
- Website: `cd website && npm install && npm run dev`. `next.config.js` uses `output: 'export'`, and Firebase Hosting serves `website/out` per root `firebase.json`.
- Local mandi sync fallback: `cd scripts && python mandi_sync_service.py --once` or run the launchd workflow in `scripts/README.md`.

## Project-specific conventions
- Do not “simplify away” the localhost fallback in `AdminLeadsClient.tsx`; it intentionally avoids misleading undeployed-function/CORS failures by reading Firestore directly during local web development.
- `ChatRepository.kt` enables Firestore offline persistence and conversation IDs are often pre-generated to align image storage paths with conversation docs.
- `agent_service/firestore_session_service.py` persists ADK session state/events to Firestore and strips inline image bytes before storage; keep that behavior if you touch session persistence.
- Keep Cloud Run non-public; the intended ingress is the Firebase callable proxy with both IAM auth headers and optional shared secret (`AGENT_SERVICE_SHARED_SECRET`).

## Files/paths to treat carefully
- Generated/output: `app/build/`, `build/`, `website/out/`, `output/`, `tsconfig.tsbuildinfo`.
- Local secrets/config: `local.properties`, `app/google-services.json`, `scripts/serviceAccountKey.json`, keystore files.

