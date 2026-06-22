# Agent Instructions

These instructions apply to the whole repository unless a nested `AGENTS.md`
overrides them.

## Operating Contract
- Prefer issue-scoped work. For planned requirements, bugs, enhancements,
  refactors, docs, and operational changes, tie the work to a GitHub issue or
  issue-ready proposal before editing.
- Keep implementation scope aligned with the linked issue or the user's current
  explicit request. Do not bundle unrelated cleanup, dependency churn, or
  opportunistic refactors.
- Preserve unrelated local work. This checkout often has dirty files from other
  tasks; inspect before staging and stage only the files that belong to the
  current change.
- Be evidence-first for failures. Reproduce or inspect the real failing path
  before patching auth, Firestore rules, Cloud Functions, GitHub Pages,
  Cloud Run, Android build, parser, or admin UI behavior.
- Record validation. When you change code, report the commands run and the
  result. If a relevant gate cannot be run, state why and what risk remains.

## Repo Map
- `app/` is the primary product: a Kotlin/Compose Android app (`MainActivity`,
  `ui/navigation/AppNavigation.kt`, `ChatViewModel.kt`).
- `functions/` is the Firebase backend in Node 22. It owns Firestore-triggered
  lead routing, callable APIs, indexes, rules, and scheduled jobs.
- `functions/*.js` includes shared backend helpers for sales/ops logic
  (`salesPipeline.js`, `leadRecommendation.js`, `leadProfile.js`,
  `adminLeadView.js`, `adminAffiliateMessaging.js`, `affiliateRegistry*.js`,
  `leadWorkflow.js`). `index.js` wires deployed exports, but lead/admin changes
  usually span helpers and tests too.
- `agent_service/` is a FastAPI + Google ADK service meant to stay behind
  `functions/index.js:agentChatProxy` and Cloud Run.
- `website/` is a static-export Next.js 15 app for marketing plus admin/supplier
  operational screens.
- `scripts/` contains local operational helpers, including the alternate local
  mandi sync path because `data.gov.in` may block GCP IPs. Read
  `scripts/README.md` before assuming Cloud Functions is the only sync path.

## Big-Picture Flows
- Android chat has two paths in
  `app/src/main/java/com/maswadkar/developers/androidify/ChatViewModel.kt`:
  legacy Firebase AI chat and the agentic path. `AppConfigManager.shouldUseAgenticChat(userId)`
  gates rollout via Remote Config (`agentic_chat_enabled`, rollout %, allowlist).
- Agentic chat flow is: Android uploads image to Storage -> Android calls
  callable `agentChatProxy` in `functions/index.js` -> proxy calls Cloud Run
  `POST /chat` in `agent_service/main.py` -> ADK sub-agents/tools read Firestore
  and return text plus metadata.
- Preserve the response contract across `agent_service/models.py` and
  `app/.../data/AgenticChatRepository.kt`: `text`, `metadata.leadCreated`,
  `requestNumber`, `citedDocumentIds`, `traceId`, `askedClarification`.
- If the model recommends products, keep the fenced `krishi_products` JSON block
  contract documented in
  `app/src/main/java/com/maswadkar/developers/androidify/AppConstants.kt` and
  parsed by `util/ProductRecommendationParser.kt`.

## Sales Lead Pipeline
- Lead creation is deliberately duplicated across Android callable flow and ADK
  tool flow: `functions/index.js:createSalesPipelineLead` and
  `agent_service/tools/lead_tools.py:create_sales_lead`. If fields change,
  update both.
- Android `app/src/main/java/com/maswadkar/developers/androidify/data/SalesLeadRepository.kt`
  has a direct Firestore fallback when `createSalesPipelineLead` is unavailable;
  keep doc ID, request number, and initial routing/default fields aligned with
  callable and ADK paths.
- `sales_pipeline` docs are deduped by deterministic doc ID
  (`buildSalesPipelineDocId` / `_build_doc_id`) and start with
  `status=initiated`, `routingStatus=initiated`, `recommendationStatus=pending`.
- Firestore trigger `recommendSupplierForLead` enriches new leads with
  `suggestedSupplier`, `commissionPreview`, and routing status. Admin recovery
  paths are `retryLeadRecommendation` and `backfillPendingLeadRecommendations`.
- No-match routing can pivot into Amazon affiliate handling. Shared channel
  defaults live in `functions/salesPipeline.js`, exact-match registry logic in
  `functions/affiliateRegistry.js`, admin lead shaping in
  `functions/adminLeadView.js`, and handoff/message formatting in
  `functions/adminAffiliateMessaging.js`.
- If affiliate lead fields change, sync `functions/index.js`,
  `website/lib/types.ts`, `website/app/admin/affiliate-links/AffiliateLinksClient.tsx`,
  and relevant admin/supplier clients. If lead fields change generally, also
  update Kotlin lead models/repositories.
- Website admin flow intentionally mixes direct Firestore edits and callable
  functions: `AdminLeadsClient.tsx` updates ops notes/status directly, while
  recommendation retry, bulk assignment, affiliate backfill, and workflow
  advancement go through callables.

## Auth, Rules, And Deployment
- Everything Firebase Functions-related is pinned to `asia-south1`
  (`FirebaseFunctions.getInstance("asia-south1")`, website `getFunctions(app,
  'asia-south1')`, deployed functions region).
- Admin access is granted by privileged emails or `admin_users/{uid}` in
  `functions/firestore.rules`; supplier auto-approval is tied to the allowlisted
  phone number there too.
- Firestore rules are strict around `sales_pipeline`, `offers`, `suppliers`, and
  affiliate registry writes. Prefer server-side callables for workflow/state
  transitions instead of broadening client writes.
- Production website is GitHub Pages at `maswadkar.com`. The deploy workflow is
  `.github/workflows/deploy.yml` and runs on pushes to `main` with `website/**`
  changes. `website/public/CNAME` holds the custom domain.
- Firebase Hosting config still exists and serves `website/out`, but it is not
  the canonical production site. Do not treat a Firebase Hosting deploy as a
  `maswadkar.com` deploy.
- Functions deploy through Firebase, for example:
  `firebase deploy --only functions:<functionName> --project lumensparkxy`.
  Static website changes require the GitHub Pages workflow unless the user
  explicitly asks for Firebase Hosting.
- Keep Cloud Run agent service non-public. Intended ingress is the Firebase
  callable proxy with IAM auth headers and optional `AGENT_SERVICE_SHARED_SECRET`.

## Development Workflow
1. Read the linked issue or user request plus relevant repo docs before editing.
2. Confirm live state when it matters: GitHub issue/PR state, deployed function,
   GitHub Pages run, Firestore data shape, Cloud Run service, or Android build
   output.
3. Use an issue-scoped branch/PR for planned work when practical. Direct pushes
   to `main` should be reserved for explicit user-directed production fixes or
   this repo's established solo-maintainer flow.
4. Implement only the requested scope.
5. Add/update focused tests for changed behavior.
6. Update docs when behavior, API contracts, deployment, config, or operator
   workflow changes.
7. Run the smallest validation gate that proves the change, then broaden when
   the blast radius crosses Android, Functions, website, agent service, rules,
   or deployment.
8. When committing, include the issue number if one exists.
9. After deploys, verify the actual target: Firebase Functions for callables,
   GitHub Actions/GitHub Pages for `maswadkar.com`, Play Store artifacts for
   Android release, and Cloud Run health for agent service.

## Commands You Will Actually Use
- Android debug/release from repo root: `./gradlew assembleDebug`,
  `./gradlew bundleRelease`, `./gradlew assembleRelease` (see
  `docs/BUILD_RELEASE_BUNDLE.md`). Signing comes from `local.properties`.
- Functions: `cd functions && npm install && npm test`. Targeted deploy:
  `firebase deploy --only functions:<functionName> --project lumensparkxy`.
  Broader backend deploy: `firebase deploy --only functions,firestore:indexes`.
- Functions tests live under `functions/test/*.test.js`; `npm test` exercises
  helpers like `salesPipeline.js`, `leadRecommendation.js`,
  `affiliateRegistry.js`, `adminLeadView.js`, and `affiliateRegistryBackfill.js`.
- Agent service local run:
  `cd agent_service && uvicorn agent_service.main:app --host 0.0.0.0 --port 8080`.
- Agent service tests are `pytest`-style under `agent_service/tests/`;
  `test_firestore_session_service_live.py` only runs when `GOOGLE_CLOUD_PROJECT`
  points to a real project.
- Website local/dev: `cd website && npm install && npm run dev`.
  Production build: `cd website && npm run build`. GitHub Pages uses `npm ci`
  with Node 24 in `.github/workflows/deploy.yml`.
- Local mandi sync fallback: `cd scripts && python mandi_sync_service.py --once`
  or run the launchd workflow in `scripts/README.md`.
- Farmer profile backfill helper:
  `cd scripts && python backfill_farmer_profile_from_legacy.py` for dry run,
  then rerun with `--apply` after review. It only fills missing fields into
  `users/{uid}/settings/farmer_profile`.

## Validation Gates
- Functions/helper changes: `cd functions && npm test`.
- Website/admin changes: `cd website && npm run build`; for production, verify
  the GitHub Pages workflow completed and inspect `https://maswadkar.com/...`.
- Android model/UI changes: run the relevant Gradle unit tests if available and
  at minimum `./gradlew assembleDebug` for broad compile/resource coverage.
- Agent service changes: run focused `pytest` tests under `agent_service/tests/`
  and a local `/health` or `/chat` smoke when endpoint behavior changes.
- Firestore rules/index changes: deploy/test rules carefully and prefer emulator
  or targeted admin read/write checks when feasible.
- Data/backfill operations: dry-run first, inspect counts/conflicts, then apply.
  Report scanned/eligible/changed/skipped/conflict counts.

## Project-Specific Conventions
- Do not simplify away the localhost fallback in `AdminLeadsClient.tsx`; it
  intentionally avoids misleading undeployed-function/CORS failures by reading
  Firestore directly during local web development.
- `ChatRepository.kt` enables Firestore offline persistence, and conversation IDs
  are often pre-generated to align image storage paths with conversation docs.
- `users/{uid}/settings/farmer_profile` is the canonical persisted profile.
  `MandiPreferences.kt` is only a compact projection for mandi UI state, and both
  `SalesLeadRepository.kt` and `agent_service/tools/lead_tools.py` expect
  `farmer_profile` to carry lead-required fields.
- `agent_service/firestore_session_service.py` persists ADK session state/events
  to Firestore and strips inline image bytes before storage; keep that behavior
  if touching session persistence.
- Affiliate registry exact matches are product-name based. Do not overwrite
  active entries during backfills; report conflicting historical links for manual
  review.
- Prefer shared helper modules in `functions/` over duplicating logic inside
  `index.js`, and add `node:test` coverage for helper behavior.

## Files And Paths To Treat Carefully
- Generated/output: `app/build/`, `build/`, `website/out/`, `output/`,
  `tsconfig.tsbuildinfo`, `.firebase/hosting.*.cache`.
- Dependency/vendor folders: `node_modules/`, `functions/node_modules/`,
  `website/node_modules/`, Python virtualenvs.
- Local secrets/config: `local.properties`, `app/google-services.json`,
  `scripts/serviceAccountKey.json`, keystore files, `.env*` files.
- Do not commit credentials, API keys, tokens, service-account JSON, generated
  screenshots, local browser profiles, or one-off local logs.

## Completion Criteria
A change is complete only when:
- The user request or linked issue acceptance criteria are satisfied.
- The implementation stays within scope and unrelated local changes are left
  untouched.
- Relevant tests/builds/deploy verifications are run and reported.
- Documentation is updated for changed workflow, API, configuration, deployment,
  or user-facing behavior.
- Follow-up risks or manual steps are explicit instead of hidden in TODOs.
