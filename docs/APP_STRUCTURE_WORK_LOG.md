# App Structure Work Log

This is the current handoff for the app-structure touch-up branch. Inspect the referenced production code and tests before continuing; do not assume later planned slices have started.

## Completed

- Home no longer renders the visible Progress Glance section or bottom backup reminder card.
- The floating Home workout CTA remains full size while scrolling. Its existing breathing animation and Start/Resume workout behavior remain intact.
- The Home tour now has three targets. The removed Progress Glance target is no longer part of the walkthrough.
- Obsolete Home-only rendering, event binding, compact-state styling, and backup-reminder integration were removed. The service-worker cache was bumped to `hector-workout-tracker-pwa-v20`.
- App-wide coach marks now use shared responsive placement: narrow screens dock guidance opposite the highlighted target, highlights stay out from under the explanation card, and wider screens retain contextual above/below placement.
- Tour-step movement now fades and repositions smoothly while continuing to honor reduced-motion and disabled-animation preferences. Inline guidance actions and the RPE explanation dialog also use more consistent mobile sizing. The service-worker cache was bumped to `hector-workout-tracker-pwa-v21`.
- Phone follow-up: Home's remaining grid rows no longer stretch into the space left by the removed cards, the Home shell no longer creates obsolete page scroll, and mobile tour bubbles are compact again while retaining responsive no-overlap placement. The service-worker cache was bumped to `hector-workout-tracker-pwa-v22`.
- Guidance follow-up: the first-run Home tour invitation is a compact floating dialogue instead of a layout row, and Home once again uses content-driven scrolling after guidance closes without restoring removed-card whitespace. The service-worker cache was bumped to `hector-workout-tracker-pwa-v23`.
- Invitation refinement: the same first-run dialogue now has a speech-bubble tail, inset placement, and layered depth that visually anchor it to the workout card while keeping it out of layout flow. The service-worker cache was bumped to `hector-workout-tracker-pwa-v24`.
- Invitation placement follow-up: the compact speech bubble now floats at the lower weekly-summary boundary with its body outside the workout card and only its tail pointing into that card. The service-worker cache was bumped to `hector-workout-tracker-pwa-v25`.
- Active Workout guidance follow-up: each mobile spotlight now repositions its target into the clear space beside the compact dialogue before drawing the full six-pixel frame, so exercise, current-set, Finish, and RPE targets are no longer offset or cut through. Spotlight geometry no longer animates behind target movement. The service-worker cache was bumped to `hector-workout-tracker-pwa-v26`.
- CI geometry correction: coach marks now briefly release the body scroll lock while positioning each target, preserve the resulting scroll offset while restoring the lock, and handle sticky targets from their pre-unlock document position. All four Active Workout steps explicitly require their targets to intersect the mobile viewport while retaining the full padded frame and no-overlap assertions. The service-worker cache was bumped to `hector-workout-tracker-pwa-v27`.
- Escape reliability correction: an open coach mark now listens for Escape at document scope for its active lifetime, so dismissal works immediately while initial positioning is still moving focus into the dialogue. The existing root-level focus trap, body scroll restoration, guide history, and positioning behavior remain unchanged. The service-worker cache was bumped to `hector-workout-tracker-pwa-v28`.
- Catalog-backed starter routines: new users now receive five starter templates composed at runtime from normalized catalog records selected by provider-neutral movement metadata. The app-owned default exercise inventory was removed; only catalog canonical names are persisted in the unchanged routine schema, existing routines remain untouched, and an incomplete catalog creates no exercise fallback. The empty Custom routine remains available for user-authored exercises. The service-worker cache was bumped to `hector-workout-tracker-pwa-v29`.
- Short-viewport Active Workout correction: sticky guide targets are remeasured after the body lock is temporarily released, positioned from their live viewport geometry, and verified again after the lock returns. The 360 x 640 guide contract now covers exercise, current-set, Finish, and RPE targets, with Finish explicitly required to remain fully in view behind a real padded frame. The service-worker cache was bumped to `hector-workout-tracker-pwa-v30`.
- RPE education placement: the Active Workout guide now ends after its existing three steps. The unchanged one-time RPE explanation appears when the user first opens the focused exercise-detail page, targets that page's RPE control, and retains the existing `rpeBasics` completion/replay state without repeating after dismissal. The service-worker cache was bumped to `hector-workout-tracker-pwa-v31`.
- Confirmed routine exercise additions: after a workout is saved, exercises that are not already in its originating saved routine can be added through an explicit confirmation. Declining leaves the routine unchanged, and an unexpected routine persistence failure leaves the completed workout intact. The routine candidate is validated, refreshed by ID before merging, saved through the existing guarded persistence path, and verified after the write. The service-worker cache was bumped to `hector-workout-tracker-pwa-v32`.
- Routine identity correction: the active workout captures the originating routine ID when its template loads, carries that optional compatible metadata through drafts and completed workouts, restores it before a resumed draft renders, and resolves the optional routine update by ID rather than display name. This prevents a routine rename or same-name replacement from suppressing or redirecting a confirmed update. The service-worker cache was bumped to `hector-workout-tracker-pwa-v33`.

Relevant files: `index.html`, `src/js/router.js`, `src/js/application/routine-seeding.js`, `src/js/catalog/starter-routines.js`, `src/js/screens/today.js`, `src/js/screens/active-workout.js`, `src/js/screens/progress.js`, `src/js/screens/backup.js`, `src/js/components/coach-mark.js`, `src/js/domain/routine-draft.js`, `src/js/schema/normalize.js`, `src/js/schema/validators.js`, `src/styles/today.css`, `src/styles/screens.css`, `src/styles/education.css`, `service-worker.js`, and the related unit and browser tests.

## Remaining planned work

- None in this handoff.

## Constraints and decisions

- Preserve the Home CTA's breathing animation; the removed behavior is only its scroll-driven compact/shrink morph.
- Do not restore the removed Progress Glance target to the Home tour.
- Preserve existing saved routines, workouts, drafts, settings, storage keys, schemas, backup formats, and navigation behavior.
- Catalog-backed starter-routine seeding must remain compatible with existing user-created and previously seeded routines, without duplication or destructive replacement. Starter templates must not introduce app-authored exercise inventories or provider-specific persisted fields.
- Continue using the project's existing modules, visual language, test setup, and service-worker cache-update convention.

## Verification at this checkpoint

- `npm run lint`
- `npm run format:check`
- `npm run test:unit` - 161 passing
- Full `tests/e2e/workout.spec.js` coverage at the configured 412 x 915 mobile viewport - 12 passing
- Affected Unicode and input-hardening browser case - 1 passing
- Targeted accept, decline, and injected routine-write-failure flows - 3 passing
- Targeted 412 x 915 rendered QA - confirmation text identified the exercise and routine, the existing completion dialog stayed in the viewport, both records persisted after acceptance, and console output was clean apart from the expected Playwright service-worker block
- `node --check` on the touched JavaScript and service-worker files

No additional commit or push was created for the follow-up fix.
