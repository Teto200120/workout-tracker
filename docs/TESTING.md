# Testing

## Automated coverage

Playwright runs the PWA in Chromium at a 412 x 915 mobile viewport. Each test receives a new browser context, so its IndexedDB and localStorage data are isolated from other tests and from normal browser profiles.

The smoke suite covers:

- Startup, primary navigation, Stats details, Profile subpages, and `aria-current` state.
- Immediate workout start after changing the selected Home routine.
- Active-workout draft values, notes, and active-exercise recovery after reload.
- Saving a workout and finding one non-duplicated record in History.
- Settings save, reload, and reset behavior.
- Backup download, clear, restore without legacy `weights`, invalid import rejection, and failed-transaction rollback.
- Stats, History, and search behavior with about 200 fixture workouts.

Tests block service workers to prevent an older local cache from mixing assets between runs. Vibration is replaced with a harmless browser stub. Downloads, file uploads, and confirmation dialogs are handled through Playwright. Fixtures are inserted only into the test context's IndexedDB database.

## Commands

- `npm run serve` starts the static server at `http://127.0.0.1:4175`.
- `npm run lint` checks JavaScript for likely defects.
- `npm run format:check` checks the adopted tooling, test, workflow, and documentation files.
- `npm run test` and `npm run test:e2e` run the headless Chromium suite.
- `npm run test:e2e:ui` opens Playwright UI mode for interactive debugging.
- `npm run check` runs the pre-commit validation used by CI.

When a browser test fails, inspect `playwright-report/index.html` with `npx playwright show-report`. CI uploads the HTML report, traces, screenshots, and retained failure videos when available. Locally, rerun one test with `npx playwright test tests/e2e/workout.spec.js --debug` or use UI mode.

## Manual coverage

The focused browser suite does not replace [QA_CHECKLIST.md](QA_CHECKLIST.md). Offline install/startup, installed-PWA behavior, device vibration, file handling outside Chromium, real keyboard behavior, and Android-specific layout/performance remain manual. These checks depend on an installed PWA, physical hardware, or platform behavior that headless CI cannot represent reliably.

Formatting adoption is intentionally scoped to new tooling and test infrastructure in this branch. A later formatting-only change can expand Prettier to production HTML, CSS, and JavaScript without mixing whitespace churn into functional work.
