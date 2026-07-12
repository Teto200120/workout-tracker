# Testing

## Automated coverage

### Direct unit tests

Node's built-in test runner imports production functions directly from `src/js/domain/` and the pure backup validator in `src/js/application/backup.js`. It needs no browser DOM, storage emulation, jsdom, or additional dependency.

The unit suite covers:

- Epley estimated 1RM, set volume, workout volume, and workout duration.
- Working-set totals and the existing completed warm-up behavior.
- Best-set selection, workout and exercise aggregation, PR comparison, and completion summaries.
- Empty workouts, empty sets, zero and missing values, invalid numeric input behavior, and workout saveability.
- Monday-first schedule resolution, weekly activity, streaks, rolling weekly counts, and date boundaries with an explicit current date.
- Exercise classification, routine tags, duration estimates, warm-up filtering, exercise profiles, RPE-aware targets, and progression recommendations.
- Exercise-name trimming, case-insensitive deduplication, local-source option construction, substring search, and canonical-name matching.
- Backup record-array validation, optional legacy `weights`, required IDs, and pre-transaction rejection.

Domain tests should assert business rules and deterministic aggregation. They should not copy production formulas into test helpers, inspect private implementation details, or depend on the real current time.

### Browser tests

Playwright runs the PWA in Chromium at a 412 x 915 mobile viewport. Each test receives a new browser context, so its IndexedDB and localStorage data are isolated from other tests and normal browser profiles.

The browser suite covers:

- Startup, primary navigation, Stats details, Profile subpages, and `aria-current` state.
- Immediate workout start after changing the selected Home routine.
- Active-workout draft values, notes, and active-exercise recovery after reload.
- Exercise Details single-scroll ownership, action-dock visibility, notes entry, content clearance, and back navigation at 412 x 915.
- Add Exercise picker open/cancel behavior, local options, search, deduplication, existing selection, custom-name validation, focus return, and draft recovery.
- Manual rest-timer absence alongside active-workout elapsed-time and saved-duration checks.
- Saving a workout and finding one non-duplicated record in History.
- Settings save, reload, and reset behavior.
- Backup download, clear, restore without legacy `weights`, invalid import rejection, and failed-transaction rollback.
- Stats, History, and search behavior with about 200 fixture workouts.

Tests block service workers to prevent an older local cache from mixing assets between runs. Vibration is replaced with a harmless browser stub. Downloads, uploads, and confirmation dialogs are handled through Playwright. Fixtures use the same explicit production storage interfaces as the app.

## Commands

- `npm run serve` starts the static server at `http://127.0.0.1:4175`.
- `npm run lint` checks JavaScript for likely defects and guards the reduced transitional-global list.
- `npm run format:check` checks the adopted tooling, tests, workflow, and documentation files.
- `npm run test:unit` runs direct domain tests only.
- `npm run test:e2e` runs the headless Chromium workflow suite only.
- `npm run test` runs unit tests, then browser tests.
- `npm run test:e2e:ui` opens Playwright UI mode for interactive browser debugging.
- `npm run check` runs every merge-blocking validation used by CI: lint, formatting, unit tests, and browser tests.

When a browser test fails, inspect `playwright-report/index.html` with `npx playwright show-report`. CI uploads the HTML report, traces, screenshots, and retained failure videos when available. Locally, rerun one test with `npx playwright test tests/e2e/workout.spec.js --debug` or use UI mode.

## Manual coverage

The automated suites do not replace [QA_CHECKLIST.md](QA_CHECKLIST.md). Offline install/startup, installed-PWA behavior, device vibration, file handling outside Chromium, real keyboard behavior, and Android browser layout/performance remain manual. These checks depend on an installed PWA, physical hardware, or platform behavior that headless CI cannot represent reliably.

On the Samsung Galaxy S24 Ultra, repeat the Home, Active Workout, Exercise Details scroll/action/keyboard checks, Add Exercise picker flows, draft reload, save/edit, backup clear/restore, offline reload, haptic, and portrait-layout checks from the release checklist.

Formatting adoption remains scoped to tooling, tests, workflow, and documentation. Production HTML, CSS, and JavaScript are not reformatted as part of this behavior-preserving refactor.
