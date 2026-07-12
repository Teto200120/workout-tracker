# Release QA Checklist

## Startup

- [ ] Load once with empty storage; Home renders with no console errors.
- [ ] Reload; default routines are not duplicated.
- [ ] Confirm Home is active with and without an existing workout draft.
- [ ] Test once with animations enabled and once disabled.

## Home

- [ ] Check scheduled workout, rest-day copy, and completed-workout state.
- [ ] Change the selected routine and start it; the selected routine opens.
- [ ] Confirm the primary CTA shows Start, Resume, or the completed state correctly.
- [ ] Confirm card controls do not trigger the card preview accidentally.

## Active Workout

- [ ] Load a multi-exercise routine; only the intended exercise is expanded.
- [ ] Expand, collapse, remove, and reorder exercises where supported.
- [ ] Open Add Exercise; search local options, select an existing name, and confirm the new exercise opens.
- [ ] Cancel and dismiss Add Exercise; confirm neither action changes the workout or draft.
- [ ] Create a custom exercise; reject an empty name, trim a valid name, and reuse canonical capitalization for an existing name.
- [ ] Add and remove sets; mark a warm-up set separately.
- [ ] Enter weight, reps, RPE, and notes; confirm values persist.
- [ ] Complete and undo a set; confirm the active-workout elapsed indicator remains visible and continues updating.
- [ ] Confirm no manual rest-timer buttons, countdown, or panel appear.
- [ ] Open Exercise Details, switch Log/Guide tabs, and return without losing input.
- [ ] At approximately 412 x 915, scroll Exercise Details and confirm the document stays locked while the detail content is the only vertical scroller.
- [ ] Confirm Exercise Notes and set controls clear the bottom action, and the Log/Complete Set action remains usable with the browser toolbar visible.
- [ ] Focus and type into Exercise Notes with the mobile keyboard open; confirm the field can be scrolled into view.

## Draft Recovery

- [ ] Return to Home mid-workout and resume the same routine and exercise.
- [ ] Reload mid-workout and confirm all set data and active position restore.
- [ ] Clear the draft and confirm Home returns to its normal state.

## Workout Save and Edit

- [ ] Save a completed and an incomplete workout using the existing confirmation.
- [ ] Finish the completion popup with and without a custom note.
- [ ] Edit a saved workout and confirm its ID is updated without a duplicate.
- [ ] Confirm start time, end time, duration, notes, and tags remain valid.

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
- [ ] Clear an unsaved draft and reset routines to defaults.
- [ ] Start a saved routine and reload to confirm routine changes persist.

## Settings

- [ ] Save schedule, weight jump, rep ranges, haptics, and animations.
- [ ] Reload and confirm settings apply to Home and Stats.
- [ ] Reset settings and confirm workout history and routines remain.

## Backup Export and Import

- [ ] Export representative workouts, routines, goals, settings, and legacy data.
- [ ] Inspect the JSON and filename; import it after Clear All Local Data.
- [ ] Confirm all supported data returns and restored routines are usable.
- [ ] Try malformed JSON and a structurally invalid backup; confirm neither is imported.

## Offline Startup

- [ ] Load online until the service worker controls the page.
- [ ] Enable DevTools Network > Offline and reload; Home still renders.
- [ ] Open cached subpages and confirm no mixed-version module errors.
- [ ] Re-enable networking and confirm the current cache replaces older caches.

## Mobile Layout

- [ ] Check a narrow phone viewport and the target phone in portrait.
- [ ] Confirm no horizontal scrolling, clipped titles, or covered controls.
- [ ] Focus form inputs and confirm they remain visible above fixed UI and keyboard.
- [ ] Check the exercise picker, completion overlay, long names, and bottom navigation.

## Accessibility Basics

- [ ] Confirm the active tab exposes `aria-current="page"`.
- [ ] Check button names, input labels, focus indicators, and touch target size.
- [ ] Activate interactive Stats cards with Enter and Space.
- [ ] Confirm reduced/disabled motion does not break navigation.
