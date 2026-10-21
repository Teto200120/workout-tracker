# Native Android transition instructions

Status: implementation plan, 2026-10-21 UTC (2026-10-20 Eastern). Native implementation has not started. The completed web baseline is recorded in [BASELINE_HANDOFF.md](BASELINE_HANDOFF.md); the finite review dispositions are in [PRE_ANDROID_CHECKLIST.md](PRE_ANDROID_CHECKLIST.md).

## Product direction and scope

Build a proper native, local-first Android workout tracker with Kotlin, Jetpack Compose and Material 3 Expressive. Keep the recognizable navy/coral identity, workout-first hierarchy and quick logging workflow. Preserve routines, schedule, RPE preference, progression suggestions, History, independent PRs, Stats, custom exercises, offline catalog/guides, profile and backup behavior. The PWA remains usable during the transition.

This is a new native presentation and persistence implementation with compatible user-data import, not a WebView wrapper or a translation of browser event handlers. No accounts, cloud workout sync, local AI, social features or iOS implementation are required for the first native release. Feedback remains separate from workout storage. Do not reopen general PWA cleanup as a prerequisite.

## Read before implementation

1. [DATA_SCHEMA.md](DATA_SCHEMA.md): logical records, versioned backup envelope, legacy inputs, unknown-field preservation and exclusions.
2. [METRICS_CONTRACT.md](METRICS_CONTRACT.md): current calculations and deliberately different exercise-name matching rules.
3. [ARCHITECTURE.md](ARCHITECTURE.md): existing domain/application/storage boundaries and web limitations.
4. [ONBOARDING_AND_ROUTINE_CATALOG.md](ONBOARDING_AND_ROUTINE_CATALOG.md), [BETA_EDUCATION.md](BETA_EDUCATION.md), [EXERCISE_CATALOG_SPIKE.md](EXERCISE_CATALOG_SPIKE.md): behavior, catalog provenance/licensing and guidance.
5. [QA_CHECKLIST.md](QA_CHECKLIST.md), [TESTING.md](TESTING.md), and current tests: behavior references, not proof that Android works.

Use source and fixtures as the final reference when a historical progress entry differs from the current implementation. Port pure rules to Kotlin with shared JSON input/expected-output fixtures. Do not embed JavaScript just to reuse calculations.

## Architecture and repository layout

Recommended starting point: add `android/` to this repository so the web reference, backend and native contract tests remain together. Keep web deployment workflows scoped to web files when adding Android CI. Build from current main, using a short-lived implementation branch for review; merge completed slices back to main. Do not maintain competing long-lived migration branches.

Start with one Gradle app module and clear packages: `core/model`, `core/database`, `core/backup`, `core/designsystem`, `domain`, `data`, and `feature/{home,session,routines,history,stats,settings,onboarding,feedback}`. Extract Gradle modules only when dependency/build ownership warrants it. Use a Gradle wrapper and version catalog; select compatible supported SDK, Kotlin, Compose, Room and tooling versions at implementation time and record them. SDK and package identifiers are not decided by this plan.

- Compose screens render immutable UI state and emit actions. ViewModels coordinate state through StateFlow and lifecycle-aware collection; repositories own persistence. No composable reads or writes the database directly.
- Pure Kotlin rules own metrics, scheduling, progression, validation and serialization. Inject clock/date inputs; avoid hidden global session state.
- Room is the source of truth for user records and durable session drafts. Keep imported settings/goals that must commit with records in the same database transaction. DataStore may hold independent device presentation preferences; do not recreate the web cross-storage rollback problem.
- Start with explicit dependency construction; add a DI framework only if it simplifies actual dependencies.
- Use native navigation with a defined back stack, keyboard/inset handling and state restoration. Back from a session must preserve its durable draft; deliberate discard requires confirmation.
- Use WorkManager only for suitable persistent background work, such as a consented feedback queue. It is not a precise workout timer. Timer display derives from persisted timing state and a monotonic clock while running; define restart/reboot handling explicitly and test it.

## Storage, import and compatibility requirements

Android cannot directly read the installed PWA's private IndexedDB/localStorage. The initial bridge is an explicit PWA JSON export and Android import through the system document picker. Explain this once in migration onboarding. Do not imply automatic shared storage or ongoing synchronization.

1. Preserve current backup envelope 3 / application schema 2 as the interchange contract. Support documented legacy envelopes 0–2 and application migrations 0→1→2. Reject future versions before writes. Native Room schema versions are independent of those numbers.
2. Represent compatibility IDs with their type. Numeric `0` and string `"0"` are distinct: use a tagged key plus the original JSON ID, not a lossy string cast. Preserve array order with explicit positions, local workout dates, zero duration, blank numeric inputs, timestamps, notes, tags and safe unknown extension fields at every nested level.
3. Separate compatibility DTOs from native entities/UI models. Keep safe unknown JSON fields in explicit extension storage and include them on re-export. A native numeric UI must not discard the distinction between blank and zero or silently change imported units. Inspect and document current load units; never guess a conversion.
4. Validate file size/complexity, structure, versions, duplicate typed IDs and semantic ranges before mutation. Preview counts and existing-ID conflicts; import only after confirmation. Preserve current merge-by-typed-ID behavior and unrelated records. Existing-ID replacement is an intentional import action, distinct from an ordinary stale-editor save.
5. Apply the complete accepted import, including supplied settings/goals/backup metadata, in one Room transaction. Failed imports leave the original database intact. Repeat import must not duplicate records. Reject or explicitly reconcile an active native draft before import; never overwrite it silently.
6. Current normal backups exclude active drafts, education state, recovery journals and feedback outbox. Ask the user to finish/save the web workout before export; a normal backup cannot transfer an unfinished session. Raw rescue exports are a separate diagnostic format and must not be presented as normal importable backups.
7. Retain legacy weights without restoring the removed body-weight UI. Catalog selection remains a name-only boundary; no automatic historical rename, alias merge or new catalog identity migration.
8. Native export must round-trip supported data and be tested against the PWA importer before claiming reverse compatibility. Native-only draft/device state remains separate. Changes to the interchange format need an explicitly versioned adapter and product decision.
9. Export Room schemas and test upgrades. No destructive migration fallback for user data. Maintain one stable application ID and signing identity across delivered APK updates, with monotonically increasing versionCode; keep signing secrets outside source control. Android updates must retain data without requiring export/import for every installation.

Preserve [METRICS_CONTRACT.md](METRICS_CONTRACT.md), including saved unchecked working sets in volume/PRs, warm-up exclusions, independent record winners, exact-case Stats identity, case-insensitive suggestion matching, local-date buckets and first-encountered ties. Compare Kotlin outputs against the web fixture outputs before native Stats is accepted. Completed-only volume, canonical exercise IDs and changed chart grouping are future product changes, not migration fixes.

## Native session state and reliability

The native session model replaces DOM-backed controls completely. Own exercises, ordered sets, entered values, active position, notes, elapsed-time state, edit target and original saved revision in a durable model. Save edits through repository transactions; report success only after commit. Persist drafts during editing with an explicit flush boundary before navigation/finish; do not depend on Activity destruction callbacks or SavedStateHandle as the only durable store.

Use conditional writes/revisions for history, routine and settings editors so a stale screen cannot overwrite newer data. Saving a completed workout and consuming its draft must be atomic and idempotent. A failure to refresh the UI after commit must not invite a duplicate save. Test rotation, backgrounding, process death, force-stop/relaunch, failed writes and competing saves. Make intentional discard and conflict recovery understandable.

Routine-name uniqueness and capacity checks belong in the same transaction as routine writes, using the existing normalization semantics. Do not silently deduplicate imported history/routines or alter seed ownership. Seed only when policy permits and preserve user-owned records.

## Material Expressive while keeping identity

Extract the actual web colors, typography intent, spacing, shapes, icons and key screen hierarchy into a native token sheet before drawing screens. Starting web anchors include `src/styles/base.css` (`#070b14`, `#111827`, `#172033`, text `#f8fafc`) and `src/styles/motion.css` (coral `#ff6b45` / `#ff7a59`); inspect the full cascade before finalizing native tonal roles. Navy surfaces and coral emphasis remain the default brand palette; test accessible tonal variants in light/dark modes. Dynamic color is optional and must not silently replace the default identity.

Use Material 3 Expressive components, shape hierarchy and purposeful motion for the main workout action, navigation, grouped set controls and completion feedback. Keep repetitive set entry compact, legible and easy to reach with one hand. Use progressive disclosure for guides, filters, advanced settings and secondary analytics. Do not add decoration that slows logging or hides entered data.

Specify Home, active session, exercise detail/picker, routine editor, History, Stats and settings before implementing each feature. Include loading, empty, error, conflict, offline and resumed-session states. Use standard accessible touch targets, TalkBack descriptions, scalable text, visible focus/selection and reduced-motion behavior. Color alone must not convey completion or errors. Honor the animation/haptics preferences and keep historical RPE when entry is disabled.

## Feedback and backend boundary

The existing Worker is live, but its origin check and Turnstile flow were designed for the PWA. Do not fake a browser Origin header in a native HTTP client or assume the browser site key works in native UI. Before native feedback, choose and document a supported challenge flow (for example, a hosted browser feedback surface) or a deliberately designed native-compatible receiver contract with abuse protection. Keep the working PWA endpoint compatible throughout.

No Worker/Resend secrets go in the APK. Feedback has a durable, separately scoped queue and explicit retry/deadline behavior; removing local workout data must follow a documented policy for that queue. R2 screenshots remain private. Email delivery was deliberately not verified in the web handoff and is not a prerequisite for Android foundation work.

## Ordered implementation slices and acceptance gates

| Slice                             | Deliverable                                                                                                                                   | Gate before continuing                                                                                                                                                   |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| A. Foundation                     | Android build/CI, application ID/signing strategy, design tokens, navigation, Room schema/repositories, pure model and compatibility fixtures | Debug APK launches offline; theme/navigation work; database persists across relaunch; foundation has no fake completed features                                          |
| B. Migration bridge               | Legacy/current parsers, validation, import preview/transaction, compatible export and first-run setup                                         | Realistic anonymized fixtures preserve typed IDs, extensions, order, dates, RPE and zero/blank values; repeat/failure/future-version tests pass; PWA round trip passes   |
| C. Workout vertical slice         | Home → start/resume → add/custom/catalog exercise → set entry → finish → History                                                              | A real workout survives process death and saves once; draft consumption is atomic; data remains after signed APK upgrade                                                 |
| D. Feature parity                 | Routines/schedule, history edit/delete, completion details, PRs/Stats, settings/profile, backup reminders and education                       | Screen parity matrix complete; golden metric fixtures and conflict/import tests pass; fully usable offline                                                               |
| E. Native integration and release | Chosen feedback flow, accessibility, device/lifecycle QA, measured performance, release APK/build provenance                                  | End-to-end acceptance on Hector's S24 Ultra plus emulator/other supported configuration; no data-loss regressions; release checklist and remaining limitations published |

For every slice: identify touched contracts, implement the bounded slice, run relevant JVM/instrumented/Compose tests, produce an installable APK when meaningful, and update this checklist with commit, validation and limitations. Do not claim that web tests validate native behavior. Run relevant web/backend gates when shared contracts/assets/backend change; use documentation/link checks for documentation-only edits. Do not add unrelated cleanup just to fill a pass.

Before switching daily use, compare imported record totals and representative PR/volume/date results, complete a workout offline, restore a killed session, export/reimport, and install a same-signature upgrade without uninstalling. Keep the original PWA and exported backup until the user confirms native data and workflows. A later PWA retirement is a separate decision; APK installation does not migrate or remove PWA data.

## First implementation instruction

Start slice A and the shared compatibility fixtures needed for slice B. Record exact toolchain choices and the initial Room/interchange mapping before adding feature screens. Deliver one native vertical foundation that builds, installs, persists data and demonstrates the navy/coral Material Expressive direction. Do not restart PWA cleanup, promise full parity in that first APK, or change historical data semantics.

## Official implementation references

Consult current documentation and pin chosen versions at implementation time; this plan does not freeze library versions.

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Offline-first data architecture](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Material 3 and Expressive in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Room migration and upgrade testing](https://developer.android.com/training/data-storage/room/migrating-db-versions)
