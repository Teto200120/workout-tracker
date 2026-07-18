# Release QA Checklist

## Startup

- [ ] Load once with empty storage; only the one-screen display-name onboarding renders, with no Home flash or console errors.
- [ ] Try blank, invisible-only, 81-character, international, emoji, and markup-like names; confirm clear validation, literal safe rendering, retained input, and successful correction.
- [ ] Force a settings write failure and double-submit; confirm one in-flight attempt, visible retryable feedback, retained input, and no app entry until the retry succeeds.
- [ ] Save a valid name; confirm Home, Profile, and Settings update, then reload without seeing onboarding.
- [ ] Reload; default routines are not duplicated.
- [ ] Confirm Home is active with and without an existing workout draft.
- [ ] Test once with animations enabled and once disabled.
- [ ] Seed unversioned workouts, routines, settings, goals, draft, backup metadata, and legacy weights; confirm ordered migration writes schema marker `2` only after all data remains usable, then requests a name.
- [ ] Reload canonical schema `2` data with a valid name; confirm records and raw localStorage values are not rewritten or duplicated.
- [ ] Seed malformed legacy data; confirm startup stops, the original records remain, the marker stays absent, and the recovery message is visible.
- [ ] Correct the malformed data and reload; confirm migration retries successfully.
- [ ] Seed a future application schema marker; confirm startup refuses to downgrade or overwrite it.

## Home

- [ ] Confirm the saved display name appears in the existing time-of-day greeting and markup-like text is not interpreted as HTML.
- [ ] Check scheduled workout, rest-day copy, and completed-workout state.
- [ ] Change the selected routine and start it; the selected routine opens.
- [ ] Confirm the primary CTA shows Start, Resume, or the completed state correctly.
- [ ] Confirm card controls do not trigger the card preview accidentally.

## Active Workout

- [ ] Load a multi-exercise routine; only the intended exercise is expanded.
- [ ] Expand, collapse, remove, and reorder exercises where supported.
- [ ] Open Add Exercise; search local options, select an existing name, and confirm the new exercise opens.
- [ ] Delay catalog loading; confirm local options and Create New Exercise remain usable immediately while a compact loading status is visible.
- [ ] At approximately 412 x 915, confirm search is immediately visible, Filters is collapsed by default, three local rows plus Show All remain accessible, and the catalog section is reachable without excessive preliminary scrolling.
- [ ] Open Filters with click and keyboard; confirm `aria-expanded`, the active-filter count, native labels, primary muscle/equipment/category filtering, Reset, close behavior, visible focus, and minimum 44 px controls.
- [ ] Search a catalog exercise; confirm exact/prefix/substring ranking, compact primary-muscle/equipment metadata, immediate catalog access, and only one row for a local/catalog name collision.
- [ ] Open catalog preview; confirm only a short instruction preview appears with a remaining-step count, full instructions are absent, source attribution is quiet, and Back/Add work without empty labels.
- [ ] At approximately 412 x 915, confirm preview content is the single contained vertical scroller, mobile-safe actions remain visible, the document is locked, and neither picker state has horizontal overflow.
- [ ] Add a catalog exercise, reload/resume, save/edit, and inspect the draft/workout; only existing `name`, `notes`, and `sets` fields are present.
- [ ] Make the catalog unavailable and separately inject one malformed record; local/custom flows still work, and usable catalog records survive the single malformed entry.
- [ ] Cancel and dismiss Add Exercise; confirm neither action changes the workout or draft.
- [ ] Create a custom exercise; reject an empty name, trim a valid name, and reuse canonical capitalization for an existing name.
- [ ] Add and remove sets; mark a warm-up set separately.
- [ ] Enter weight, reps, RPE, and notes; confirm values persist.
- [ ] Complete and undo a set; confirm the active-workout elapsed indicator remains visible and continues updating.
- [ ] Confirm no manual rest-timer buttons, countdown, or panel appear.
- [ ] Add a known catalog exercise, open Exercise Details > Guide, and confirm full provider steps use an ordered list with equipment/muscles/category/difficulty when present and attribution at the bottom.
- [ ] Confirm Guide rendering leaves Exercise Notes empty/unchanged and the draft exercise still contains only `name`, `notes`, and `sets`.
- [ ] Start a default routine containing an exact name, recover a draft containing a reviewed alias, and edit completed history; each receives the intended catalog Guide while its local saved name remains unchanged.
- [ ] Create `My Saturday Row` and broad `Bench Press`; each keeps the generic Guide, shows no catalog attribution, and produces no console error or incorrect related-variant instructions.
- [ ] Open Exercise Details, switch Log/Guide tabs, and return without losing input.
- [ ] At approximately 412 x 915, scroll Exercise Details and confirm the document stays locked while the detail content is the only vertical scroller.
- [ ] Confirm Exercise Notes and set controls clear the bottom action, and the Log/Complete Set action remains usable with the browser toolbar visible.
- [ ] Focus and type into Exercise Notes with the mobile keyboard open; confirm the field can be scrolled into view.
- [ ] Enter blank, zero, negative, `300000`, decimal, excessive-precision, scientific-notation, letters, decimal-comma, and leading-zero values in weight/reps/RPE; confirm invalid raw text remains visible, inline copy explains the boundary, and correction succeeds.
- [ ] Confirm weight 10,000 and reps 10,000 save, one-above is blocked, RPE 10 is accepted, RPE 10.5 is blocked, and blank optional RPE remains accepted.
- [ ] Confirm values above 2,000 weight or 500 reps warn once at save without clamping; cancel and verify the draft remains unchanged.
- [ ] Rapidly click Add Set beyond 200 and Add Exercise beyond 100; confirm the boundary message appears and existing draft content remains usable.
- [ ] Paste over-limit exercise/workout notes and a 121-character exercise name; confirm no truncation, then correct each and continue.

## Draft Recovery

- [ ] Return to Home mid-workout and resume the same routine and exercise.
- [ ] Reload mid-workout and confirm all set data and active position restore.
- [ ] Clear the draft and confirm Home returns to its normal state.

## Workout Save and Edit

- [ ] Save a completed and an incomplete workout using the existing confirmation.
- [ ] Finish the completion popup with and without a custom note.
- [ ] Edit a saved workout and confirm its ID is updated without a duplicate.
- [ ] Confirm start time, end time, duration, notes, and tags remain valid.
- [ ] Trigger top and bottom Save nearly simultaneously and double-tap completion Done; confirm exactly one workout ID exists.
- [ ] Leave the date empty and enter malformed time text through a bypass test; confirm neither reaches IndexedDB.
- [ ] Save a future date and an end time earlier than start; confirm each warning is explicit and overnight duration is correct after acceptance.
- [ ] Inject an IndexedDB workout-write rejection; confirm no success, no record, preserved draft/inputs, re-enabled controls, and successful retry.

## History

- [ ] Confirm newest workouts appear first.
- [ ] Test routine filtering, exercise search, and empty results.
- [ ] Open full details, edit one workout, and delete one workout.
- [ ] Return to Stats and confirm the Stats tab remains active.

## Stats

- [ ] Check empty, one-workout, and several-workout states.
- [ ] Verify weekly activity, recent sessions, totals, records, and weekly goal.
- [ ] Switch Best Load, Estimated 1RM, and Best Set Volume metrics.
- [ ] Open every detail card with click, Enter, and Space; test every back control.

## Routines

- [ ] Create, name, edit, and delete a routine; add exercises with button and Enter.
- [ ] Browse the shared picker and add one local, one catalog, and one custom name; confirm only the unsaved draft changes before Save Routine.
- [ ] Change one existing row through the picker; confirm other rows keep their exact names, Cancel is non-destructive, and focus returns to Browse/Change.
- [ ] Exercise normalized duplicate add/replace confirmations and rapid selection; confirm intentional repeats require acceptance and no accidental duplicate action occurs.
- [ ] Make the catalog unavailable and repeat local/custom add plus an empty search; confirm the routine builder remains usable.
- [ ] Clear an unsaved draft and reset routines to defaults.
- [ ] Start a saved routine and reload to confirm routine changes persist.
- [ ] Try blank/81-character routine names, 121-character exercise names, normalized duplicates, and more than 100 exercises; confirm clear correction paths.
- [ ] Double-tap routine Save, Delete, Reset, and Start; confirm one logical action and no duplicate routines/sessions.

## Settings

- [ ] Edit the display name, Cancel, save an international/emoji value, force one localStorage failure, and reload; confirm unrelated settings and the prior saved name survive failed/cancelled edits.
- [ ] Save schedule, weight jump, rep ranges, haptics, and animations.
- [ ] Reload and confirm settings apply to Home and Stats.
- [ ] Reset settings and confirm workout history and routines remain.
- [ ] Try blank, negative, scientific, decimal-integer, 300000, minimum-above-maximum, and maximum-boundary settings/goal values; confirm invalid values never replace the current stored settings.
- [ ] Trigger a localStorage write failure; confirm the entered form remains and no false success appears.

## Backup Export and Import

- [ ] Export representative workouts, routines, goals, settings, and legacy data.
- [ ] Inspect the JSON and filename; confirm `backupFileVersion: 3`, `applicationSchemaVersion: 2`, and the saved display name, then import it after Clear All Local Data.
- [ ] Confirm all supported data returns and restored routines are usable.
- [ ] Try malformed JSON and a structurally invalid backup; confirm neither is imported.
- [ ] Import an unversioned, v1, and v2 legacy backup without a name; confirm each migrates to schema `2` without losing unknown safe record fields and requires onboarding before Home.
- [ ] Import an over-limit, invisible-only, or non-string display name; confirm the whole import is rejected and current data/name remain unchanged.
- [ ] Import a backup without `weights`; confirm it remains valid and the compatibility store stays intact.
- [ ] Reimport a new v3 export; confirm meaningful data is equivalent and records are not duplicated beyond merge-by-ID behavior.
- [ ] Try a future backup-file version and future application-schema version; confirm each is refused without writes.
- [ ] Trigger the `id: null` key-path failure; confirm all records in the IndexedDB import transaction roll back and localStorage is unchanged.
- [ ] Try an empty file, non-JSON, array top level, more than 25 MiB, nesting beyond 20 levels, excessive text, weight/reps above 10,000, and duplicate non-null IDs; confirm rejection occurs before any record changes.
- [ ] Start a workout draft, import twice rapidly, and confirm one explicit draft warning, one import transaction, preserved draft, and one success outcome.
- [ ] Cancel the file picker and confirmation; confirm no data changes and Import remains usable.
- [ ] Clear all local data; confirm `hector_workout_data_schema_version` and the name are removed and onboarding appears immediately.

## Offline Startup

- [ ] Load online until the service worker controls the page.
- [ ] Enable DevTools Network > Offline and reload; Home still renders.
- [ ] Resume a cached draft offline, open Add Exercise, and confirm catalog search still returns the cached snapshot.
- [ ] Open Routines offline, Browse the shared catalog, and add a name to the unsaved draft.
- [ ] Open an existing exact/alias exercise Guide offline; confirm provider steps and attribution render from cache with no provider request.
- [ ] Make the catalog asset unavailable in a disposable profile; confirm existing/local/custom exercises receive the generic Guide without an error.
- [ ] Open cached subpages and confirm no mixed-version module errors.
- [ ] Re-enable networking and confirm the current cache replaces older caches.
- [ ] Confirm the active cache name is `hector-workout-tracker-pwa-v17`, every onboarding/schema/catalog/routine-picker module is present, and `src/data/exercise-catalog.json` is cached.
- [ ] Save and reload a representative workout while offline; confirm one record, recovered/corrected draft behavior, and no missing `input-guardrails.js` or `action-coordinator.js` request.

## Mobile Layout

- [ ] Check a narrow phone viewport and the target phone in portrait.
- [ ] Confirm no horizontal scrolling, clipped titles, or covered controls.
- [ ] Focus form inputs and confirm they remain visible above fixed UI and keyboard.
- [ ] Check the compact exercise picker, expanded filter panel, shortened preview, long catalog-backed Guide, completion overlay, long names, and bottom navigation.
- [ ] At 412 x 915, check onboarding without nested scrolling and routine Browse/Change with long names, visible actions, 44 px targets, and no horizontal overflow.

## Samsung Galaxy S24 Ultra data checks

- [ ] In Chrome portrait, migrate representative unversioned local data and confirm Home, History, Stats, Routines, Settings, and draft resume remain usable.
- [ ] Force a malformed legacy record in a disposable test profile; confirm the migration failure message is readable and original data can still be exported/inspected after correction.
- [ ] Export a v3 backup, clear data, reimport it, and confirm the schema marker and all supported data return.
- [ ] Enable airplane mode after an online controlled load; confirm cache v17 starts Home, opens cached subpages, searches the catalog from Active Workout and Routines, and renders an existing-name catalog Guide without mixed-version module errors.
- [ ] Repeat keyboard, haptic, Exercise Details scrolling, compact filters/count/reset, local/Show All/catalog hierarchy, shortened preview/full Guide, save/edit, and no-horizontal-overflow checks at the device's default display scaling.

## Accessibility Basics

- [ ] Confirm the active tab exposes `aria-current="page"`.
- [ ] Check button names, input labels, focus indicators, and touch target size.
- [ ] Confirm onboarding errors are announced, the name input keeps focus on correction, picker titles/actions describe add versus replace context, Escape/Back/cancel are non-destructive, and focus returns to the launcher.
- [ ] Confirm the filter disclosure announces expanded/collapsed and active-count state, Reset stays reachable, Guide sections use headings, instructions use an ordered list, and source is not conveyed by color alone.
- [ ] Activate interactive Stats cards with Enter and Space.
- [ ] Confirm reduced/disabled motion does not break navigation.
