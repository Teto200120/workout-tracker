# Workout Tracker for Android

Native Kotlin/Compose implementation in the existing PWA repository. The first foundation supports local profile/RPE setup, Today/History/You navigation, custom-exercise sessions, working/warm-up/completed set entry, durable draft resume and saving a workout into History. The migration bridge adds reviewed PWA JSON import and compatible export. Imported routine launch and offline catalog search/text guides are available. Routine creation/editing/reordering/deletion are available from Today → Manage routines. Empty libraries can explicitly preview and add the six PWA starter routines. Weekly schedule editing and Today plan suggestions work offline. Saved-workout editing, personal records, Stats and preferences are available. Native progression guidance and last-set reuse are available. Backup status/reminders and offline help with dismissible tips are available in You. Reviewed settings reset and local-data clear are available. Browser feedback is implemented locally; publishing its hosted entry and final release acceptance remain open. See the progress document for current verification status.

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

Version 12 / `0.12.0-preview` starts a permanent release signing identity for a new installation, as explicitly selected by the owner. Earlier CI previews used temporary debug keys; the old private key is unavailable, so this release cannot update those installations in place. Preserve the previous app and a verified JSON export before planning that transition. Never remove an app containing unbacked workouts or an unfinished session to resolve a certificate mismatch.

After native verification passes, trusted branch-push CI builds the existing non-debuggable release variant, signs it with repository secrets `ANDROID_RELEASE_KEYSTORE_BASE64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_PASSWORD` and `ANDROID_RELEASE_KEY_ALIAS`, and verifies the public certificate against `qa/release-certificate.sha256`. The `workout-tracker-android-release-preview` artifact contains the signed APK, SHA-256 checksum, signature/manifest reports and source-commit/run provenance. This is the deliverable artifact. Ordinary debug APKs and verification reports remain development evidence. Future delivered versions must increase versionCode and reuse this permanent key. Keep private key/password backups outside Git; only the public certificate fingerprint is committed. Physical S24 and daily-use acceptance remain required.

## Scope boundaries

Current PWA data is untouched and is not automatically visible to Android. Export a JSON backup from the PWA, then choose **Import PWA backup** in native setup or You, review the preview and confirm. Finish/save the PWA session before exporting; active sessions do not transfer. Finish or discard an active native session before importing. Current and supported legacy backups merge by typed ID; changed matching IDs are replaced, unrelated records remain. Export uses the same current PWA envelope. Keep the browser backup until you verify record counts and representative workouts. Raw rescue files are diagnostic files and cannot be imported here. Stats and preference verification are recorded in the progress document. Backup reminders and guidance have dedicated native tests; native feedback and final feature/device acceptance remain open. See the progress document for what was actually tested.


## Migration verification

`BackupCodecTest` compares full nested migration output to `tests/fixtures/android/backup-migration.json`; web tests compare the same fixture to the actual PWA migrations. `BackupRepositoryTest` covers transactional rollback, revisions/idempotency, typed IDs, stale previews, active drafts, omitted documents, merged capacity and schema-1 continuity. It emits a synthetic native export that CI verifies with:

```sh
node ../scripts/verify-android-roundtrip.mjs app/build/outputs/compatibility/native-export.json
```

Run that command from `android/` after the unit-test task. It verifies actual native output against the PWA parser and semantic guardrails, including native-created sets. The instrumented flow also checks document-contract cancellation/rejection, preview/confirm, importing alongside a native workout and exporting through ContentResolver. It uses a test-only provider and intercepted picker results; vendor document-picker navigation and physical signed upgrade testing remain manual release gates.


## Training library verification

The generated asset task bundles only `src/data/exercise-catalog.json` from the shared PWA snapshot. Native code uses no remote image references. `CatalogTest` and the web contract test compare ranking/filter order, local spelling precedence and reviewed aliases against the same synthetic fixture. Catalog data is disposable reference data; selected names and typed routine origins are the persistence boundary.

`RepositoryTest` additionally emits `app/build/outputs/compatibility/training-export.json`. Verify it with `node ../scripts/verify-android-training.mjs app/build/outputs/compatibility/training-export.json` from this directory. The device flow covers imported routine launch, alias-guide navigation, filtered exercise search, adding from a guide, resume and export. True process-death and installed APK upgrade retention are covered by the separate host-driven acceptance harness below.


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

Open **You → Edit preferences & goal** for your name, weekly goal, RPE, haptics and animations. **Progression defaults** expands the load jump and rep ranges; suggestions use saved History and the RPE preference. Changes apply only after **Save preferences**. Back confirms discarding edits; stale changes offer an explicit reload. Changing RPE preserves all existing values. Your weekly goal updates Stats immediately after saving.

The editor transaction preserves imported extensions, schedules and unrelated records. No-op/equivalent numeric entries preserve stored documents. App animations and haptics honor the saved toggles and Android system settings; keyboard/system UI and loading indicators are outside decorative app motion. Eight JVM/Room tests and the full Compose/upgrade acceptance gates cover this slice. Verify actual exports with `node ../scripts/verify-android-preferences.mjs app/build/outputs/compatibility/preferences-export.json`; device exports use `--device`.

### Training guidance (0.10.0)

Saved routines load their last logged sets when a new workout starts. Each exercise shows its last logged date, a suggested target and its best estimated-1RM source set. **Review last sets** shows all previous rows. **Use last sets** copies warm-ups and RPE; **Use target sets** copies the suggested working sets. Both reset completion, generate independent row keys and ask before replacing entered values. Targets follow the PWA's existing progression rules and the saved load-jump, rep-range and RPE preferences. Guide aliases never merge training History.

Changes to History/preferences invalidate a pending copy. The source and the complete destination draft are checked again in the write transaction. Failed/stale requests keep entered values; review the latest guidance and choose again. Existing or resumed drafts are never automatically refilled. Legacy RPE zero retains native provenance on copied rows; editing RPE clears that provenance. Native-only row metadata stays out of exports. Targets outside canonical bounds/precision cannot be copied.

`generate-android-training-rules.mjs --check` compares 25 pure-rule fixtures to the actual PWA implementation. Eight JVM/Room tests cover rules, source selection/ties, transactional copy/retry/conflicts, routine auto-load, observers and compatible exports. The complete Compose flow and installed version-2→10 upgrade/recovery gates remain required. Check actual unit/device output with `verify-android-progression.mjs` (device uses `--device`).


## Backup reminders and help

You shows the last recorded export age (including imported metadata), with 14/30-day reminders. Only a successfully closed document export updates the timestamp; metadata failures report the file success separately. Normal backups exclude active drafts and device-only guidance preferences. Help & guidance works offline, can replay seven contextual tips, and resets only those tips after confirmation. No reminder notifications are scheduled. See [the finite feature acceptance checklist](../docs/ANDROID_FEATURE_ACCEPTANCE.md) for remaining parity and release gates.

## History filters

History offers **Search exercises** and **Routine** filters, combined together. Exercise search ignores case and surrounding whitespace and searches exercise names only. Routine matches use the exact recorded name. The native chooser includes current library names and historical names from saved workouts; deleting or renaming a routine does not hide its older sessions. **Clear filters** returns to all saved workouts. Filters survive screen changes and Android-managed recreation and never modify workouts or the active session.

`node scripts/generate-android-history-filters.mjs --check` at the repository root checks 22 cases against the actual PWA renderer. `HistoryFiltersTest` checks the same fixture, including numeric/string IDs and Unicode, plus live data rebuilding. The complete instrumented flow covers combined filters, empty/clear results, saved-state retention and live History changes. See D9 in the implementation notes for current local validation and release limitations.

## Active workout order

Use **Move up** or **Move down** on an expanded exercise to change the order within the active workout. Positions and button descriptions identify the row, including repeated names. Entered sets and notes stay with their original exercise. Order saves through the same on-device draft mechanism as set entry; check the saving/error indicator before leaving. Saved routines and past workouts stay independent. See D10 and D11 in the implementation notes for validation.

## Exercise navigation and compact summaries

**Collapse exercise** keeps its entered values and shows completed working-set progress. **Expand exercise** opens that exercise and collapses the others. **Next exercise** opens the next row in the current order and dismisses keyboard focus; it never marks sets completed. The final row offers **Review workout**, which opens the existing save confirmation. **Keep editing** cancels review without saving to History.

Collapsed cards show up to three completed working-set summaries and a count of any additional completed rows. Warm-ups are excluded from that progress and numbering, matching the PWA. Expand a card to inspect every set. Collapse state saves in the native draft, survives recreation/process restart and remains outside canonical workouts/backups. Older drafts default to expanded cards. Saved routines, settings and History are unaffected. See D11 in the implementation notes for verification and remaining release gates.

## Strength snapshot and recent sessions

Stats shows up to three narratives about recent personal records, rising exercise trends and volume changes against the previous workout of the same recorded type. **View all insights** reveals the bounded detail list; **Show fewer insights** returns to the preview. These observations follow the PWA's loaded working-set rules and 60-day PR cutoff. They are descriptions of saved records, not training prescriptions. The recent-session preview shows the three newest saved workouts with their recorded duration and exercise count. Full details remain in History. Active drafts are excluded and these derived views are never stored in backups.

`node scripts/generate-android-insights.mjs --check` checks 24 cases against the actual PWA renderer. See D12 in the implementation notes for native, device and visual verification.

## Reset settings and clear local data

**You → Reset app settings** restores the default schedule, load jump, rep ranges, RPE, haptics and animations after a separate review. Your name, weekly goal, saved records, backup reminder, guidance and active workout stay intact. Previously entered RPE values remain. Reset intentionally replaces settings extensions, matching the PWA reset action.

**You → Clear local data** reviews the current counts and active-workout warning before clearing records, profile, preferences, goal, draft, backup metadata and guidance in one transaction. It restores the six reviewed starter routines and returns to fresh setup. Export a backup first to retain saved records; backups exclude the active workout. Existing exported files, PWA data and browser feedback reports stay unchanged. Cancel writes nothing. A changed review or failed transaction keeps the previous data and asks you to review again.

Clear advances a device-only data epoch and revision fences, then finishes the old Activity task. Old navigation/editor state cannot restore the cleared profile or draft after a process restart. The epoch is excluded from canonical backups. Room schema 1 and backup envelope 3/schema 2 are unchanged. `generate-android-data-actions.mjs --check` records 12 cases from the actual PWA reset handler. `DataMaintenanceTest`, the full Compose flow and the dedicated host-driven clear/cold-start checks cover the destructive actions. Actual exports pass `scripts/verify-android-data-actions.mjs reset|clear <export>`; reset device exports also use `--device`.

## Browser feedback

**You → Beta feedback → Open feedback in browser** opens the existing hosted feedback form at `https://teto200120.github.io/workout-tracker/?feedback=android`. The browser entry skips browser workout onboarding and does not open or migrate its workout database. Android passes only that fixed URL. Enter your Android app version and reproduction steps manually; optional diagnostics describe the browser page. No Android records, files or diagnostics are attached automatically.

Reports use the existing browser outbox, verification challenge, receiver and deadlines. Saving shows the durable pending copy immediately while verification runs. Failed sends survive reload; reopen in the same browser to explicitly retry or delete. Native data clearing does not delete browser reports. Internet is needed for first access and sending; a successfully cached page can reopen and save pending reports offline. Private browsing or clearing browser storage can remove that browser's pending reports.

The native screen handles a missing/restricted browser with a retry message. This is a hosted-browser adaptation, with no native queue or WebView. Publishing the hosted entry and verifying its real challenge/delivery remain release gates. The changes are local; no Pages/Worker deployment or live report submission is included. See D14 in the implementation notes for QA and remaining acceptance.

## Performance baseline

E1 in the implementation notes records an initial debug/software-emulator baseline: five process-cold Activity launches, five Today/History/Stats/feedback navigation cycles, memory/frame statistics and a separate Perfetto trace. It uses synthetic empty History and six starters. The measurements and their limits are retained in the continuation handoff. Release/profileable measurements on the S24 with representative History, trace analysis and physical acceptance remain required.

## Accessible setup and saved-workout flags

The setup **Track RPE** label and switch act as one named switch row. In saved-workout editing, **Completed** and **Warm-up** each have a labeled checkbox row with exercise position/name and set number. The rows wrap at narrow widths or large text sizes, and tapping a label toggles its flag. Disabled controls keep their disabled appearance while saving. Values still persist only through the existing explicit Save changes action; cancel/discard keeps the saved workout and independent active session unchanged. E2 in the implementation notes records focused offline/configuration checks. Manual TalkBack and physical S24 accessibility acceptance remain open.

History discard, reload and delete confirmations expand within the available width and allow scrolling their explanations so large text remains readable in landscape. Their existing actions and data effects are unchanged.

## Keyboard and system insets

The app consumes the Scaffold's applied content insets before adding keyboard padding. This keeps the scrollable content aligned with the software keyboard instead of reserving system/navigation space twice. The ordinary full Compose flow opens a real keyboard during set entry, checks the viewport against Android's reported IME edge and confirms the entered reps remain visible. It temporarily enables keyboard display on hardware-keyboard emulators and restores that secure setting afterward. E3 in the implementation notes records focused navigation/theme/configuration checks; physical device and vendor keyboard acceptance remain open.

Weight, reps and optional RPE keep their compact row when enough space is available. Their group wraps into fewer columns as available width shrinks or text size increases, keeping the existing labels, entered values, units and callbacks.

## Active-workout confirmations

Discard, set replacement and Save to History use the same adaptive dialog width as History editing, with 24dp side clearance and up to 560dp content width. Their explanations can scroll at large text sizes; the finish review retains its scrollable summary and tags. The warning and cancellation actions stay available before an explicit destructive/save action. Cancellation keeps the entered draft and saved History unchanged. E4 in the implementation notes records the focused checks; manual TalkBack and physical device acceptance remain open.

## Preference confirmations

Discard edits, reload saved preferences and unusual-value review use the existing adaptive dialog width and vertically scrollable warnings. This keeps explanations and both actions available at large text sizes in landscape. Changes still require Save preferences and, for unusually high values, Save anyway. Keep editing preserves unsaved entries without changing saved preferences. E5 in the implementation notes records pending-dialog recreation, conflict-reload cancellation, focused 200% configuration checks and cold-resume data checks. Physical-device and manual TalkBack acceptance remain open.

## Routine and schedule dialogs

Routine discard/reload/delete/repeated-exercise reviews and schedule choices/discard/reload use the established adaptive dialog width. Plain explanations can scroll, the activity choices can scroll, and the schedule routine list keeps its existing scrolling. This keeps Gym, Rest and Soccer reachable at large text sizes in landscape. Selection, explicit saves/deletes and conflict handling are unchanged. E6 in the implementation notes records chooser cancellation, pending discard restoration, exact reload-cancellation snapshots and focused 200% configuration checks. Final physical-device and manual TalkBack acceptance remain open.

## Import review and system documents

The import review uses the existing adaptive-width dialog pattern and retains its scrolling explanation. Replacement/settings warnings and Import/Cancel behavior are unchanged. E7 in the implementation notes covers pending-review recreation, scroll access, exact cancellation snapshots and real Android OpenDocument/CreateDocument flows on a disposable emulator, including a PWA-validated saved file while the active native draft remains excluded and intact. Physical-device acceptance remains open; cloud providers have not been tested.

## Reset and clear reviews

Reset settings and Clear local data use the established adaptive-width dialog pattern while retaining the existing scrolling warnings, reviewed counts, explicit confirmation and revision/epoch fences. E8 in the implementation notes covers pending-review recreation, warning access, exact cancellation snapshots, 200% landscape/portrait checks and refreshed actual clear/cold-restart/fresh-profile acceptance on the disposable emulator. No reset/clear scope or backup format changed; physical-device and manual TalkBack acceptance remain open.

## Remaining dialog configuration pass

Help's Reset guidance review uses the existing adaptive-width/scrolling pattern so the complete preserved-data explanation remains readable at large text sizes. Its text and explicit Reset tips/Keep guidance behavior are unchanged. E9 covers pending-review recreation and exact cancellation, plus 200% landscape/portrait checks of Home routine choice, History routine filtering, all three catalog filters and previous-set review. Those other layouts already work and are retained. This scoped pass does not replace physical-device, TalkBack or final one-handed acceptance.

## Performance diagnostics

E10 in the implementation notes analyzes the saved E1 trace and records five cold Activity starts plus three History entry/scroll flows per current debug and existing release variant, using 1003 synthetic workouts. The release diagnostic uses the existing non-minified configuration and local QA certificate. Dataset/APK restoration, raw traces, query results, trace-health exclusions and metric definitions are recorded separately. This is emulator evidence; physical S24 release performance and production signing/CI acceptance remain open. No source optimization or build/signing configuration change is included.
