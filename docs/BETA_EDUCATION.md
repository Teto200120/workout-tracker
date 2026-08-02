# Beta Education

## Product flow

First launch remains a blocking display-name gate with no Home flash, but the gate now has two focused screens:

1. Welcome explains tracking, local draft resume, progress review, and device-local privacy. Continue validates the existing display-name rules without writing storage.
2. Training Effort explains RPE 8, 9, and 10 and offers `Track RPE`, enabled by default. Back preserves the name and preference. Finish Setup validates again and saves the name, `settings.rpeAware`, the education record, and the current application-schema marker as one coordinated operation with compensating rollback.

Existing users with a valid display name bypass onboarding. Haptics and animations retain their existing defaults and remain in Settings.

`rpeAware` is still the compatibility key. When enabled, Active Workout shows RPE entry and training rules may use effort in target suggestions. When disabled, new RPE controls are hidden and suggestions ignore RPE; saved set values are retained and reappear when enabled again.

## Dependency boundaries

- `domain/education.js` owns definitions, schema/content versions, statuses, normalization, transitions, replay, reset, step bounds, timestamp rules, unknown-key retention, and non-mutation.
- `storage/local.js` is the only production module that reads or writes the education localStorage key.
- `application/education.js` owns persistence, in-memory fallback, offers, explicit replay requests, reset, and user-action results.
- `components/coach-mark.js` owns reusable overlay DOM, target resolution, scrolling, positioning, focus, Escape, browser Back, and missing-target behavior.
- Screen modules own experience copy, stable `data-education-target` attributes, inline tips, and context-specific safety signals.
- `router.js` owns screen-settle coordination, Settings replay/reset commands, and Stats/History inline-tip navigation.

Education does not access IndexedDB and is not application-schema data.

## Local record

The key is `hector_workout_education_v1`; `EDUCATION_SCHEMA_VERSION` is `1`. Known experiences are:

- `homeTour` — four coach-mark steps.
- `activeWorkoutBasics` — three coach-mark steps.
- `rpeBasics` — one coach-mark step.
- `routineEditorBasics` — three coach-mark steps.
- `historyBasics`, `statsBasics`, and `exerciseGuideBasics` — inline first-use notes.

Every known record has `contentVersion`, `status`, `lastStep`, `updatedAt`, and `completedAt`. Supported statuses are `unseen`, `offered`, `in_progress`, `completed`, `skipped`, `dismissed`, and `deferred`.

Missing, null, malformed, and partial records normalize to safe defaults. Valid known state and unknown future experience keys are retained. A future schema or content version is preserved rather than downgraded. A wording-only edit keeps the same content version and does not reset completion. A materially different walkthrough must increment that experience's `contentVersion` or use a new experience ID; older known content is then eligible to reset independently.

Education read failure uses session-memory defaults. A failed education write never blocks workout use; explicit Done, Skip, Replay, or Reset actions receive honest nonblocking feedback. Onboarding is stricter because its required writes share the existing rollback guarantee.

## Presentation rules

Home shows one normal invitation card for a newly unseen tour. It never auto-starts. Show Me Around opens the tour; Skip For Now records `skipped`. A persisted `offered` invitation is not repeatedly resurfaced on later launches, but Settings can always replay it.

Coach marks open only when the screen has settled and no dialog, picker, completion overlay, other coach mark, focused form field, likely software keyboard, set-entry activity, or drag is present. Active Workout additionally tracks interaction within the current rendered session so guidance cannot repeatedly reopen or interrupt a changed set. Routine guidance may open only the existing builder disclosure and restores its prior collapsed state.

History and Stats render compact inline notes on their first relevant visit. Exercise Guide inserts its note inside the Guide surface for both catalog-backed and generic guides. These notes do not cover content and record `completed` through their Got It action.

## Coach-mark accessibility and mobile behavior

The bubble uses modal dialog semantics and an accessible title and description. Focus enters the controls, stays within them while open, and returns to the launcher when that launcher remains appropriate. The first Back control is disabled. The full-page blocker prevents pointer interaction with the highlighted application target.

Opening a walkthrough pushes one same-document history entry. Android/browser Back pops that entry, records `dismissed`, closes the overlay, and keeps the current app screen. Programmatic completion, skip, or dismissal removes the transient entry without navigation loops. Escape also records `dismissed`.

Before each step the component resolves a stable target, verifies visibility and connection, scrolls with the app's motion preference, waits two animation frames, measures, and positions above or below the target. It listens for resize, orientation, scroll, and `visualViewport` changes only while open. Placement reserves the bottom navigation/safe-area region.

Missing steps are skipped. If no usable walkthrough remains, the overlay closes, records `deferred`, and never leaves an invisible blocker. Reduced-motion or disabled-animation settings use instant scrolling and no coach-mark transitions.

## Replay, reset, backup, and clearing

Settings → Help & Guidance provides Home, Active Workout, Routine Editor, RPE, History, Stats, and Exercise Guide replay plus Reset All Guidance. Active Workout replay waits for a real session; it never creates a workout. Routine replay opens the existing editor without changing its draft. RPE Explained is readable immediately and also queues the contextual tip. Exercise Guide replay waits for the next opened Guide.

Reset All Guidance resets only known education experiences to `unseen`. It does not restart onboarding or alter settings, workouts, routines, drafts, goals, schedule, backup metadata, haptics, or animations.

Clear All Data removes the education key and still restores application-schema marker `2` before onboarding. Reset Settings does not change education. Backup version `3` export/import excludes education, and backup rollback snapshots deliberately remain independent from it.

## Offline and testing

Service-worker cache `hector-workout-tracker-pwa-v19` includes `education.css`, the pure domain module, application coordinator, and coach-mark component. No education path performs a network request.

`tests/unit/education.test.js` covers defaults, malformed/partial/future records, statuses, content versions, transitions, bounds, timestamps, replay/reset, non-mutation, unknown keys, and storage failure fallback. `tests/e2e/beta-education.spec.js` covers the two onboarding screens, RPE persistence, Home offer/tour/replay, Escape and Back, focus trapping, missing targets, reduced motion, resize, background blocking, Active Workout/RPE safety, Routine draft preservation, inline tips, Settings reset, Guide behavior, and Clear All Data. Existing regression, offline shell, catalog, backup, and migration suites remain authoritative.

## Adding an experience

1. Add one definition with a stable ID, `contentVersion`, and step count in `domain/education.js`.
2. Add pure normalization/transition tests and decide whether the presentation is a coach mark or inline note.
3. Use stable `data-education-target` attributes for anchored steps.
4. Route persistence through `application/education.js`; screens must not edit localStorage.
5. Add replay/reset behavior and safe-context browser tests.
6. Add every new production file to the service-worker shell and bump the cache once for that release.

Do not increment the application schema, IndexedDB version, backup version, or unrelated experience content versions for an education-only change.
