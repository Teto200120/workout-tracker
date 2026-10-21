# Workout Tracker for Android

Native Kotlin/Compose implementation in the existing PWA repository. The first foundation supports local profile/RPE setup, Today/History/You navigation, custom-exercise sessions, working/warm-up/completed set entry, durable draft resume and saving a workout into History. The migration bridge adds reviewed PWA JSON import and compatible export. Imported routine launch and offline catalog search/text guides are available. Routine editing, full analytics and native feedback remain subsequent slices. See the progress document for current verification status.

Read [the transition instructions](../docs/ANDROID_TRANSITION.md) and [implementation progress](../docs/ANDROID_IMPLEMENTATION.md) before changing contracts.

## Build and verify

Use a full JDK 17 (not a JRE), Android SDK platform `android-37.0`, build-tools 36.0.0, and the checked-in Gradle wrapper. Set `ANDROID_HOME` or a machine-local `local.properties` with `sdk.dir`. Open this directory in compatible Android Studio, or run:

```sh
./gradlew checkKotlinFormat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

`./gradlew formatKotlin` formats Kotlin source and Gradle scripts.

The second command needs a running Android emulator/device. The instrumented flow starts from fresh application data; use a disposable emulator, not a device holding personal workouts. The APK is `app/build/outputs/apk/debug/app-debug.apk`.

From the repository root, `npm run test:unit` also checks the shared Android golden fixture against the actual PWA backup parser and metrics. Android tests read the same `tests/fixtures/android/baseline.json`. This fixture is synthetic; do not commit private workout exports.

## Toolchain

Versions live in `gradle/libs.versions.toml`: AGP 9.1.1 with built-in Kotlin, Kotlin Compose/serialization plugins 2.2.21, KSP 2.3.4, Gradle 9.3.1, Compose BOM 2026.09.00, Material 3 1.5.0-beta01 and Room 2.8.4. The explicit Material version enables the Expressive theme API and is a beta dependency. Keep it isolated in the catalog and revalidate UI/tests before upgrading. Compile SDK 37.0 uses the minor-version DSL; target SDK is 36 and minimum SDK is 26. Compile SDK does not raise the app's minimum Android version.

## Storage ownership

One Room database, `workout-tracker.db`, schema 1:

- `records`: `(kind, idKey)` composite key, untouched JSON payload and revision. Kinds reserve workout/routine/legacy-weight ownership. Tagged IDs preserve numeric versus string identity; nested arrays/extensions remain in the payload. This is deliberately a compatibility-first store; normalize additional query tables only when needed and preserve the original contract.
- `documents`: named JSON settings/metadata documents and revisions. `native_profile` holds display name/RPE for the UI; `settings`, `goals` and `backup_meta` retain canonical compatibility documents. Profile changes update the canonical settings document atomically while preserving extensions. Foundation-only profiles are explicitly mapped into complete settings on export.
- `drafts`: one active native session, payload and revision. This is not a PWA backup draft format. Saving into History and consuming the draft is one transaction. UI edits update immediately and enqueue immutable whole-draft snapshots in order; the UI distinguishes pending persistence. A killed process cannot retain an entry still marked as saving.

Room schemas are exported under `app/schemas/` and must be committed after schema changes. There is no destructive migration fallback. Subsequent schema changes require upgrade tests. Settings and records participating in imports must stay within the same transaction.

A ViewModel owns an ordered write channel and immutable UI state. Repository writes use revision checks; a stale save/discard does not replace current data. The UI never calls Room. No network permission, accounts, remote workout storage or secrets are needed for the foundation.

Session duration uses monotonic time across process recreation on the same boot. After reboot/unknown boot count it falls back to wall time; negative elapsed time becomes zero and duration is bounded to 24 hours. Time away from the app counts toward elapsed duration. Local workout dates are retained; the draft never re-dates itself on resume. The PWA uses pounds; the native entry field also labels pounds and performs no conversion.

## Preview signing and updates

Stable application ID: `com.hector.workouttracker`. The initial preview was versionCode 1 / `0.1.0-foundation`; the migration preview is versionCode 2 / `0.2.0-migration`; the training preview is versionCode 3 / `0.3.0-training`. Subsequent delivered APKs must increase versionCode and reuse their signing identity. Debug APKs are development previews, not the production signing plan. Keep the original PWA and its backup until native migration and signed upgrade checks pass.

CI can restore a persistent preview key from repository secret `ANDROID_DEBUG_KEYSTORE_BASE64`. Without that secret, CI generates an ephemeral debug key and successive CI artifacts are not guaranteed to update one another. Do not advise uninstalling an app containing workouts to fix a certificate mismatch. Production signing and its secure backup must be finalized before daily-use release. Never commit a keystore or passwords.

## Scope boundaries

Current PWA data is untouched and is not automatically visible to Android. Export a JSON backup from the PWA, then choose **Import PWA backup** in native setup or You, review the preview and confirm. Finish/save the PWA session before exporting; active sessions do not transfer. Finish or discard an active native session before importing. Current and supported legacy backups merge by typed ID; changed matching IDs are replaced, unrelated records remain. Export uses the same current PWA envelope. Keep the browser backup until you verify record counts and representative workouts. Raw rescue files are diagnostic files and cannot be imported here. Native Stats, routine planning and feedback need their own parity gates. User guidance and motion/haptic preference parity follow in those slices. See the progress document for what was actually tested.


## Migration verification

`BackupCodecTest` compares full nested migration output to `tests/fixtures/android/backup-migration.json`; web tests compare the same fixture to the actual PWA migrations. `BackupRepositoryTest` covers transactional rollback, revisions/idempotency, typed IDs, stale previews, active drafts, omitted documents, merged capacity and schema-1 continuity. It emits a synthetic native export that CI verifies with:

```sh
node ../scripts/verify-android-roundtrip.mjs app/build/outputs/compatibility/native-export.json
```

Run that command from `android/` after the unit-test task. It verifies actual native output against the PWA parser and semantic guardrails, including native-created sets. The instrumented flow also checks document-contract cancellation/rejection, preview/confirm, importing alongside a native workout and exporting through ContentResolver. It uses a test-only provider and intercepted picker results; vendor document-picker navigation and physical signed upgrade testing remain manual release gates.


## Training library verification

The generated asset task bundles only `src/data/exercise-catalog.json` from the shared PWA snapshot. Native code uses no remote image references. `CatalogTest` and the web contract test compare ranking/filter order, local spelling precedence and reviewed aliases against the same synthetic fixture. Catalog data is disposable reference data; selected names and typed routine origins are the persistence boundary.

`RepositoryTest` additionally emits `app/build/outputs/compatibility/training-export.json`. Verify it with `node ../scripts/verify-android-training.mjs app/build/outputs/compatibility/training-export.json` from this directory. The device flow covers imported routine launch, alias-guide navigation, filtered exercise search, adding from a guide, resume and export. It does not yet prove true process-death or installed APK upgrade retention.
