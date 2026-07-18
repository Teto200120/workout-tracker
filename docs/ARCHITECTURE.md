# Architecture

## Runtime shape

The deployed app remains directly served HTML, CSS, images, and browser-native ES modules. There is no production build step or framework runtime.

`index.html` is the static application shell. It contains the screen containers, persistent navigation, dialogs, and form controls. Generated workout, History, Routine, Stats, and guidance markup is owned by the corresponding screen module.

`src/js/main.js` has two responsibilities:

1. Import and start `init()` from `router.js`.
2. Register `service-worker.js` after the window load event.

`src/js/router.js` owns startup order, primary and nested screen navigation, static event binding, and app-wide rendering. Startup opens IndexedDB, completes application-schema detection, validation, and migration, applies settings, and then evaluates the display-name gate while the static app shell remains hidden. A required name shows the one-screen onboarding surface; a usable saved name continues through routine seeding, event binding, selector preparation, app-wide rendering, and Home.

## Dependency direction

The intended dependency direction is:

```text
index.html and screen modules
             |
             v
application coordination and DOM components
             |
             v
pure domain modules and explicit storage interfaces
             |
             v
IndexedDB and localStorage browser APIs
```

The important boundaries are:

- `src/js/domain/` contains deterministic workout, schedule, and training rules. These modules do not read the DOM, browser storage, global app state, or the current clock.
- `src/js/schema/` contains version constants, canonical contract metadata, structured validators, non-mutating normalizers, schema errors, and the pure ordered migration registry. It has no DOM or storage access.
- `src/js/storage/` owns IndexedDB and localStorage access. Storage functions return values or reject/throw; they do not render, navigate, show toasts, trigger haptics, or open dialogs.
- `src/js/application/` coordinates domain and storage operations that span more than one data source. It does not render screens.
- `src/js/components/` contains reusable DOM-facing pieces such as icons and the routine selectors shared by multiple screens.
- `src/js/catalog/` contains the provider-neutral exercise-catalog contract, pure search/merge and name-resolution rules, reviewed aliases, Guide/preview adaptation, the Free Exercise DB adapter, and the static-asset loader. Provider fields stop at the adapter; catalog records and resolution results are disposable app-shell data rather than application storage.
- `src/js/screens/` owns rendering, user interaction, feature-local DOM state, and user-facing feedback.
- `router.js` coordinates startup, navigation, and app-wide rerendering.

No module may import Node-only code. Every production import must resolve from the static site root and must be added to the service-worker app shell.

## Domain modules

`domain/workout-metrics.js` owns:

- Epley estimated 1RM
- Set and workout volume
- Working-set and completed-set totals
- Workout duration
- Best-set selection
- Exercise and workout aggregation
- Historical PR comparison
- Completion summaries
- Workout saveability

Warm-up behavior intentionally matches the pre-refactor application: warm-up sets contribute no volume and are excluded from working-set totals and best-set selection. The existing completed-set total counts any set marked done, including a warm-up marked done.

`domain/schedule.js` owns Monday-first week resolution, day-plan resolution, weekly activity, streaks, and rolling recent-workout counts. Any time-dependent function receives the current date as an argument.

`domain/training-rules.js` owns exercise classification, routine tags, duration estimates, working-set filtering, exercise profiles, and progression-target rules. Settings are passed into these functions explicitly.

`domain/exercise-options.js` owns exercise-name trimming, case-insensitive keys and deduplication, local option construction, substring search, and canonical-name resolution. It receives routine, workout-history, and current-workout data explicitly and has no DOM or storage access. The catalog layer does not replace this source: local options remain independently constructed and win name collisions.

`domain/input-guardrails.js` owns beta-specific hard limits, warning thresholds, strict numeric parsing, Unicode-aware text validation, collection boundaries, date/time rules, workout/routine/settings validation, and pre-import size/complexity/data validation. It is provider-independent and pure: callers receive structured normalized values, errors, and warnings without DOM access, storage access, mutation, or automatic clamping. Historical records are validated for safety but are never rewritten merely to match a current UI boundary.

To add a pure workout calculation:

1. Add a named export to the most specific domain module.
2. Pass every input explicitly, including current time or settings when relevant.
3. Keep stored objects unchanged unless the existing behavior already requires mutation.
4. Add focused tests under `tests/unit/`.
5. Import the function into the screen or application module that needs it.

## Storage and application coordination

`storage/indexed-db.js` privately owns the open database connection. Its public interface is organized by record type:

- Workouts: `getWorkouts`, `saveWorkoutRecord`, `deleteWorkoutRecord`
- Routines: `getRoutines`, `saveRoutine`, `deleteRoutine`, `clearRoutines`
- Legacy weights: `getLegacyWeights`, `saveLegacyWeight`
- Lifecycle: `openDatabase`, `isDatabaseOpen`, `seedDefaultTemplates`
- App-wide operations: `getAllApplicationRecords`, `clearApplicationStores`, `importBackupRecords`, `replaceApplicationRecords`

The database name, version, store names, key paths, and indexes are unchanged. Record-specific writes validate schema 2 before opening a write request. `importBackupRecords` keeps one read-write transaction across all three stores, handles request and transaction errors, and explicitly aborts if synchronous or asynchronous insertion fails. `replaceApplicationRecords` uses the same multi-store transaction for startup migration and compensating rollback.

`storage/local.js` owns the existing settings, goals, draft, and backup-metadata keys plus `hector_workout_data_schema_version`. It provides typed-by-purpose reads and validated writes, raw snapshot/restore operations for cross-storage recovery, and application cleanup. Clear All Local Data removes the schema marker.

`application/data-schema.js` coordinates startup reads, the pure migration pipeline, transactional IndexedDB replacement, localStorage persistence, marker-last completion, and compensating rollback. Current-version startup validates without rewriting records.

`application/display-name.js` owns the onboarding-required decision and the single-flight validated settings write. Onboarding and Profile own DOM feedback; this application boundary preserves every unrelated setting and returns a name-only result.

`application/backup.js` builds canonical backup-file version 3 payloads, migrates supported legacy envelopes, validates before confirmation and writes, coordinates atomic IndexedDB import with localStorage restoration and compensating rollback, and clears application-owned data. File selection, downloads, confirmations, rendering, and toast messages remain in `screens/backup.js`.

`application/schedule.js` combines stored settings and the pure schedule rules for Home and Stats.

`application/action-coordinator.js` is the small single-flight primitive used by non-reentrant UI/application operations. A repeated call receives the current promise instead of starting another write or confirmation. Screens own disabled/loading state and correction workflow; the coordinator owns no UI or persistent state.

Workout and routine storage entry points apply the same semantic guardrails as defense in depth before opening a write. Storage still returns/rejects and never displays feedback. Backup restore runs file-size checks in the screen before reading, then application-level complexity, migration/schema, semantic-value, and duplicate-ID checks before the atomic replacement transaction.

To add or change a storage operation:

1. Add an explicit record-specific function in the relevant storage module.
2. Return the stored value or reject/throw an error; do not handle UI there.
3. If multiple stores or storage technologies must be coordinated, add a focused application function.
4. Handle confirmations, navigation, toasts, and rerendering in the calling screen or router.
5. Preserve keys, store names, IDs, and record formats unless a separate migration task authorizes a change.

## Screen responsibilities and state ownership

Screen modules may query and update their DOM, bind generated controls, call domain/data/application functions, and display user-facing feedback.

Feature state now has a specific owner:

- `active-workout.js`: editing workout ID, session timer handle, exercise-detail element/tab/render token, drag state, focus token, completion workout, and completion tags; Exercise Details asks the catalog boundary for an optional name match and builds catalog or generic Guide DOM without changing the active exercise
- `exercise-picker.js`: one dialog controller with per-open Active Workout or Routine context and add/replace mode; it owns local/catalog result state, collapsed-filter state/count, limited-versus-expanded local results, preview selection, async load token, focus return, and selection guard, and resolves only `{ name }`
- `today.js`: CTA mode/morph state and Home active-workout timer handle
- `routines.js`: routine draft exercises and editing routine ID; Browse and Change update only this unsaved name array until the established Save Routine action succeeds
- `onboarding.js`: the one-screen gate, retryable inline storage feedback, and reveal transition
- `profile.js`: display-name rendering plus the later edit/cancel/save UI
- `indexed-db.js`: open database connection
- Router and DOM: currently visible screen and rendered active exercise position
- localStorage draft: serialized recovery state, including the editing workout ID and active exercise index

Other modules use exported query or command functions such as `getEditingWorkoutId`, `setEditingWorkoutId`, and `completeActiveExerciseDetailSet`; they do not write those state variables directly.

The manual rest timer has no runtime state or module. Workout elapsed-time ownership remains split between `active-workout.js` for the live session and `today.js` for the Home resume state; saved duration remains a workout-metrics and stored-workout concern.

## Exercise catalog boundary

The catalog prototype is a static-data feature with a development-only ingestion step:

1. `scripts/refresh-exercise-catalog.mjs` obtains one official Free Exercise DB revision.
2. `free-exercise-db-adapter.js` converts provider records into the internal contract and reports malformed/duplicate records.
3. Strict validation writes `src/data/exercise-catalog.json` only after the full source is usable.
4. `catalog-loader.js` fetches the same-origin static asset asynchronously, retains it only in memory, and constructs one resolver index for that loaded snapshot.
5. `catalog-search.js` ranks and filters normalized data and merges it with independently collected local names.
6. `exercise-picker.js` renders a short local section, catalog results/status, compact filter disclosure, and contained summary preview.
7. Selection crosses into Active Workout or the routine draft as a provider-independent name only. Routine replace changes only the selected unsaved row; opening a routine never renames existing entries.
8. `exercise-catalog-resolver.js` can later map any displayed saved name to one unambiguous normalized-name or reviewed-alias match. It does not read the DOM or storage and deliberately performs no fuzzy matching.
9. `exercise-guide-adapter.js` converts a matched record into provider-neutral Guide data. `active-workout.js` inserts provider strings with `textContent`, or keeps the existing generic Guide when matching/instructions are unavailable.

The resolver result is render-only: saved local name → resolver → optional catalog match → Guide. There is no rename, history merge, Stats regrouping, alias persistence, or provider-field write. The browser never calls the upstream provider. The service worker owns offline caching of the catalog asset and production modules. IndexedDB, localStorage, backup export/import, application-schema migration, and workout normalization know nothing about the catalog. A failed, malformed, ambiguous, or instruction-less match therefore cannot block a workout, local option, custom exercise, or generic Guide.

The normalized contract, provider comparison, licensing evidence, refresh workflow, measurements, and deferred decisions are documented in [EXERCISE_CATALOG_SPIKE.md](EXERCISE_CATALOG_SPIKE.md).

## Remaining transitional globals

Eight global names intentionally remain:

- `$` and `all`: pervasive DOM query aliases used by every existing screen. Replacing them would be a large mechanical diff with little boundary benefit in this branch.
- `renderAll` and `switchScreen`: screen actions call router coordination. Importing the router back into those screens would create router-to-screen cycles.
- `closeTodayReview`, `showTodayView`, `stopTodayActiveElapsedTimer`, and `syncTodayFloatingCta`: Active Workout and Home still have a bidirectional view-transition relationship. These four commands are the narrow compatibility bridge that avoids a static Today/Active Workout import cycle while draft recovery and workout completion remain stable.

No mutable feature state, calculation, icon, settings accessor, or storage function is exposed through `globalThis`.

## Testing

Pure domain functions, catalog contracts/adapters/search/resolution/Guide adaptation, and the backup-structure validator are imported directly by Node's built-in test runner from `tests/unit/`. These tests cover formulas, warm-up handling, aggregation, PR comparison, schedule boundaries, explicit-time rules, saveability, progression targets, catalog normalization/ranking/failure behavior, conservative matching/ambiguity, preview/Guide shaping, and pre-write backup rejection without a browser.

Playwright remains responsible for storage integration, first-run gating and no-Home-flash behavior, display-name edit/retry/clear/backup flows, DOM rendering, navigation, Active Workout and Routine add/replace/cancel selection, compact local/catalog exercise-picker behavior, offline/failure paths, preview scroll ownership, catalog/generic Guide selection, exact/alias/history enrichment without rewrites, Exercise Details scroll ownership, draft recovery, workout save/update behavior, settings, backup compatibility and rollback, and representative larger histories.

Storage errors propagate to screens or application callers. Unit tests assert pure return values; Playwright asserts the user-visible result and checks page and console errors.

## Service-worker caching

`service-worker.js` manually lists the complete production app shell. When adding or renaming a production module:

1. Add or update its app-shell path.
2. Confirm the path returns HTTP 200 from the static server.
3. Bump the cache name once for the completed change set.
4. Verify an online load before testing cached/offline startup.

The current cache is `hector-workout-tracker-pwa-v17`. It includes the onboarding screen and application module, routine-draft domain helper, shared picker, every catalog production module—including aliases, resolver, and Guide adapter—and `src/data/exercise-catalog.json`; the network-only development refresh scripts are not production assets.

## Versioned data contracts

The full record contracts, ordered application schema 0-to-1-to-2 migration, version boundaries, startup recovery and onboarding sequence, backup compatibility rules, future-version behavior, and future-migration procedure are documented in [DATA_SCHEMA.md](DATA_SCHEMA.md). IndexedDB remains version 2, application data is schema 2, and new backup files remain version 3. The legacy `weights` store and backups without a `weights` array remain supported.
