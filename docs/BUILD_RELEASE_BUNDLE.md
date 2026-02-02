# Quick Reference: Build Signed Release Bundle

## TL;DR - One Command

```bash
cd /Users/m1/AndroidStudioProjects/androidifyxy
./gradlew bundleRelease
```

**Output location:** `app/build/outputs/bundle/release/app-release.aab`

---

## Prerequisites

1. **Keystore file** exists at `app/my-release-key.keystore`
2. **local.properties** contains signing credentials:
   ```properties
   keystore.file=my-release-key.keystore
   keystore.password=<your-password>
   key.alias=<your-alias>
   key.password=<your-password>
   ```

---

## Build Commands

### Release Bundle (AAB) - For Play Store
```bash
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

### Release APK - For Direct Installation
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Clean Build (if issues)
```bash
./gradlew clean bundleRelease
```

---

## Version Bump Checklist

Before building a release:

1. **Update `app/build.gradle.kts`:**
   - Increment `versionCode` (must increase for each Play Store upload)
   - Update `versionName` (user-facing version)

2. **Update docs:**
   - `RELEASE_NOTES.md`
   - `PLAY_STORE_LISTING.md`

---

## Current Version Info

| Field       | Value                                   |
|-------------|-----------------------------------------|
| Package     | `com.maswadkar.developers.androidify`   |
| versionCode | 112                                     |
| versionName | 0.1.12                                  |
| minSdk      | 29 (Android 10)                         |
| targetSdk   | 36                                      |

---

## Upload to Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app
3. Go to **Release > Production** (or Testing track)
4. Click **Create new release**
5. Upload `app-release.aab`
6. Add release notes
7. Review and roll out

---

## Troubleshooting

### Build fails with signing error
- Verify `local.properties` has correct credentials
- Ensure keystore file exists at specified path

### "versionCode already used" error
- Increment `versionCode` in `app/build.gradle.kts`

### Clean Gradle cache
```bash
./gradlew clean
rm -rf ~/.gradle/caches/
./gradlew bundleRelease
```

---

*Last updated: February 2, 2026*
