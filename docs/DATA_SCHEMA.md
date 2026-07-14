# Persisted Data Schema

## Version boundaries

The tracker has three deliberately separate version concepts:

| Concept                         | Current value | Storage                                                | Responsibility                                                                        |
| ------------------------------- | ------------: | ------------------------------------------------------ | ------------------------------------------------------------------------------------- |
| IndexedDB database version      |             2 | `indexedDB.open("hector_workout_tracker_fresh_v1", 2)` | Creates or upgrades object stores and indexes only.                                   |
| Application data-schema version |             1 | localStorage key `hector_workout_data_schema_version`  | Describes the logical shape of all application-owned IndexedDB and localStorage data. |
| Backup-file version             |             3 | `backupFileVersion` in exported JSON                   | Describes the backup envelope and import/export contract.                             |

IndexedDB remains at version 2 because schema 1 needs no new store or index. The application schema marker is not stored on individual records. A single marker is sufficient because startup reads, validates, and migrates the full application snapshot before any screen renders.

A missing application marker means legacy application schema 0. A marker greater than 1 is a future version and stops startup without writing. Invalid, negative, fractional, or non-numeric markers also stop startup.

Backup imports support unversioned envelopes as backup version 0, historical version 1, and the prior exported version 2. New exports use version 3. Version 3 requires both `backupFileVersion: 3` and `applicationSchemaVersion: 1`. A higher backup or application-schema version is rejected as newer data rather than guessed at or downgraded.

## Canonical application snapshot

Schema coordination uses this in-memory shape:

```js
{
  workouts: [],
  legacyWeights: [],
  routines: [],
  settings: null,
  goals: null,
  draft: null,
  backupMeta: null
}
```

The arrays are always present. `null` means that the corresponding localStorage key is absent. Unknown fields on records, nested exercises, sets, settings, goals, and backup metadata are retained when they are safe structured-clone/JSON data. Known fields are validated and normalized; unknown fields do not override normalized known fields.

## Set

| Field    | Required | Canonical value                            |
| -------- | -------- | ------------------------------------------ |
| `weight` | Yes      | Numeric string or `""`. `"0"` is valid.    |
| `reps`   | Yes      | Numeric string or `""`. `"0"` is valid.    |
| `rpe`    | Yes      | Numeric string from 0 through 10, or `""`. |
| `done`   | Yes      | Boolean completion flag.                   |
| `warmup` | Yes      | Boolean warm-up flag.                      |

The app has no canonical set ID today. Set order is the array order. Numeric legacy values become strings because the active-workout form is the canonical producer and already persists these fields as strings. Missing legacy flags default to `false`; missing numeric inputs default to `""`.

## Exercise

| Field   | Required | Canonical value                                                             |
| ------- | -------- | --------------------------------------------------------------------------- |
| `name`  | Yes      | Trimmed string. An empty name remains valid for historical unnamed entries. |
| `notes` | Yes      | String; `""` is valid.                                                      |
| `sets`  | Yes      | Ordered set array; an empty array is valid.                                 |

Schema 1 has no exercise-catalog ID, image URL, equipment taxonomy, muscle taxonomy, API source, or remote metadata. Unknown existing metadata is retained but is not interpreted as a catalog identity.

## Workout

| Field             | Required | Canonical value                                                                     |
| ----------------- | -------- | ----------------------------------------------------------------------------------- |
| `id`              | Yes      | Non-empty string or finite numeric compatibility ID. App-generated IDs are strings. |
| `date`            | Yes      | Local calendar date in `YYYY-MM-DD`.                                                |
| `type`            | Yes      | Workout/routine name string; an empty historical value is retained.                 |
| `startTime`       | Yes      | `HH:MM` or `""`.                                                                    |
| `endTime`         | Yes      | `HH:MM` or `""`.                                                                    |
| `durationMinutes` | No       | Non-negative finite number. Zero is valid.                                          |
| `notes`           | Yes      | String; `""` is valid.                                                              |
| `tags`            | Yes      | String array; an empty array is valid.                                              |
| `exercises`       | Yes      | Ordered canonical exercise array.                                                   |
| `createdAt`       | Yes      | ISO timestamp or `""` for a legacy record with no timestamp.                        |

Set `done` flags are the persisted completion data. Workout completion summaries, volume, records, and duration fallback calculations remain derived domain behavior and are not added to storage. Migration never changes the workout date, invents an ID, recalculates duration, or stores derived personal records.

## Routine/template

| Field       | Required | Canonical value                                                          |
| ----------- | -------- | ------------------------------------------------------------------------ |
| `id`        | Yes      | Non-empty string or finite numeric compatibility ID.                     |
| `name`      | Yes      | Trimmed string.                                                          |
| `exercises` | Yes      | Ordered array of trimmed exercise-name strings. An empty array is valid. |
| `createdAt` | Yes      | ISO timestamp or `""` for legacy data.                                   |
| `updatedAt` | Yes      | ISO timestamp or `""` for legacy data.                                   |

Exercise order is represented only by the array. No separate order values are added.

## Settings

The canonical settings object includes every current behavior setting:

- `schedule`: entries `0` through `6`, each with `kind` (`gym`, `rest`, or `soccer`) and a routine-name string.
- `defaultWeightJump`.
- `compoundMin` and `compoundMax`.
- `pullMin` and `pullMax`.
- `isolationMin` and `isolationMax`.
- `generalMin` and `generalMax`.
- `rpeAware`.
- `haptics`.
- `animations`.

Numeric settings are finite numbers. Rep-range values are at least 1 and each maximum is greater than or equal to its minimum. The weight jump is greater than zero. Flags are booleans. Missing legacy fields and schedule days receive the existing values from `DEFAULT_APP_SETTINGS`. Convertible legacy number and boolean strings are normalized.

## Goals

`weeklyGoal` is the required positive integer used by current Stats behavior. Historical `targetWeight` remains an optional compatibility-only field and may be numeric, an empty string, or `null`; the removed body-weight UI is not restored. Other safe unknown fields are retained.

## Draft

A draft contains the Workout contract plus:

| Field                 | Required | Canonical value                         |
| --------------------- | -------- | --------------------------------------- |
| `editingWorkoutId`    | Yes      | `null` or an existing workout ID.       |
| `activeExerciseIndex` | Yes      | Non-negative integer.                   |
| `savedAt`             | Yes      | ISO timestamp or `""` for an old draft. |

`startTime`, `endTime`, and optional `durationMinutes` are the existing elapsed-time state. Drafts may contain empty set values because active-workout recovery intentionally preserves incomplete rows. Drafts remain excluded from backups.

## Backup metadata

`backupMeta` is an object whose current known field is optional `lastExportedAt`, stored as an ISO timestamp, `""`, or `null`. Unknown safe metadata is retained.

## Legacy weight record

The `weights` store remains for compatibility only:

| Field       | Required | Canonical value                                      |
| ----------- | -------- | ---------------------------------------------------- |
| `id`        | Yes      | Non-empty string or finite numeric compatibility ID. |
| `date`      | Yes      | `YYYY-MM-DD`.                                        |
| `weight`    | Yes      | Non-negative finite number. Zero is preserved.       |
| `notes`     | Yes      | String; `""` is valid.                               |
| `createdAt` | Yes      | ISO timestamp or `""` for legacy data.               |

Legacy numeric strings become numbers so the compatibility record has one stable numeric representation. The store, database version, export field, and import support remain intact.

## Backup envelope version 3

New exports contain:

```js
{
  app: "Hector's Workout Tracker",
  backupFileVersion: 3,
  applicationSchemaVersion: 1,
  database: "hector_workout_tracker_fresh_v1",
  exportedAt: "ISO timestamp",
  workouts: [],
  weights: [],
  templates: [],
  goals: {},
  settings: {},
  backupMeta: {}
}
```

`workouts` is required. `weights` and `templates` remain optional on legacy imports. `goals`, `settings`, and `backupMeta` are restored only when present. Drafts are not exported. Legacy envelopes use the former generic `version` field or no version; version 3 deliberately uses the unambiguous `backupFileVersion` name.

## Validation and normalization

`src/js/schema/validators.js` is pure. Validators return `{ valid, errors }`, where each error has a stable path, code, and message. Storage-boundary assertion helpers convert these results into `DataSchemaError` instances. Validation has no DOM, storage, navigation, toast, haptic, clock, or dialog access.

Canonical validation checks required objects and arrays, nested record paths, dates, times, timestamps, numeric representations, ranges, booleans, and IDs. Missing IDs are invalid. Backup validation can deliberately defer `id: null` to IndexedDB so the separate key-path constraint and full transaction rollback remain tested.

`src/js/schema/normalize.js` is pure and non-mutating. It deep-clones safe persisted values, preserves IDs and dates, preserves set and exercise order, fills only existing application defaults, normalizes known scalar/boolean fields, and retains unknown safe fields. It never generates an ID, changes a workout date, recalculates duration, computes a record, or adds exercise-catalog data.

## Migration 0 to 1

Schema 0 is the real unversioned data produced by the app before this change. The one registered migration:

1. Validates the legacy snapshot with leniency only for documented missing/defaulted fields and convertible historic scalar forms.
2. Adds missing set flags and scalar fields.
3. Adds missing exercise notes and set arrays.
4. Adds missing workout times, notes, tags, exercise arrays, and empty legacy timestamps without recalculating history.
5. Adds missing routine arrays and timestamps.
6. Applies existing settings and weekly-goal defaults.
7. Adds missing draft recovery metadata.
8. Normalizes legacy weight numeric values.
9. Validates the complete schema 1 output.

There are no invented intermediate application versions. The migration registry maps each source version to exactly the next version. Current schema 1 data is validated and cloned by the pure pipeline but is not rewritten during startup.

## Startup and recovery

Startup order is:

1. Open IndexedDB version 2.
2. Detect the application marker. Future or invalid markers stop immediately.
3. Read all three stores and raw application-owned localStorage values.
4. Parse and validate the complete source snapshot in memory.
5. Run each pure migration and validate its output.
6. Replace all affected IndexedDB records in one read-write transaction. Clear and put requests are queued only after the full migrated snapshot exists; any synchronous or request failure aborts the transaction.
7. Write canonical localStorage values.
8. Write `hector_workout_data_schema_version` last.
9. Seed default routines only after schema handling succeeds.
10. Bind and render the application.

If a persistence step fails, the coordinator restores the original IndexedDB records with a compensating multi-store transaction when needed, restores the exact raw localStorage snapshot, leaves the schema marker unchanged, and throws a structured error. Startup does not render. The screen shows a concise recovery message and the console receives the full technical error. The next startup can retry.

IndexedDB and localStorage cannot share a native transaction. The implementation minimizes that boundary by preparing everything first, using one IndexedDB transaction, snapshotting exact localStorage strings, writing the marker last, and compensating caught failures. A browser or device process termination in the narrow interval between storage technologies cannot be synchronously compensated; the absent old marker and idempotent 0-to-1 migration make the next startup safe and non-duplicating.

## Backup restore safety

Backup detection, validation, application-schema migration, normalization, and post-migration validation complete before confirmation or writes. Imported IndexedDB records are merged by ID exactly as before; unrelated existing entries stay. Workouts, weights, and routines share one transaction with explicit abort handling.

Before import, the app snapshots every IndexedDB store and exact application localStorage strings. If a caught failure occurs after the import transaction, a compensating replacement restores the original stores and raw localStorage. The schema marker is written only after imported records, default-routine handling, and supplied localStorage values succeed. Future files and malformed structures are never written.

## Adding a future schema migration

1. Increase `CURRENT_APPLICATION_SCHEMA_VERSION` only for a real logical contract change.
2. Add the next pure migration to `APPLICATION_MIGRATIONS`; do not skip versions.
3. Define legacy input validation for the actual prior canonical contract.
4. Preserve unknown safe fields, IDs, dates, and array order unless the product change explicitly requires otherwise.
5. Validate the output as the new canonical version.
6. Add direct version, validation, normalization, migration, startup failure/retry, backup, and browser fixtures.
7. Increase the backup-file version only when the envelope/import-export contract changes.
8. Increase IndexedDB `DB_VERSION` only if a store or index changes, with separate fresh-install and upgrade tests.

## Compatibility guarantees and deferred work

Schema 1 preserves the existing database name, IndexedDB version, stores, indexes, localStorage data keys, workout/routine IDs, workout dates, ordering, legacy weights, backup merge semantics, optional legacy backup weights, current draft behavior, and visible workout workflows. Safe unknown record fields survive migration and new backup round trips.

The exercise-catalog prototype does not change these contracts. `src/data/exercise-catalog.json` is versioned disposable application content cached by the service worker, not application-owned user data. It is never read or written by IndexedDB/localStorage interfaces, schema migration, normalization, backup export/import, or Clear All Local Data. Selecting a catalog entry passes only its canonical name into the existing Exercise contract, so drafts and saved workouts remain `{ name, notes, sets }`.

Schema 1 deliberately adds no catalog ID, provider/source, remote image, equipment taxonomy, muscle taxonomy, instruction/version reference, sync status, catalog/custom discriminator, account ownership, or Android-only field. Custom exercises and catalog-derived names are intentionally indistinguishable after selection. A future requirement for durable catalog identity must define compatibility and custom-exercise rules as a separate application-schema migration. This document remains the platform-neutral user-data blueprint for that work and an eventual Android implementation; the runtime catalog is not an Android storage migration.
