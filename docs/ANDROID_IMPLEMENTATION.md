# Android implementation progress

Started 2026-10-20 Eastern, after the PWA/Cloudflare baseline closure. Follow [ANDROID_TRANSITION.md](ANDROID_TRANSITION.md) for the complete contract and ordered acceptance gates. This file tracks native implementation only; [BASELINE_HANDOFF.md](BASELINE_HANDOFF.md) remains the web completion record.

## First slice: foundation and a minimal workout flow

Implemented in `android/`:

- Single app module, pinned version catalog, Gradle wrapper and Android CI. Package `com.hector.workouttracker`; minimum Android 8/API 26, compile API 37.0, target 36. No internet/storage runtime permission required.
- Native Compose Material Expressive theme with branded navy/coral tonal roles, system light/dark palettes and native back-stack navigation. Today, History and You are separate destinations; session and onboarding have their own feature owners.
- Display-name setup, RPE preference, custom-exercise set entry, optional RPE, warm-up/completed flags, notes, adding sets and confirming finish/discard. Pounds match the PWA. No placeholder buttons pretending that imports/routines/catalog/Stats are available.
- Room schema 1 with typed record keys, raw compatibility JSON, revisioned documents and a durable native draft. Repository handles conditional writes; the ViewModel owns ordered snapshots and explicitly reports pending/failed persistence.
- Atomic History insert plus draft consumption. Repeated finish cannot save twice; a failed insert leaves the draft. Process recreation reads the committed draft and profile. This does not promise that a keystroke still marked as saving survives immediate termination.
- Deterministic elapsed-time rules: monotonic time on the same boot, bounded wall-time fallback after reboot, local session date retained.
- Synthetic shared JSON fixture checked by both web and Kotlin metric/ID tests. It covers numeric/string zero IDs, blank/zero fields, nested extensions, unchecked working sets, warm-ups, explicit zero duration and midnight rollover.

The native model is not a complete backup importer. Raw record storage preserves JSON but does not substitute for validation/migrations. The minimal native profile is not exported as canonical PWA settings. Full PR winner/grouping/schedule fixtures remain part of their feature-porting slices.

## Verification

Local verification: debug APK and instrumentation APK build successfully; all **10 native JVM/Robolectric tests** pass (5 compatibility/domain, 5 actual Room persistence/transaction tests); Android lint reports **0 errors** (5 toolchain/version advisory warnings). Web verification: **236 unit tests**, lint, formatting and release/shell integrity pass. Kotlin sources/Gradle scripts pass the ktfmt check. After a native build, generated Gradle HTML-report JavaScript is excluded from the web ESLint scope; normal web checks still cover authored web code.

The local preview APK is signed and its preview key has a separate private backup named `WorkoutTracker-preview-signing.keystore`; restore that identity before producing a replacement local preview. Certificate SHA-256: `745ae6710c550a68759a188e2bdeaa81f9d1959df3a49e7d2a3fe5b35325198f`. No key is committed, and the CI signing secret has not been configured. Same-signature version upgrade acceptance and production signing are not yet complete.

The local workspace has no hardware acceleration; a software emulator failed to boot within six minutes. Rendered verification therefore uses GitHub's accelerated Android 15/API 35 emulator. The first complete native run at `d22c148` passed **10 JVM/database tests plus one instrumented workout-flow test**, including screenshot collection: [Android CI](https://github.com/Teto200120/workout-tracker/actions/runs/39540400674). Its [full web CI](https://github.com/Teto200120/workout-tracker/actions/runs/39540400680) passed **236 unit, 124 browser and 18 backend tests**, lint, formatting, catalog and release checks.

Today, active-session and History screenshots were inspected. That review completed the branded surface roles and singular set labels; the screenshot helper hides the keyboard for a clear capture. The final app and instrumentation APKs compile and pass formatting/lint locally. Latest per-commit CI results and reports are attached to [PR #31](https://github.com/Teto200120/workout-tracker/pull/31). The CI command retains only the disposable emulator install long enough to collect screenshots.

Physical Samsung S24 Ultra validation, large-text/TalkBack acceptance, same-signature version-upgrade retention and a production signing identity remain release gates. The next implementation slice is the backup migration bridge, not another general PWA cleanup pass.

## Migration bridge

Slice B is described below. It adds explicit migration through a JSON file; it never silently reads or removes browser data.

Then build routine/catalog integration and full session/History/Stats parity according to the transition plan. Do not restart general PWA cleanup or treat this foundation as the finished daily-use Android app.

## Slice B: PWA backup migration bridge

Implemented in `android/` on top of the foundation. The merge gate includes the native CI flow and actual native-export compatibility checks below.

- System document contracts (`OpenDocument` / JSON `CreateDocument`) on first-run setup and You. The app requests no broad storage permission. Cancellation leaves data unchanged; parsing and file I/O run off the main thread.
- Bounded UTF-8 input: 25 MiB stream limit, nesting preflight before recursive JSON parsing, 20 levels and 500,000 accepted values. Future/conflicting versions, malformed structure, duplicate typed IDs and semantic bounds are rejected before a preview. Unsafe prototype-like extension keys are rejected explicitly rather than exported into JavaScript consumers.
- Legacy envelopes 0–2 and current envelope 3, logical schema 0→1→2. Migration fixtures are derived from the actual PWA migrations, including legacy name reset, numeric/boolean normalization, blank/zero/RPE values, typed zero IDs and nested extensions. Current records retain their original JSON values and ordered arrays.
- Preview separates new, changed matching-ID and unchanged counts for workouts/routines/legacy weights. It names changed preferences, shows the resulting weekly goal/name/RPE preference, and discloses settings/goals/metadata replacement. Other records remain. Supplied settings replace the canonical document; omitted settings retain preferences but reset the display name to setup, matching the PWA restore policy. Omitted goals/metadata remain untouched.
- One Room transaction merges records, settings, goals, backup metadata and the native profile. Changed records/documents advance revisions; unchanged imports are no-ops. A fingerprint of the coherent preview snapshot rejects confirmation after any saved state changed. Active native drafts block both preview and confirmation. No destructive schema change or database reset is involved; Room remains schema 1.
- Native profile edits update the canonical settings document in the same transaction, retaining imported extensions, schedule and unsupported preferences. When a legacy import requires name setup, onboarding initializes RPE from the imported profile and retains that preference when saving the name. Export maps foundation-only profiles into complete canonical settings. Disabling RPE does not delete historical values.
- Export takes a coherent database snapshot, emits envelope 3/schema 2 and validates the result. Native drafts/device state, education, journals and feedback outboxes are excluded. A successful export notice appears only after the provider stream closes; creating an export does not mutate saved metadata. Interrupted/failed provider writes can leave an incomplete destination file and must be retried.
- Imported routines, legacy weights, goals, schedule and unsupported preferences are retained and re-exported. Routine/catalog/Stats screens and complete haptic/motion preference application remain later slices.

Preview version: versionCode 2 / `0.2.0-migration`, using the original preview signing identity. A real signed APK upgrade on a physical device remains a release gate; a schema-1 reopen test alone is not an APK upgrade test.

### Verification for this slice

Local verification: APK and instrumentation APK build; Kotlin formatting passes; **23 native JVM/Room tests** pass, including a 2,000-workout history and forced rollback after records/profile/settings writes. Android lint has no errors. The actual native-generated export passes the PWA parser and semantic guardrails. Web lint, formatting, release integrity and **237 unit tests** pass. The accelerated emulator flow and screenshots are an Android CI merge gate; current results are recorded in the implementation PR and [Android verification runs](https://github.com/Teto200120/workout-tracker/actions/workflows/android.yml). The document-contract emulator test supplies a test provider through the activity-result registry; it verifies the real ContentResolver streams, picker intent actions and preview/confirm UI, not every vendor's document-picker UI. No private workout exports are committed.

### Next bounded slice after B

Continue slice C: routine/catalog integration in the workout flow, complete kill/relaunch recovery and same-signature APK upgrade retention acceptance. Then slice D covers the remaining schedule/History/PR/Stats/settings parity. Keep the existing PWA and its backup until native daily-use acceptance is complete.
