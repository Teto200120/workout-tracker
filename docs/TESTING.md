# Testing

## Automated coverage

### Direct unit tests

Node's built-in test runner imports production functions directly from `src/js/domain/`, `src/js/schema/`, `src/js/catalog/`, the startup coordinator, and the backup validator. It needs no browser DOM, storage emulation, jsdom, or additional dependency.

The unit suite covers:

- Epley estimated 1RM, set volume, workout volume, and workout duration.
- Working-set totals and the existing completed warm-up behavior.
- Best-set selection, workout and exercise aggregation, PR comparison, and completion summaries.
- Empty workouts, empty sets, zero and missing values, invalid numeric input behavior, and workout saveability.
- Monday-first schedule resolution, weekly activity, streaks, rolling weekly counts, and date boundaries with an explicit current date.
- Exercise classification, routine tags, duration estimates, warm-up filtering, exercise profiles, RPE-aware targets, and progression recommendations.
- Exercise-name trimming, case-insensitive deduplication, local-source option construction, substring search, and canonical-name matching.
- Catalog provider normalization, missing optional fields, malformed-record reporting, non-mutation, deterministic IDs, duplicate handling, unknown-field exclusion, metadata/envelope validation, tolerant runtime parsing, and unavailable fallback.
- Catalog exact/prefix/substring ranking, stable empty-query ordering, muscle/equipment/category filters, local precedence, local/catalog duplicate merging, filter extraction, and custom canonical-name matching.
- Catalog resolver trimming, case/space/safe-punctuation normalization, exact matches, shipped and missing aliases, alias-target existence, duplicate normalized names, malformed/missing catalogs, deterministic non-mutation, missing instructions, and deliberate non-matches across press, pulldown, row, and custom-name variants.
- Catalog Guide/preview adaptation: instruction order, blank-step removal, metadata normalization, missing-instruction fallback, attribution, strict preview truncation, and source non-mutation.
- Backup record-array validation, optional legacy `weights`, required IDs, and pre-transaction rejection.
- Application and backup version detection, including invalid and future versions.
- Canonical Set, Exercise, Workout, Routine, Settings, Goals, Draft, backup-metadata, and legacy-weight validation with structured nested paths.
- Non-mutating normalization, zero preservation, ordering, existing defaults, and unknown-field retention.
- The real schema 0-to-1 migration, deterministic/idempotent behavior, invalid input, and current-version validation.
- Startup marker-last ordering, cross-storage rollback, retry, failure-before-effects, and current-version no-rewrite behavior.
- Input guardrails at exact numeric, text, collection, date/time, backup-size, depth, and node-count boundaries, including blank/zero/negative/scientific/non-finite values, Unicode, control characters, duplicates, and non-mutation.
- Single-flight action coordination, including repeated callers sharing one operation and release after rejection.
- Backup semantic abuse checks for extreme workout numbers, excessive strings, duplicate IDs, and unchanged source objects.

Domain tests should assert business rules and deterministic aggregation. They should not copy production formulas into test helpers, inspect private implementation details, or depend on the real current time.

### Browser tests

Playwright runs the PWA in Chromium at a 412 x 915 mobile viewport. Each test receives a new browser context, so its IndexedDB and localStorage data are isolated from other tests and normal browser profiles.

The browser suite covers:

- Startup, primary navigation, Stats details, Profile subpages, and `aria-current` state.
- Immediate workout start after changing the selected Home routine.
- Active-workout draft values, notes, and active-exercise recovery after reload.
- Exercise Details single-scroll ownership, action-dock visibility, notes entry, content clearance, and back navigation at 412 x 915.
- Add Exercise picker open/cancel behavior, three-row local summary/Show All, search, deduplication, existing selection, custom-name validation, focus return, and draft recovery.
- Delayed catalog loading with immediate local options, compact loading/ready status, collapsed keyboard-accessible filter disclosure, active count/reset, search-driven catalog access, local/catalog collision handling, touch-target/layout/no-overflow checks, shortened preview attribution/Back/Add/scroll containment, name-only persistence, and catalog selection after draft reload.
- Catalog-backed Exercise Details Guides for a picker selection, an exact default-routine name, a reviewed alias after draft recovery, and completed history loaded for editing; assertions confirm ordered provider steps, attribution, untouched Notes, and unchanged stored names/shapes.
- Custom/broad-name generic Guide fallback, unsafe related-name protection, malformed-record tolerance, and invalid-envelope fallback with local/custom exercises remaining usable.
- Manual rest-timer absence alongside active-workout elapsed-time and saved-duration checks.
- Saving a workout and finding one non-duplicated record in History.
- Settings save, reload, and reset behavior.
- Backup download, clear, restore without legacy `weights`, invalid import rejection, and failed-transaction rollback.
- Legacy startup migration across IndexedDB and localStorage, current startup without rewrite or duplication, failed migration preservation/retry, and future application-version refusal.
- Backup version declarations, old v2 migration, new v3 round trip, future-version refusal, clear-data marker removal, and retained `id: null` rollback.
- Every service-worker app-shell path returning HTTP 200 and a controlled cached startup with catalog search plus an existing-name catalog Guide while offline.
- Stats, History, and search behavior with about 200 fixture workouts.
- Eight deterministic beta-abuse journeys covering correctable giant/negative/scientific set values; one-record rapid save; Unicode and executable-looking literal text; 205 Add Set taps; double Add Exercise/catalog/routine/import actions; invalid settings/goals; empty/oversized/extreme/duplicate-ID backups; recoverable IndexedDB failure; and malformed/overnight date-time behavior.
- A controlled offline journey that resumes a draft, searches the cached catalog, opens a catalog-backed Guide, saves once, reloads, and verifies the IndexedDB workout remains available.

Tests block service workers to prevent an older local cache from mixing assets between runs. Vibration is replaced with a harmless browser stub. Downloads, uploads, and confirmation dialogs are handled through Playwright. Fixtures use the same explicit production storage interfaces as the app.

## Commands

- `npm run serve` starts the static server at `http://127.0.0.1:4175`.
- `npm run lint` checks JavaScript for likely defects and guards the reduced transitional-global list.
- `npm run format:check` checks the adopted tooling, tests, workflow, and documentation files.
- `npm run catalog:refresh` is a manual, networked development command that fetches and deterministically regenerates the pinned provider snapshot. It is not part of startup or CI.
- `npm run catalog:validate` validates the committed snapshot entirely offline and reports record, byte, gzip, and revision metadata.
- `npm run test:unit` runs direct domain tests only.
- `npm run test:e2e` runs the headless Chromium workflow suite only.
- `npm run test` runs unit tests, then browser tests.
- `npm run test:e2e:ui` opens Playwright UI mode for interactive browser debugging.
- `npm run check` runs every merge-blocking validation used by CI: lint, formatting, unit tests, and browser tests.

When a browser test fails, inspect `playwright-report/index.html` with `npx playwright show-report`. CI uploads the HTML report, traces, screenshots, and retained failure videos when available. Locally, rerun one test with `npx playwright test tests/e2e/workout.spec.js --debug` or use UI mode.

If the managed Playwright browser cannot launch on Windows, set `PLAYWRIGHT_CHROME_PATH` to an installed Chrome executable before running the existing commands. The config uses this optional path only when it is supplied; CI and normal environments retain Playwright's managed browser.

## Manual coverage

The automated suites do not replace [QA_CHECKLIST.md](QA_CHECKLIST.md). Offline install/startup, installed-PWA behavior, device vibration, file handling outside Chromium, real keyboard behavior, and Android browser layout/performance remain manual. These checks depend on an installed PWA, physical hardware, or platform behavior that headless CI cannot represent reliably.

The complete audit, exact hard limits and warning thresholds, failure behavior, and remaining risks are recorded in [BETA_HARDENING.md](BETA_HARDENING.md).

On the Samsung Galaxy S24 Ultra, repeat the Home, Active Workout, Exercise Details scroll/action/keyboard checks; collapsed filter/count/reset and short local/catalog hierarchy; preview-versus-full-Guide behavior; exact/alias/custom/history name resolution; draft reload; save/edit; legacy startup migration; failed-migration recovery; backup clear/restore; offline catalog/Guide reload; haptic; and portrait-layout checks from the release checklist.

Formatting adoption covers tooling, catalog modules/data, tests, workflow, and documentation. Existing unrelated production HTML, CSS, and JavaScript are not reformatted as part of this spike.
