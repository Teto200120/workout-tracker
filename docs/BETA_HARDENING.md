# Beta Hardening

## Outcome

This pass treats ordinary typing mistakes, pasted values, rapid taps, interrupted actions, malformed backups, and local-storage failures as expected beta conditions. Confirmed failure paths received focused validation or coordination without changing a persisted record shape.

The guardrail policy is:

1. Preserve the user's raw draft value while it is incomplete or invalid.
2. Validate when the field is committed, a set is completed, or a record is saved.
3. Hard-block structurally invalid or technically dangerous values.
4. Ask for confirmation when a valid value is merely unusual.
5. Keep the affected action retryable after failure.

IndexedDB remains version 2, the application schema remains version 1, backup files remain version 3, and the service-worker cache remains `hector-workout-tracker-pwa-v16`.

## Risk matrix

| Surface                                | Pre-hardening failure path                                                                        | Could reach storage?                                | User feedback before this pass                           | Repeat-action exposure                                                       | Final treatment                                                                            |
| -------------------------------------- | ------------------------------------------------------------------------------------------------- | --------------------------------------------------- | -------------------------------------------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Set weight, repetitions, and RPE       | Manual values could be stripped, clamped, or accepted without a generous technical ceiling        | Yes                                                 | Partial native constraints; some values changed silently | Increment controls could be tapped indefinitely                              | High: inline hard blocks, warning thresholds, collection boundary                          |
| Workout names, notes, dates, and times | Required names and dates could default or pass late; text had no finite limit                     | Yes                                                 | Mostly toast/confirm at save                             | Top and bottom save actions could overlap                                    | High: field validation, date/time rules, single-flight save                                |
| Workout exercise/set collections       | Rapid taps could create very large drafts                                                         | Yes                                                 | No limit message                                         | Add Set/Add Exercise were repeatable                                         | High: 200 sets/exercise and 100 exercises/workout                                          |
| Completion dialog                      | Long custom text and repeated Done could overlap persistence                                      | Yes                                                 | No text-length error                                     | Repeated submission possible                                                 | High: validation, focus/scroll lifecycle, single-flight completion                         |
| Routines                               | Names and exercise lists were unbounded; duplicate save/delete/reset actions could overlap        | Yes                                                 | Basic required checks                                    | Save and destructive actions were reentrant                                  | High: text/collection limits and action coordination                                       |
| Catalog picker and Guides              | Long search, repeated selection, or malformed/extreme provider records could stress rendering     | Catalog data is not persisted; selected name can be | Safe fallback existed                                    | Selection guard existed; open taps were idempotent through one native dialog | Medium: search/result/contract limits and existing single-selection guard retained         |
| Settings and weekly goal               | `Number` conversion and normalization could silently replace or clamp bad input                   | Yes                                                 | Native minimums only; save could appear successful       | Saves/resets could overlap                                                   | High: semantic validation and single-flight storage actions                                |
| Backup import                          | File size, nesting, duplicate IDs, and domain-extreme values were unchecked before parsing/writes | Yes                                                 | Schema errors existed, but not practical abuse limits    | Imports could overlap                                                        | Critical: pre-parse size limit, complexity/domain validation, single-flight atomic restore |
| Draft/local storage                    | A quota or browser storage exception could escape a UI event                                      | Draft only                                          | No durable correction message                            | Autosave could repeat the exception                                          | High: preserve visible inputs, skip invalid snapshots, recoverable failure messaging       |
| Navigation and dialogs                 | Reload/back/rapid open-close could expose stale dialog or focus state                             | Indirectly                                          | Native dialog behavior plus existing routes              | Multiple opens and quick tabs were possible                                  | Medium: one dialog, focus trap/return, body lock, reload-safe hidden state                 |
| Offline/catalog/service worker         | Missing assets could remove enrichment or create mixed-version modules                            | No catalog persistence                              | Generic Guide fallback existed                           | Repeated filtering only                                                      | Medium: bounded catalog contract; app-shell path coverage; v16 retained                    |

## Input and action inventory

The “storage boundary” column describes whether an invalid value can become canonical data after this pass. Schema validation remains a separate compatibility layer; the domain guardrails add beta-specific semantics without changing schema 1.

### Active workout

| Input or action         | Accepted values and HTML behavior                                                                                            | Domain/application guardrail                                                                          | Storage boundary and feedback                                             | Repeat behavior                                                         | Classification                                                    |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Weight/load             | Blank, `0`, negative zero, or decimal from 0 through 10,000; up to two decimal places; decimal comma is normalized on commit | Finite plain-number syntax; no scientific notation; warning above 2,000                               | Invalid raw text remains visible and cannot complete/save; inline message | Steppers stop at the hard maximum and do not replace invalid typed text | Hard block outside range; warning above 2,000; otherwise accepted |
| Repetitions             | Blank or integer from 0 through 10,000                                                                                       | Reject decimal, negative, non-finite, scientific notation, and mixed text; warning above 500          | Invalid raw text remains correctable and cannot complete/save             | Increment/decrement honors the boundary                                 | Hard block outside range; warning above 500                       |
| RPE                     | Blank or 1 through 10 in 0.5 increments; imported historical `0` remains compatible                                          | Reject negative, 0 for new entry, above 10, unsupported precision, and scientific notation            | Inline message; no silent conversion of 11 to 10                          | Stepper uses 0.5 increments and stops at 10                             | Hard block; blank accepted                                        |
| Warm-up                 | Boolean checkbox                                                                                                             | Existing meaning retained                                                                             | Stored only as the existing boolean field                                 | Repeated toggle is idempotent                                           | Accepted                                                          |
| Set completion          | Existing checkbox/action semantics                                                                                           | Validates weight, repetitions, and RPE at completion                                                  | Invalid row stays in place and receives field feedback                    | Completion action does not create a second row                          | Hard block on invalid committed set                               |
| Add Set                 | Existing set defaults                                                                                                        | Maximum 200 sets per exercise; warning during save above 50                                           | Existing sets remain; clear toast at maximum                              | Hundreds of rapid clicks stop deterministically                         | Hard block at 200; warning above 50                               |
| Remove Last Set         | Existing action and minimum-row behavior retained                                                                            | No new destructive rewrite                                                                            | Removes only the intended current draft row                               | Sequential clicks act on current state                                  | Accepted with existing confirmation/state behavior                |
| Add Exercise            | Local/custom/catalog choice                                                                                                  | Maximum 100 exercises/workout; exact duplicate name asks for confirmation                             | Limit does not alter existing exercises                                   | One native picker; catalog selection is single-flight                   | Hard block at 100; warning above 50; duplicate warning            |
| Remove/reorder exercise | Existing controls and order                                                                                                  | No schema change; draft validation reruns before save                                                 | Only current draft changes until save                                     | Existing action state retained                                          | Accepted                                                          |
| Exercise name           | Required after trimming; Unicode preserved; 120 Unicode code points                                                          | Whitespace/invisible-only, control characters, and over-limit names rejected                          | Inline error; invalid draft is not written as a canonical workout         | Duplicate exact normalized names warn rather than merge                 | Hard block; duplicate warning                                     |
| Exercise notes          | Optional multiline text; 2,000 Unicode code points                                                                           | Control characters rejected; line breaks, emoji, punctuation, and markup-like text accepted literally | Inline error; never silently truncated                                    | Input event autosave skips invalid canonical snapshot                   | Hard block only at technical boundary                             |
| Workout notes           | Optional multiline text; 4,000 Unicode code points                                                                           | Same safe text rules as exercise notes                                                                | Inline error and preserved text                                           | Save remains retryable                                                  | Hard block only at technical boundary                             |
| Workout type/name       | Required selected routine or editable existing value; 80 Unicode code points                                                 | Trim, required, safe text, length validation                                                          | Cannot save invalid value                                                 | Save coordinator covers top/bottom actions                              | Hard block                                                        |
| Workout date            | Required exact `YYYY-MM-DD` real calendar date                                                                               | Leap days supported; future dates warn                                                                | Invalid/blank value no longer defaults silently to today                  | Save remains available after correction                                 | Hard block malformed; warning future; historical accepted         |
| Start/end time          | Optional exact `HH:MM` 24-hour values                                                                                        | Malformed time rejected; end earlier than start is treated as overnight only after confirmation       | Stored existing strings and calculated duration only after validation     | One save outcome                                                        | Hard block malformed; overnight warning                           |
| Save new workout        | Existing create shape                                                                                                        | Full workout validation and single-flight coordination                                                | At most one IndexedDB write and one completion outcome                    | Top/bottom/rapid taps share one promise                                 | Duplicate in-progress action ignored                              |
| Edit workout            | Existing ID and update behavior                                                                                              | Same validation and coordinator as create                                                             | Editing cannot race into a second create                                  | One in-flight update                                                    | Duplicate in-progress action ignored                              |
| Completion tags         | Existing predefined tags; maximum 32 tags and 80 characters/tag at the domain boundary                                       | Required safe text for selected tags                                                                  | Invalid imported/new tag collections cannot be stored                     | Repeated toggle keeps set semantics                                     | Hard block only at technical boundary                             |
| Custom completion note  | Optional multiline; 1,000 Unicode code points                                                                                | Safe text/control/length rules                                                                        | Error remains in dialog; work is not lost                                 | Done is single-flight; controls re-enable on failure                    | Hard block only at technical boundary                             |

### Routines

| Input or action       | Existing and new constraints                                                       | Storage/error behavior                                         | Repeat behavior                            | Classification                                  |
| --------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------ | ----------------------------------------------- |
| Routine name          | Required, trimmed, Unicode, maximum 80; case/space-insensitive duplicate detection | Inline error or duplicate warning before IndexedDB             | Save is single-flight                      | Hard block invalid; warning duplicate           |
| Exercise names        | Required, trimmed, Unicode, maximum 120 each                                       | Inline error; no silent truncation                             | Add remains usable after correction        | Hard block invalid                              |
| Exercise count        | Maximum 100; warning above 50                                                      | Draft list is preserved at boundary                            | Rapid adds stop at maximum                 | Hard block/warning                              |
| Duplicate exercises   | Normalized case/space duplicates are not silently merged                           | Confirmation allows intentional repeats                        | Each accepted add is explicit              | Soft warning                                    |
| Add/remove/reorder    | Existing list behavior retained                                                    | Only the current routine draft changes                         | Current-state actions remain deterministic | Accepted                                        |
| Save/edit             | Full routine validation before `saveRoutine`                                       | Storage errors retain entered values and show a clear message  | One in-flight promise                      | Hard block invalid/duplicate in-progress action |
| Delete/reset defaults | Existing confirmations retained                                                    | Failure reports no success; stored routines remain recoverable | Delete/reset are single-flight             | Duplicate in-progress action ignored            |
| Start routine         | Existing selected routine                                                          | Start action coordinated                                       | Rapid taps open one session                | Duplicate in-progress action ignored            |

### Add Exercise, catalog, and Guides

| Input or action                | Guardrail                                                                                                                        | Persistence/rendering                                                                                         | Classification                                           |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| Search text                    | Maximum 200 Unicode code points; empty and ordinary punctuation accepted                                                         | Search is local; over-limit query shows an inline/visible message and does not render an unbounded result set | Hard block at 200                                        |
| Filters                        | Values come from bounded provider-neutral metadata lists                                                                         | No provider value is persisted                                                                                | Accepted enumerated choice                               |
| Custom name                    | Same required/trim/120-character rule as workout exercise names                                                                  | Only canonical name, empty notes, and sets use the existing shape                                             | Hard block invalid                                       |
| Duplicate custom/existing name | Existing case-insensitive local option resolution retained; exact workout duplicate warns                                        | No automatic merge or rename                                                                                  | Soft warning                                             |
| Catalog selection              | Existing `selecting` guard retained; add result capped to 60 visible catalog rows                                                | Only the selected exercise name enters the workout                                                            | Duplicate in-progress action ignored                     |
| Catalog failure                | Existing local/custom fallback retained                                                                                          | Failure does not block workout use or create provider fields                                                  | Accepted fallback                                        |
| Alias resolution               | Reviewed conservative aliases only                                                                                               | Enriches rendered Guide; never rewrites the stored name                                                       | Accepted when exact/reviewed; generic fallback otherwise |
| Missing metadata/instructions  | Provider-neutral contract supplies generic/absent-state rendering                                                                | Safe text APIs; no raw provider HTML                                                                          | Accepted fallback                                        |
| Extreme catalog data           | At most 10,000 records, 100 instructions/record, 2,000 characters/instruction, 100 list items, and 500-character metadata fields | Malformed/extreme asset fails closed to local/custom behavior; source is not mutated                          | Hard block at contract boundary                          |

### Settings and goals

| Input                                               | Accepted values                                                | Storage/error behavior                                              | Classification                       |
| --------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------ |
| Seven schedule kinds                                | Existing `gym`, `rest`, or `soccer` selects                    | Values originate from options and schema validation                 | Accepted enumerated choice           |
| Seven scheduled routines                            | Existing stored routine options                                | Selected existing name is stored in the unchanged settings shape    | Accepted enumerated choice           |
| Default load jump                                   | 0.5 through 1,000, up to two decimals                          | Invalid settings are not persisted; focused inline error            | Hard block                           |
| Compound/pull/isolation/general rep minimum/maximum | Integers 1 through 1,000; each minimum must not exceed maximum | No silent normalization or replacement with defaults                | Hard block; warning above 100        |
| RPE-aware suggestions                               | Boolean                                                        | Existing setting retained                                           | Accepted                             |
| Weekly goal                                         | Integer 1 through 100                                          | Inline message; failed localStorage write reports failure           | Hard block; warning above 14         |
| Haptics and animations                              | Boolean                                                        | Existing behavior retained                                          | Accepted                             |
| Save/reset                                          | Semantic validation and single-flight coordination             | Failure leaves current values available and does not report success | Duplicate in-progress action ignored |

### Backup and migration

| Input/action                 | Validation order and behavior                                                                                                             | Corruption protection                                                                                | Classification                       |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------ |
| File selection/cancel        | Cancel is a no-op; empty file is rejected                                                                                                 | No parsing or writes                                                                                 | Accepted cancel; hard-block empty    |
| File size                    | Reject above 25 MiB before `file.text()` or JSON parsing                                                                                  | Prevents an obvious memory/freeze path while leaving ample long-term history capacity                | Hard block                           |
| JSON/envelope                | Non-JSON, array top level, missing required fields, invalid/future versions, and wrong field types use existing version/schema validation | No write begins                                                                                      | Hard block                           |
| Complexity                   | Maximum depth 20 and 500,000 visited values                                                                                               | Checked before migration and record writes                                                           | Hard block                           |
| Nested records               | Existing schema validators plus beta text/numeric/collection semantics                                                                    | Malformed sets, exercises, routines, settings, and goals cannot reach stores                         | Hard block                           |
| Extreme strings/numbers      | Same canonical limits as new user input                                                                                                   | Prevents an import from bypassing UI rules                                                           | Hard block                           |
| Duplicate IDs                | Duplicate non-null IDs within workouts, routines, or legacy weights rejected                                                              | Avoids order-dependent overwrite                                                                     | Hard block                           |
| Null IDs                     | Existing compatibility path is retained                                                                                                   | IndexedDB rejects the invalid key during the single transaction; rollback preserves original records | Atomic failure, no rewrite           |
| Import with active draft     | Explicit confirmation identifies the unsaved draft                                                                                        | Draft localStorage is preserved by import                                                            | Soft warning                         |
| Repeated import              | One single-flight import/confirmation                                                                                                     | No overlapping clear/write transactions or duplicate success                                         | Duplicate in-progress action ignored |
| Storage/localStorage failure | Existing records and local snapshot are restored where a write already committed                                                          | Technical cause logged without workout payload; user sees retryable failure                          | Hard failure with rollback           |
| Restore/reload               | Successful v2/v3 import writes the existing schema marker last and reloads normally                                                       | No version or record-shape change                                                                    | Accepted compatible path             |

### Navigation and lifecycle

| Scenario                              | Finding/treatment                                                                                                                                                                                          |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Browser or Android-style Back         | Existing Today/Active Workout and Exercise Details back behavior remains. Browser-history and dialog dismissal coverage found no reason to redesign routing. Physical Android Back remains a device check. |
| Reload during active workout          | Valid draft restores. Invalid raw field text remains on screen but is deliberately not promoted to a canonical draft snapshot.                                                                             |
| Reload with modal open                | Native dialog open state is not persisted; the active workout draft remains recoverable.                                                                                                                   |
| Reload during save/import             | Single-flight protects same-page overlap; IndexedDB transaction atomicity and backup rollback protect interrupted writes. Exact process-kill timing remains a manual installed-PWA check.                  |
| Repeated navigation/open taps         | Screen changes are idempotent; one native Add Exercise dialog is used. Catalog preview selection has one in-flight outcome.                                                                                |
| Dialog focus/scroll                   | Completion and exercise-picker flows trap/restore focus, lock document scrolling, preserve visible focus, and expose existing labels.                                                                      |
| Navigating away with unsaved data     | Existing active-draft behavior remains; backup import adds a specific unsaved-draft confirmation.                                                                                                          |
| Offline startup/service-worker update | Existing app-shell and generic catalog fallback remain; both new production modules are cached in v16.                                                                                                     |
| Current and legacy startup            | Existing schema 0-to-1 migration, marker-last behavior, and current-version no-rewrite path remain unchanged.                                                                                              |

## Exact limits and rationale

### Numeric limits

| Value                      | Hard range                         | Warning                                        | Rationale                                                                                                                                          |
| -------------------------- | ---------------------------------- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Weight/load                | 0–10,000, two decimals             | Above 2,000                                    | 10,000 accommodates plates, machines, sleds, assisted/bodyweight conventions, and non-pound units while rejecting obvious entries such as 300,000. |
| Repetitions                | Integer 0–10,000                   | Above 500                                      | Supports endurance, rehab, timed conversions, and unusual challenges without allowing accidental six-digit counts.                                 |
| RPE                        | Blank or 1–10 in 0.5 steps         | None                                           | Matches the app's established 10-point scale. Historical stored/imported zero is read unchanged for compatibility.                                 |
| Default load jump          | 0.5–1,000, two decimals            | None                                           | Keeps useful small increments and broad unit conventions while preventing zero/negative or runaway steppers.                                       |
| Weekly goal                | Integer 1–100                      | Above 14                                       | Allows multiple sessions per day but calls attention to a goal beyond two daily sessions.                                                          |
| Rep-range bounds           | Integer 1–1,000; minimum ≤ maximum | Above 100                                      | Supports high-repetition programming while preventing malformed recommendation ranges.                                                             |
| Calculated/stored duration | 0–1,440 minutes                    | Overnight confirmation when end precedes start | One day is the technical ceiling for a single calculated session; no historical record is rewritten.                                               |

Warnings are confirmations at save/commit, not toasts while typing. A warning never clamps or changes the entered value.

### Text limits

| Text                   | Maximum Unicode code points | Notes                                                                                  |
| ---------------------- | --------------------------- | -------------------------------------------------------------------------------------- |
| Exercise name          | 120                         | Required/trimmed; supports Unicode; protects cards, labels, search, and announcements. |
| Routine/workout type   | 80                          | Required/trimmed; keeps selectors and compact headers usable.                          |
| Workout notes          | 4,000                       | Generous multiline session journal.                                                    |
| Exercise notes         | 2,000                       | Generous multiline exercise-specific note.                                             |
| Completion custom note | 1,000                       | Designed for a concise completion annotation.                                          |
| Completion tag         | 80                          | Defensive boundary for imported/future tags.                                           |
| Catalog search         | 200                         | More than enough for meaningful search while limiting repeated filter work.            |

Length checks count Unicode code points so an emoji is not accidentally counted as two UTF-16 code units. Legitimate Unicode, accented, non-Latin, right-to-left, emoji, quotes, apostrophes, ampersands, angle brackets, and HTML-like strings remain accepted. Unsupported control characters are rejected. Notes retain line breaks and are never silently truncated.

### Collection and payload limits

| Collection/payload      | Hard maximum                       | Warning  | Rationale                                                                                                                     |
| ----------------------- | ---------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Exercises/workout       | 100                                | Above 50 | Far beyond an ordinary session, but bounds draft rendering and save validation.                                               |
| Sets/exercise           | 200                                | Above 50 | Preserves very high-volume protocols while stopping hundreds of accidental taps.                                              |
| Exercises/routine       | 100                                | Above 50 | Supports large templates without permitting unbounded editor/render work.                                                     |
| Stored routines         | 500                                | None     | Defensive domain constant for future collection validation; current UI is naturally much smaller.                             |
| Completion tags         | 32                                 | None     | Defensive bound around the fixed UI set.                                                                                      |
| Visible catalog results | 60                                 | None     | Keeps local filtering responsive without changing the source snapshot.                                                        |
| Catalog records         | 10,000                             | None     | Generous provider-neutral asset envelope above the current snapshot.                                                          |
| Backup file             | 25 MiB                             | None     | Large enough for years of local text workout history while rejecting obviously accidental or hostile payloads before parsing. |
| Backup nesting/values   | 20 levels / 500,000 visited values | None     | Prevents pathological traversal before migration and schema validation.                                                       |

## Confirmed defects and protections

- Manual set inputs could be sanitized or clamped before the user understood the error. Raw invalid values now remain visible with inline feedback.
- Weight and repetition values had no beta-specific absolute ceiling. Storage writes and imports now share the same pure semantic boundary.
- Settings used numeric conversion/default normalization that could replace a mistake. Invalid settings now remain in the form and never persist.
- Required names, notes, and search could be extremely long. Finite Unicode-aware limits now protect render/search/storage work without truncation.
- Add Set, workout exercise lists, and routine lists were unbounded. Generous collection limits stop accidental runaway structures.
- New-workout save was reentrant, so top/bottom/rapid save could create multiple IDs. Save and completion now share a single in-flight outcome.
- Routine save/delete/reset, settings save/reset, goals save, History edit/delete, Home start/resume, backup export/import/clear, and completion submission could overlap. Focused coordinators now disable/reuse the in-flight action and re-enable on failure.
- Backup parsing began without a file-size or practical-complexity check. Size is checked before reading; structure, complexity, semantic values, and duplicate IDs are checked before writes.
- Draft and settings localStorage exceptions could escape user actions. They now preserve visible data, report a retryable failure, and avoid false success.
- Blank workout dates could acquire an unexpected default during save. New saves require a real date; future and overnight interpretations require confirmation.

## Security-oriented rendering findings

- User-entered/imported names and notes are rendered with existing escaped template helpers or DOM text APIs; the adversarial browser journey verifies script-like and event-handler-like text remains literal.
- Catalog provider strings pass through the provider-neutral contract and safe rendering; no catalog instruction is copied into a user note or persisted workout.
- Search does not insert a user-controlled HTML highlight fragment.
- Error messages come from fixed validation copy and do not echo raw personal workout text.
- No sanitization dependency was added because no confirmed raw-HTML sink accepted these inputs.

## Storage and failure behavior

- Workout/routine storage entry points perform defense-in-depth semantic validation before opening a write.
- A failed workout write leaves the active screen and valid draft available, shows one failure message, and re-enables Save.
- Backup restore validates before confirmation/writes, uses one IndexedDB transaction, restores the captured localStorage snapshot on failure, and never reports success after rejection.
- Application-schema metadata remains marker-last. A failed migration/import does not advance it.
- Technical causes use concise console messages without serializing the workout or backup payload.
- No destructive automatic recovery or record rewrite was added.

## Deterministic human-like journeys

`tests/e2e/beta-hardening.spec.js` covers:

1. Careless numeric entry: giant/negative/scientific values remain visible, are corrected, and one valid workout is saved.
2. Repeated actions: top and bottom Save plus double Done create one record; modal focus and body lock recover.
3. Custom exercise: an overlong name/note is rejected, then Unicode, emoji, punctuation, and executable-looking text save and render literally.
4. Collection abuse: double Add Exercise, 205 rapid Add Set taps, and double catalog Add stop at the expected counts.
5. Routine/settings/goals: double routine save and invalid numeric/range values remain correctable and persist only after correction.
6. Backup abuse: empty, 25 MiB-plus, extreme-value, and duplicate-ID files preserve original data; repeated valid import runs once and keeps the active draft.
7. Storage failure: an injected IndexedDB rejection creates no record, keeps the draft, re-enables Save, and succeeds on retry.
8. Date/time: blank and malformed values are blocked; a leap-day overnight session requires confirmation and stores a 45-minute duration.
9. Offline tester: after an online controlled load, the app goes offline, resumes a draft, searches the cached catalog, uses a catalog Guide, saves one workout, reloads, and finds the same IndexedDB record.

Existing Playwright journeys continue to cover routine switching, draft reload, browser-style back flows, catalog failure/alias/Guide behavior, safe offline startup, service-worker app-shell paths, legacy startup, v2/v3 backups, rollback, Exercise Details scrolling, and rest-timer absence.

## Remaining beta risks

- A physical process kill at the exact moment of a browser-managed file picker, service-worker activation, or localStorage call cannot be modeled perfectly in Playwright.
- Real storage quota thresholds vary by browser, free disk space, installed-PWA mode, and eviction policy. Injected failure verifies app coordination, not every vendor message.
- A 25 MiB valid JSON backup is deliberately allowed; parsing near the ceiling may pause briefly on low-memory devices.
- Extreme but permitted workouts (for example, 100 exercises with 200 sets each) are bounded but not expected to be pleasant to render. The warning thresholds are the practical beta signal.
- Browser/Android Back, software keyboard resizing, haptics, share/file pickers, offline cold start, and service-worker replacement still need the physical Samsung Galaxy S24 Ultra checklist.

## Changing a limit

Future changes should update the constant in `src/js/domain/input-guardrails.js`, its exact-boundary and one-above unit tests, the relevant rendered correction journey, and this document. Raise or lower a limit only with a concrete usability, data-integrity, accessibility, or performance reason. Do not change a schema or rewrite historical records merely because the UI limit changes.
