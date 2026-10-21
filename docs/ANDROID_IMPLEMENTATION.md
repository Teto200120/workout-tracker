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

Local verification: debug APK and instrumentation APK build successfully; all **10 native JVM/Robolectric tests** pass (5 compatibility/domain, 5 actual Room persistence/transaction tests); Android lint reports **0 errors**. Web verification: **236 unit tests**, lint, formatting and release/shell integrity pass. Kotlin sources/Gradle scripts are formatted with ktfmt.

The local workspace has no hardware acceleration; a software emulator failed to boot within six minutes. No local rendered-device result is claimed. Accelerated emulator/Compose flow verification is being run in the Android GitHub workflow before merge. Physical Samsung S24 Ultra validation, large-text/TalkBack acceptance, same-signature upgrade retention and a production signing identity remain release gates.

## Next bounded slice

Complete slice B: validated legacy/current PWA backup parsing, future-version rejection, preview and duplicate/conflict handling, atomic Room merge with supplied settings/goals/metadata, compatible export, round-trip fixtures and explicit active-draft policy. Offer import during first-run migration once it is implemented. Do not silently load, rewrite or delete the user's browser data.

Then build routine/catalog integration and full session/History/Stats parity according to the transition plan. Do not restart general PWA cleanup or treat this foundation as the finished daily-use Android app.
