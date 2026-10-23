# Workout Tracker for Android

Native Kotlin/Compose implementation in the existing PWA repository. The first foundation supports local profile/RPE setup, Today/History/You navigation, custom-exercise sessions, working/warm-up/completed set entry, durable draft resume and saving a workout into History. The migration bridge adds reviewed PWA JSON import and compatible export. Imported routine launch and offline catalog search/text guides are available. Routine creation/editing/reordering/deletion are available from Today → Manage routines. Empty libraries can explicitly preview and add the six PWA starter routines. Weekly schedule editing and Today plan suggestions work offline. Full analytics and native feedback remain subsequent slices. See the progress document for current verification status.

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

Stable application ID: `com.hector.workouttracker`. The initial preview was versionCode 1 / `0.1.0-foundation`; the migration preview is versionCode 2 / `0.2.0-migration`; the training preview is versionCode 3 / `0.3.0-training`; the routine-editor preview is versionCode 4 / `0.4.0-routines`. Subsequent delivered APKs must increase versionCode and reuse their signing identity. Debug APKs are development previews, not the production signing plan. Keep the original PWA and its backup until native migration and signed upgrade checks pass.

CI can restore a persistent preview key from repository secret `ANDROID_DEBUG_KEYSTORE_BASE64`. Without that secret, CI generates an ephemeral debug key and successive CI artifacts are not guaranteed to update one another. Do not advise uninstalling an app containing workouts to fix a certificate mismatch. Production signing and its secure backup must be finalized before daily-use release. Never commit a keystore or passwords.

## Scope boundaries

Current PWA data is untouched and is not automatically visible to Android. Export a JSON backup from the PWA, then choose **Import PWA backup** in native setup or You, review the preview and confirm. Finish/save the PWA session before exporting; active sessions do not transfer. Finish or discard an active native session before importing. Current and supported legacy backups merge by typed ID; changed matching IDs are replaced, unrelated records remain. Export uses the same current PWA envelope. Keep the browser backup until you verify record counts and representative workouts. Raw rescue files are diagnostic files and cannot be imported here. Native Stats and feedback need their own parity gates. User guidance and motion/haptic preference parity follow in those slices. See the progress document for what was actually tested.


## Migration verification

`BackupCodecTest` compares full nested migration output to `tests/fixtures/android/backup-migration.json`; web tests compare the same fixture to the actual PWA migrations. `BackupRepositoryTest` covers transactional rollback, revisions/idempotency, typed IDs, stale previews, active drafts, omitted documents, merged capacity and schema-1 continuity. It emits a synthetic native export that CI verifies with:

```sh
node ../scripts/verify-android-roundtrip.mjs app/build/outputs/compatibility/native-export.json
```

Run that command from `android/` after the unit-test task. It verifies actual native output against the PWA parser and semantic guardrails, including native-created sets. The instrumented flow also checks document-contract cancellation/rejection, preview/confirm, importing alongside a native workout and exporting through ContentResolver. It uses a test-only provider and intercepted picker results; vendor document-picker navigation and physical signed upgrade testing remain manual release gates.


## Training library verification

The generated asset task bundles only `src/data/exercise-catalog.json` from the shared PWA snapshot. Native code uses no remote image references. `CatalogTest` and the web contract test compare ranking/filter order, local spelling precedence and reviewed aliases against the same synthetic fixture. Catalog data is disposable reference data; selected names and typed routine origins are the persistence boundary.

`RepositoryTest` additionally emits `app/build/outputs/compatibility/training-export.json`. Verify it with `node ../scripts/verify-android-training.mjs app/build/outputs/compatibility/training-export.json` from this directory. The device flow covers imported routine launch, alias-guide navigation, filtered exercise search, adding from a guide, resume and export. It does not yet prove true process-death or installed APK upgrade retention.


## Installed upgrade and process-stop checks

CI builds version 2 from `qa/upgrade-baseline.txt`, adding only `LifecycleAcceptanceTest` and its synthetic asset to that historical test APK. `qa/verify_lifecycle.py` requires an emulator with neither tracker package installed. It checks matching certificates, kills and relaunches actual app processes, upgrades with `adb install -r`, compares raw logical database checkpoints, resumes/finishes through UI-tree-derived taps, and verifies exactly-once History retention. It clears only the synthetic app state that it installed before the normal Compose flow runs.

Run from `android/` with both builds available: `python3 qa/verify_lifecycle.py --serial emulator-5554 --baseline-dir /path/to/baseline/android`. Then run `node ../scripts/verify-android-lifecycle.mjs app/build/outputs/lifecycle/database/lifecycle-export.json`. Use a fresh disposable emulator. Reports and screenshots are under `app/build/outputs/lifecycle/`. A driver phase is skipped in ordinary connected-test runs; it must be driven by the host to prove a process boundary. See slice C2 in the progress document for scope and physical-device limitations.


## Routine editor verification

`RoutineRepositoryTest` covers transactionally enforced name/capacity rules, optimistic revisions plus base fingerprints, raw extensions/typed IDs, no-op saves and isolation from active workouts and History. `node ../scripts/verify-android-routines.mjs app/build/outputs/compatibility/routine-export.json` checks its actual edited export through the PWA importer. The device flow additionally exports `qa/routine-editor-export.json`, validated with `--device` after CI collects it.

Unsaved routine edits use bounded saved-instance state and require explicit Save routine. They survive Activity recreation but are not guaranteed after force-stop. Back asks before discarding changes. Imports/competing saves produce a conflict with explicit reload/discard; no stale overwrite is allowed. Deletion preserves active workouts, saved History and existing schedule-name strings. Starter routines are not inserted automatically.

## Starter library

When the library is empty, Today → Manage routines → See starter routines previews five four-exercise programs plus an empty Custom routine. Add 6 routines commits them together only if the library is still empty. Cancelling writes nothing. Imported routines, edited starters, History, settings and active workouts are never replaced. Deleting every routine makes explicit seeding available again; merely opening the app never seeds or resurrects anything.

`app/src/main/assets/starter-routines.json` is a small generated selection manifest, not a second exercise catalog. Regenerate it from the PWA's actual builder with `node scripts/generate-android-starters.mjs` at the repository root; `--check` rejects stale selections in CI. Android validates catalog revision, exact IDs/names and available guides before preview. Builds use the committed manifest and need no Node runtime. Only routine IDs, names, ordered exercise names and timestamps are persisted/exported.

`StarterLibraryTest` covers unavailable/mismatched catalog data, repeat/concurrent seeding, existing-library protection, forced rollback, database reopen and History/document/draft retention. Run `node ../scripts/verify-android-starters.mjs app/build/outputs/compatibility/starter-export.json` from `android/` after unit tests. CI also validates the device export and exercises preview cancellation, explicit add and screen recreation. Physical S24, accessibility and post-rotation system-bar review remain release checks.

## Weekly schedule

Today → Edit schedule or You → Edit weekly schedule opens a Monday-first editor. Choose gym, soccer or rest for each day, select saved routine names for gym days, then Save schedule. Back confirms discarding edits; a conflict with settings/import changes retains the draft and offers a confirmed reload. Bounded editor values survive Android-managed recreation; unsaved plan changes are not a durable force-stop draft. Imported names are retained, including missing names and blank-name fallback behavior. Routine rename/delete never silently rewrites the plan.

Today displays the saved local-date plan. Soccer/rest stay separate from gym logging; Start workout remains available. A unique exact routine-name match enables Start planned routine; missing or ambiguous names require an explicit library choice. The date, saved day, name uniqueness and selected routine revision are checked again atomically when starting. An active workout keeps its independent snapshot and retains Resume priority. Foreground/resume date refresh handles local date changes without running a background scheduler.

Schedule writes update only the canonical settings document. Other preferences, unknown root/schedule/day fields, profile, goals, History and active drafts remain untouched. No-op saves preserve the exact payload/revision. A revision plus raw-payload fingerprint fences stale writers; large extension payloads never enter saved-instance state. Room remains schema 1 and backup remains envelope 3/schema 2.

`ScheduleRepositoryTest` covers PWA local-date golden fixtures, no-op/rollback, concurrent settings saves, imports/profile conflicts, legacy profiles, bounded state, database reopen and planned-start fences. Verify its actual export with `node ../scripts/verify-android-schedule.mjs app/build/outputs/compatibility/schedule-export.json`. CI checks the device export with `--device` and covers discard, rotation, save/reload, active-workout isolation and planned launch. Physical S24/accessibility/system-bar acceptance remains pending.

### Saved workout details and editing (0.7.0)

Open **History → Full workout → Edit workout** to review or change a saved session. Changes require **Save changes**. Back confirms discarding unsaved changes; deletion requires confirmation. Past-workout edits are independent of an active session. Clear duration to use start/end times. Imported fields outside the editor remain intact. Stale saves/deletes require an explicit reload.

Finishing a session previews its set counts, volume and elapsed time and offers quick tags. These tags remain in the active draft until the session is saved or discarded. The original preview signing identity is retained for updates. Keep the PWA and a backup until physical-device and release acceptance are complete.

### Progress (0.8.0)

Open **Stats** for totals, the rolling seven-day goal, calendar-week activity and personal records. Search an exercise and choose **View progress** for each record's source set/date and the last seven entries. Best load, best set volume and estimated 1RM are independent; warm-ups are excluded. Stats use saved workouts only and update after History changes. Finishing a session shows a dismissible saved-result card on Today after the transaction succeeds.

### Preferences (0.9.0)

Open **You → Edit preferences & goal** for your name, weekly goal, RPE, haptics and animations. **Progression defaults** expands the load jump and rep ranges; native progression suggestions are still being built. Changes apply only after **Save preferences**. Back confirms discarding edits; stale changes offer an explicit reload. Changing RPE preserves all existing values. Your weekly goal updates Stats immediately after saving.

The editor transaction preserves imported extensions, schedules and unrelated records. No-op/equivalent numeric entries preserve stored documents. App animations and haptics honor the saved toggles and Android system settings; keyboard/system UI and loading indicators are outside decorative app motion. Eight JVM/Room tests and the full Compose/upgrade acceptance gates cover this slice. Verify actual exports with `node ../scripts/verify-android-preferences.mjs app/build/outputs/compatibility/preferences-export.json`; device exports use `--device`.
