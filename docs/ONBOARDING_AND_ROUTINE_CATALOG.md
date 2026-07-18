# Onboarding and Routine Catalog Selection

## First launch and display name

Startup opens IndexedDB, completes application-schema migration, and loads settings before deciding which UI to reveal. The app shell is hidden in the static HTML, so a new or cleared installation cannot flash Home before the gate runs.

If `settings.displayName` is `null`, missing after migration, or otherwise not a usable visible name, the app shows one onboarding screen. It asks only for a display name and keeps the workout tracker local-first. A successful save reveals Home; a failed browser-storage write leaves the name in the input, shows an inline retryable error, and does not enter the app. Repeated submissions share one in-flight save.

Display names:

- are trimmed and limited to 80 Unicode code points;
- may include accents, non-Latin scripts, apostrophes, hyphens, and emoji;
- reject blank, invisible-only, control-character, non-string, and over-limit values;
- are inserted into Home, Profile, and Settings with text APIs, so markup-like text is displayed literally;
- are stored in the existing settings localStorage object and may be edited later in Settings without affecting other settings.

Reset Settings preserves the display name. Clear All Local Data removes settings and immediately returns to onboarding. A current backup restores its saved display name. A supported old backup without one imports its compatible data and then requires onboarding. Invalid imported names are rejected before any write; valid boundary whitespace is normalized.

## Shared exercise picker

`components/exercise-picker.js` is the single picker implementation for these contexts:

| Context         | Mode    | Result                                                |
| --------------- | ------- | ----------------------------------------------------- |
| Active Workout  | Add     | `{ name }` passed to the existing workout add command |
| Routine builder | Add     | `{ name }` appended to the unsaved routine draft      |
| Routine builder | Replace | `{ name }` replaces only the selected draft row       |

The result is provider-independent and name-only. No catalog ID, source metadata, image, equipment, muscle, or custom/catalog discriminator enters a draft, routine, workout, IndexedDB, localStorage, or backup.

Routine editing keeps the established builder and explicit Save Routine action. Browse opens the shared local/catalog/custom picker; Change opens it in replace mode. Cancel changes nothing and returns focus to the launching control. Adding or replacing changes only the in-memory draft until Save Routine succeeds. Existing names are not resolved, renamed, or enriched merely because a routine is opened. Normalized duplicates retain the existing confirmation behavior, including an intentional duplicate when accepted.

Local options remain usable while the static catalog loads or if it fails. Custom names remain available in every context. The service worker caches the same catalog and picker modules for offline Active Workout and routine-building use.

## Version and compatibility decisions

- IndexedDB database version remains `2`; no store or index changed.
- Application data-schema version is `2`; schema `1` migrates by adding `settings.displayName`, using `null` when no name existed.
- Backup-file version remains `3`; the envelope already declares its independent `applicationSchemaVersion`.
- Service-worker cache is `hector-workout-tracker-pwa-v18`.
- Existing database, localStorage, package, and cache prefixes containing `hector` remain compatibility identifiers only. User-facing product copy is generic.

The focused automated coverage is in `tests/unit/display-name.test.js`, `tests/unit/exercise-picker-context.test.js`, `tests/unit/routine-draft.test.js`, `tests/unit/schema-migrations.test.js`, `tests/unit/backup-validation.test.js`, and `tests/e2e/onboarding-routine-catalog.spec.js`. Backup, migration, offline, and existing picker regressions remain covered by their established suites.
