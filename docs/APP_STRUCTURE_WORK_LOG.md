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

Relevant files: `index.html`, `src/js/router.js`, `src/js/screens/today.js`, `src/js/screens/progress.js`, `src/js/screens/backup.js`, `src/js/components/coach-mark.js`, `src/styles/today.css`, `src/styles/screens.css`, `src/styles/education.css`, `service-worker.js`, and the related browser tests.

## Remaining planned work

1. Seed initial routines from the existing exercise catalog data. Reuse the current catalog contracts and resolution paths; do not introduce a parallel catalog or provider-specific stored format.

## Constraints and decisions

- Preserve the Home CTA's breathing animation; the removed behavior is only its scroll-driven compact/shrink morph.
- Do not restore the removed Progress Glance target to the Home tour.
- Preserve existing saved routines, workouts, drafts, settings, storage keys, schemas, backup formats, and navigation behavior.
- Initial routine seeding must remain compatible with existing user-created and previously seeded routines, without duplication or destructive replacement.
- Continue using the project's existing modules, visual language, test setup, and service-worker cache-update convention.

## Verification at this checkpoint

- `npm run lint`
- `npm run format:check`
- `npm run test:unit` — 155 passing
- Full beta-education Playwright coverage at the configured mobile width — 8 passing
- Targeted 390 × 844 Home layout check — passing with no document scroll, aligned card margins/widths, and compact workout-card height
- Targeted 360 × 640 coach-mark geometry checks across all Home tour steps — passing with in-viewport, non-overlapping card and highlight bounds
- Targeted first-run invitation and post-tour overflow checks — passing with a compact anchored out-of-flow dialogue, visible speech tail/depth, and restored content-driven scrolling
- Targeted 390 Ã— 844 Active Workout guidance geometry across all four available steps â€” passing with exact padded target bounds, no dialogue overlap, and clean console output
- `node --check` on the touched JavaScript and service-worker files

No commit or push was created.
