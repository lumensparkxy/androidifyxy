# Requirements Traceability

This matrix tracks GitHub requirement issues against implementation and validation evidence.

| Requirement | Issue | Status | Implementation | Validation |
| --- | --- | --- | --- | --- |
| Field Diary V1 data, repository, photo storage, and owner-only rules | [#26](https://github.com/lumensparkxy/androidifyxy/issues/26) | Implemented in `issue-26-field-diary-foundation` | `FieldDiaryEntry`, `DiaryActivityType`, `FieldDiaryRepository`, `UserSettingsFirestore` helpers, Firestore owner rule, Storage owner rule | `FieldDiaryEntryTest`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `functions/npm test`, Firestore/Storage emulator smoke |
| Field Diary Home tile and drawer navigation | [#27](https://github.com/lumensparkxy/androidifyxy/issues/27) | Implemented in `issue-27-field-diary-navigation` | `Screen.FieldDiary`, `FieldDiaryScreen` placeholder route, Home tile replacement, drawer item/callbacks, diary icon, localized strings | `HomeFeatureTest`, `NavigationDrawerTest`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `functions/npm test` |
| Field Diary Clean Timeline screen | [#28](https://github.com/lumensparkxy/androidifyxy/issues/28) | Implemented in `issue-28-field-diary-timeline` | `FieldDiaryViewModel`, timeline grouping/filtering helpers, Material 3 timeline UI, filters, empty/error states, temporary add-entry stub | `FieldDiaryTimelineTest`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `functions/npm test` |
| Field Diary add and edit entry form with photos | [#29](https://github.com/lumensparkxy/androidifyxy/issues/29) | Planned | Depends on #26 repository/photo APIs and #28 launch point | Not started |
