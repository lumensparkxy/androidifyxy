# Android Production Release Guide

This repo ships three surfaces (Android app, website, functions). This document covers **Android production releases** and aligns with current files in the repo.

## 1) Branching & naming (GitHub)

Pick one of these two lightweight models and keep it consistent:

### Option A: Tag-based releases off `main` (simplest)
- **`main`**: always green and releasable.
- **Release tags**: `v<versionName>` (e.g., `v1.0.0`).
- **Hotfix**: commit to `main`, tag a new patch version.

### Option B: Release branches (more control)
- **`main`**: stable, production-ready.
- **`develop`** (optional): integration branch.
- **Release branches**: `release/<versionName>` (e.g., `release/1.0.0`).
- Merge release branch into `main`, then tag `v<versionName>`.

**Recommendation:** Start with Option A unless you have multiple teams or long stabilization cycles.

## 2) Versioning (build.gradle.kts)

Android versioning lives in `app/build.gradle.kts`:
- `versionCode`: integer that **must increase** each Play Console upload.
- `versionName`: user-facing semver-like string (`1.0.0`).

**Current repo state:**
- `versionName` is `0.10.0` in `app/build.gradle.kts`.
- `RELEASE_NOTES.md` and `PLAY_STORE_LISTING.md` should match the same version.

**Action:** Align all three so they match **the release you publish**.

## 3) Release notes & store listing

Keep these files aligned with the version you ship:
- `RELEASE_NOTES.md` (full notes)
- `PLAY_STORE_LISTING.md` (Play Console copy)

Tip: Update these in the same commit as your version bump.

## 4) Signing & secrets

Release signing is configured in `app/build.gradle.kts` and pulls keys from `local.properties`:
- `keystore.file`
- `keystore.password`
- `key.alias`
- `key.password`
- `WEATHER_API_KEY` (WeatherAPI.com)

**Rules:**
- Never commit keystores or passwords.
- Keep `local.properties` out of version control.
- Store the keystore in a secure vault (1Password, Bitwarden, etc.).

## 5) Build artifacts (AAB/APK)

For Play Store, publish **AAB**:
- Output: `app/release/app-release.aab`

If you need a local debug build:
- Use standard Android Studio build variants.

## 6) Release checklist (recommended)

1. Pick a target version (e.g., `1.0.0`).
2. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.
3. Update `RELEASE_NOTES.md` and `PLAY_STORE_LISTING.md`.
4. Merge to `main` (or merge `release/*` to `main`).
5. Tag the release: `v<versionName>`.
6. Build signed AAB.
7. Upload to Play Console and verify.

## 7) Optional CI improvements

There is currently a GitHub Actions workflow for the website only (`.github/workflows/deploy.yml`).
If you want Android CI:
- Add a workflow that builds `bundleRelease` and/or `assembleRelease`.
- Upload artifacts for internal QA.
- (Optional) integrate Play Console publishing using service accounts.

---

If you want, I can add an Android build workflow and a lightweight release tag workflow next.