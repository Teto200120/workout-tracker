# Exercise Catalog Spike

Research and prototype status as of July 13, 2026.

## Outcome

Use a provider-neutral internal contract with a reviewed, versioned snapshot of [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) for this friends-and-family prototype. The deployed PWA loads the committed JSON asset, searches and resolves it in the browser, and caches it with the existing service worker. It does not call an exercise provider at runtime.

This is the safest prototype direction because it is keyless, static-host compatible, offline-capable, reversible, and independent from provider uptime. The adapter, not the picker or Guide, owns the Free Exercise DB field mapping. Selecting a catalog entry passes only its canonical name through the existing active-workout command. When Exercise Details opens, the saved name is resolved against an in-memory catalog index and may receive a catalog-backed Guide for that render only. No catalog ID, resolution result, provider object, instruction, image reference, muscle, or equipment value is written to a workout, routine, set, draft, backup, IndexedDB, or application localStorage key.

Recommendation: merge the spike for friends-and-family testing after the manual device checks in this document. Before commercial distribution, perform a separate legal/provenance review, especially for the provider's inherited text and image sources. Images are deliberately excluded from this prototype.

## Evidence labels

- **Verified**: stated by an official repository, license, schema, API documentation, pricing page, or terms page reviewed on July 12, 2026.
- **Observed**: measured from the pinned dataset, an official public endpoint, response headers, or this implementation.
- **Inference**: an engineering conclusion drawn from verified or observed evidence.
- **Unknown**: the official material reviewed did not answer the question clearly enough; the spike does not guess.

Pricing, quotas, and provider terms can change. Re-check every linked provider page before committing to a paid integration or commercial release.

## Current Add Exercise audit

### Entry and dismissal

`router.js` binds `#addExercise` to `bindExercisePicker`. The component receives two commands rather than importing active-workout state: `getCurrentWorkoutExerciseNames` and `addExerciseToWorkout`. It opens the native modal dialog, focuses the search input on the next animation frame, closes from Cancel, the close button, Escape, or a true backdrop click, and restores focus to `#addExercise` after close. A `selecting` guard prevents double activation.

### Local option collection

Before the spike, `buildExerciseOptions` collected names in this precedence order:

1. Built-in default routines.
2. Saved routines through `getRoutines()`.
3. Completed workout history through `getWorkouts()`.
4. Exercises already in the current workout.

Names are trimmed, empty values are dropped, and a lowercase key provides case-insensitive deduplication. The first spelling/capitalization wins, then the final local list is sorted by name. Existing local data remains the first source of truth in the expanded picker.

### Selection and persistence

The picker calls `addExerciseToWorkout(name)`. That command trims the name, creates the existing exercise DOM with `makeExercise({ name })`, appends it, updates the existing history hint, opens it, and saves the draft.

`collectWorkout()` still serializes each exercise as:

```js
{
  (name, notes, sets);
}
```

The same collector supplies both saved workouts and draft recovery. Therefore a catalog selection can safely persist only the canonical exercise name without any application-schema change. Custom exercises continue through the same name-only command and do not acquire a catalog identity.

### Behavior preserved

- Local routines, history, and current-workout names remain available.
- Local canonical capitalization wins a local/catalog name collision.
- Create New Exercise remains available during catalog load and failure.
- Cancel, close, Escape, backdrop dismissal, search focus, focus restoration, keyboard activation, draft saving, and double-tap protection remain.
- Exercise Details keeps its existing Log/Guide navigation, generic fallback, notes behavior, scroll ownership, and action dock. A safely matched name enriches only the rendered Guide.

## Provider comparison summary

| Candidate              | Prototype fit                                           | Primary reason                                                                                                                 |
| ---------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Free Exercise DB       | Recommended                                             | Open/keyless repository snapshot works with static hosting and offline caching.                                                |
| wger                   | Revisit for localization or community-maintained detail | Public API is capable and multilingual, but licenses are attached per entry and need a license-aware ingestion policy.         |
| API Ninjas Exercises   | Reject for this prototype                               | A browser API key would be exposed; the reviewed Developer paid tier also disallows data caching/storage.                      |
| ExerciseDB / AscendAPI | Reject for offline bundling                             | Current official terms reserve the data and prohibit persistent storage; the free API is non-commercial and attribution-bound. |

## Candidate 1: Free Exercise DB

| Field                  | Finding                                                                                                                                                                                                                            |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Provider/project       | **Verified:** `yuhonas/free-exercise-db`, maintained by yuhonas and contributors.                                                                                                                                                  |
| Official source        | **Verified:** [repository](https://github.com/yuhonas/free-exercise-db), [schema](https://github.com/yuhonas/free-exercise-db/blob/main/schema.json), [license](https://github.com/yuhonas/free-exercise-db/blob/main/LICENSE.md). |
| License                | **Verified:** repository declares the Unlicense/public-domain dedication.                                                                                                                                                          |
| Commercial use         | **Verified:** the license text permits use and distribution for commercial or non-commercial purposes. This is not a guarantee about third-party provenance.                                                                       |
| Attribution            | **Verified:** not required by the Unlicense text. **Prototype choice:** retain visible source attribution anyway.                                                                                                                  |
| Authentication         | **Verified:** none for repository files.                                                                                                                                                                                           |
| Safe browser access    | **Inference:** keyless access is technically safe, but the app intentionally does not rely on raw GitHub at runtime.                                                                                                               |
| Backend proxy          | **Inference:** not required for a reviewed snapshot.                                                                                                                                                                               |
| Exercise count         | **Observed:** 873 records at pinned commit `b0eed061e1c832b3ed815fbaa4b45b3cdc14df49`.                                                                                                                                             |
| Search                 | **Verified:** no formal search API; the repository has a browsable frontend. **Prototype:** local exact/prefix/substring search.                                                                                                   |
| Pagination             | **Verified:** not applicable to the combined JSON file.                                                                                                                                                                            |
| Rate limits            | **Unknown:** GitHub hosting limits are not treated as a product API contract. Runtime does not depend on them.                                                                                                                     |
| Free/paid restrictions | **Verified:** no provider plan is involved. GitHub's platform terms still apply to refresh traffic.                                                                                                                                |
| Fields                 | **Verified:** ID, name, force, level, mechanic, equipment, primary muscles, secondary muscles, instructions, category, and image paths.                                                                                            |
| Equipment/muscles      | **Verified:** schema enums define equipment and muscle taxonomies.                                                                                                                                                                 |
| Instructions           | **Observed:** present as arrays; 5 of 873 records have no instruction steps.                                                                                                                                                       |
| Safety information     | **Observed:** no separate structured safety field. Some instructions contain cues, but they are not treated as medical advice.                                                                                                     |
| Images/animations      | **Verified:** image paths exist; the repository README describes local/raw-GitHub use.                                                                                                                                             |
| Image licensing        | **Unknown:** the repository license is verified, but individual inherited image provenance and model/property considerations were not independently established. No images or image paths ship in the prototype.                   |
| Localization           | **Observed:** English names/instructions only in the pinned snapshot; no locale field.                                                                                                                                             |
| CORS                   | **Unknown:** not relied on or claimed.                                                                                                                                                                                             |
| Offline strategy       | **Prototype:** normalize once, commit static JSON, cache it in app shell v17, search on-device.                                                                                                                                    |
| Update frequency       | **Observed:** current `main` commit is May 24, 2026; much of the dataset history predates it. Updates are maintainer-driven, not a guaranteed release cadence.                                                                     |
| Data quality           | **Observed:** 29 null force, 87 null mechanic, 77 null equipment, and 5 empty instruction arrays. Some instruction prose contains grammar/formatting issues.                                                                       |
| Duplicate names        | **Observed:** no exact case-insensitive duplicate names at the pinned revision. Adapter deduplication remains defensive.                                                                                                           |
| Stability              | **Inference:** a pinned snapshot is stable even if upstream changes; schema or provenance can still change on refresh.                                                                                                             |
| Vendor lock-in         | **Inference:** low because provider fields stop at the adapter boundary.                                                                                                                                                           |
| Static PWA             | Recommended.                                                                                                                                                                                                                       |
| Future Android         | Suitable as a packaged asset behind the same contract; do not reuse web-specific cache code.                                                                                                                                       |

## Candidate 2: wger

| Field                  | Finding                                                                                                                                                                                 |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Provider/project       | **Verified:** [wger-project/wger](https://github.com/wger-project/wger), a community FLOSS workout platform.                                                                            |
| Official source        | **Verified:** [repository](https://github.com/wger-project/wger), [API documentation](https://wger.readthedocs.io/en/latest/api/api.html), and public `/api/v2/` endpoints.             |
| License                | **Verified:** application code is AGPL-3.0-or-later; documentation is CC-BY-SA-4.0; exercise/ingredient data uses Creative Commons licenses recorded per entry.                         |
| Commercial use         | **Inference:** possible only according to each entry's Creative Commons license and obligations. A blanket answer would be unsafe.                                                      |
| Attribution            | **Verified/inference:** depends on each entry's license and author metadata; a snapshot importer must retain both.                                                                      |
| Authentication         | **Verified:** public exercise endpoints are accessible without authentication; user-owned routines require authentication.                                                              |
| Safe browser access    | **Observed:** the public exercise endpoint returned `Access-Control-Allow-Origin: *` for a sample cross-origin request on July 12, 2026. No secret is needed for public exercise reads. |
| Backend proxy          | **Inference:** not required for a public read, but a controlled ingestion job is preferable for licensing, stability, and snapshot review.                                              |
| Exercise count         | **Observed:** `/api/v2/exerciseinfo/?limit=1` reported 821 entries on July 12, 2026. Counts can change.                                                                                 |
| Search                 | **Verified:** REST API supports filtering and ordering; exercise detail includes translations and taxonomy objects.                                                                     |
| Pagination             | **Verified:** API returns paginated `count`, `next`, `previous`, and `results` structures.                                                                                              |
| Rate limits            | **Unknown for the public exercise list:** official documentation includes rate-limiting guidance, but this review did not establish a stable exact public-exercise quota.               |
| Free/paid restrictions | **Verified:** project and public data API are not presented as a paid SaaS catalog plan. Hosting availability is still best-effort.                                                     |
| Fields                 | **Observed:** IDs/UUIDs, timestamps, category, primary/secondary muscles, equipment, license/author, images, translations, variation group, videos, and author history.                 |
| Equipment/muscles      | **Verified:** structured linked taxonomies.                                                                                                                                             |
| Instructions           | **Verified:** localized descriptions live in translations.                                                                                                                              |
| Safety information     | **Unknown:** no consistently structured safety field was verified.                                                                                                                      |
| Images/animations      | **Verified:** image/video relationships exist.                                                                                                                                          |
| Image licensing        | **Inference:** must be evaluated per entry along with license and author fields; not safe to flatten into one blanket license.                                                          |
| Localization           | **Verified:** multilingual translations and Weblate community workflow.                                                                                                                 |
| CORS                   | **Observed:** wildcard origin on the tested public endpoint.                                                                                                                            |
| Offline strategy       | **Inference:** ingest a license-filtered snapshot with retained attribution and revision metadata, or self-host wger.                                                                   |
| Update frequency       | **Observed:** active project with current releases and API documentation. No catalog SLA was identified.                                                                                |
| Data quality           | **Inference:** community translations and per-entry completeness can vary; taxonomy is richer than the prototype dataset.                                                               |
| Duplicate names        | **Inference:** translations and variations require identity rules beyond name-only deduplication.                                                                                       |
| Stability              | **Inference:** versioned API is stronger than raw data hosting, but entry changes and per-entry licensing require refresh review.                                                       |
| Vendor lock-in         | Moderate without an adapter; low behind this spike's normalized contract.                                                                                                               |
| Static PWA             | Possible, but license-aware snapshot generation is more complex than Free Exercise DB.                                                                                                  |
| Future Android         | Strong candidate if localization, richer media, or community updates become priorities.                                                                                                 |

## Candidate 3: API Ninjas Exercises

| Field                  | Finding                                                                                                                                                                       |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Provider/project       | **Verified:** [API Ninjas Exercises API](https://api-ninjas.com/api/exercises), operated by API Ninjas.                                                                       |
| Official source        | **Verified:** endpoint documentation and [pricing/usage page](https://api-ninjas.com/pricing).                                                                                |
| License                | **Verified:** proprietary service terms/plan rights, not an open dataset license.                                                                                             |
| Commercial use         | **Verified:** current paid plans shown on the pricing page allow commercial use. Re-check before purchase.                                                                    |
| Attribution            | **Verified:** current paid-plan table says attribution is not required.                                                                                                       |
| Authentication         | **Verified:** `X-Api-Key` is required.                                                                                                                                        |
| Safe browser access    | Rejected. A shared API key in static browser code is not secret.                                                                                                              |
| Backend proxy          | Required for any production use that protects the key. Not implemented in this branch.                                                                                        |
| Exercise count         | **Verified:** official endpoint page says over 3,000 exercises.                                                                                                               |
| Search                 | **Verified:** name, type, muscle, difficulty, and equipment filters.                                                                                                          |
| Pagination             | **Verified:** the search endpoint returns up to five results; offset is premium. The all-exercises endpoint has limit/offset and is restricted to qualifying subscriptions.   |
| Rate limits/quotas     | **Verified at plan level:** usage allowances vary by plan. Exact current numbers are intentionally not copied here; consult the official pricing page.                        |
| Free-tier restrictions | **Unknown from the official pages reviewed:** a key is required and some fields/offsets are premium, but a stable complete free-tier catalog entitlement was not established. |
| Paid-tier restrictions | **Verified:** current Developer paid tier disallows data caching/storage; current higher paid tiers shown allow it. Plan and company-size conditions apply.                   |
| Fields                 | **Verified:** name, type, primary muscle, difficulty, instructions, equipment, and safety information.                                                                        |
| Equipment/muscles      | **Verified:** structured filter/response data.                                                                                                                                |
| Instructions/safety    | **Verified:** both instruction and safety fields are documented.                                                                                                              |
| Images/animations      | **Unknown:** not documented for the reviewed Exercises response.                                                                                                              |
| Image licensing        | Not applicable to the reviewed response; otherwise unknown.                                                                                                                   |
| Localization           | **Unknown:** no localization contract was verified.                                                                                                                           |
| CORS                   | **Unknown:** irrelevant to the security decision because a browser key would be exposed.                                                                                      |
| Offline strategy       | Requires a plan that explicitly permits storage plus a secure backend ingestion job. Developer-tier caching restrictions conflict with this prototype.                        |
| Update frequency       | **Unknown:** no dataset revision or changelog contract was identified.                                                                                                        |
| Data quality           | **Inference:** structured safety data is attractive, but source provenance and versioned snapshots are not transparent enough for this spike.                                 |
| Duplicate names        | **Unknown:** no bulk sample was obtained without credentials.                                                                                                                 |
| Stability              | Commercial API availability depends on subscription and terms.                                                                                                                |
| Vendor lock-in         | High if provider responses leak into UI/storage; moderate behind this contract.                                                                                               |
| Static PWA             | Poor fit because secrets cannot be protected and offline caching rights depend on plan.                                                                                       |
| Future Android         | Possible only with a secure backend and suitable commercial/storage rights.                                                                                                   |

## Candidate 4: ExerciseDB / AscendAPI

| Field                  | Finding                                                                                                                                                                                                                               |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Provider/project       | **Verified:** current ExerciseDB API materials identify AscendAPI as operator/owner.                                                                                                                                                  |
| Official source        | **Verified:** [free API documentation](https://oss.exercisedb.dev/docs), [API terms](https://exercisedb.notion.site/ExerciseDB-API-Terms-of-Use-226983b728ca8090bf7be79564e4b356), and [developer site](https://exercisedb.dev/docs). |
| License                | **Verified:** proprietary, all-rights-reserved API terms; not an open dataset license.                                                                                                                                                |
| Commercial use         | **Verified:** free API documentation limits the free service to non-commercial/community/prototype uses; commercial use requires a paid route.                                                                                        |
| Attribution            | **Verified:** free API documentation requires AscendAPI credit.                                                                                                                                                                       |
| Authentication         | **Verified/observed docs:** the free v1 quick-start is presented as keyless; paid/RapidAPI surfaces require credentials.                                                                                                              |
| Safe browser access    | A keyless non-commercial request may be technically possible, but a live-only dependency is not local-first. Paid keys must not be shipped in the browser.                                                                            |
| Backend proxy          | Required for protected paid access.                                                                                                                                                                                                   |
| Exercise count         | **Verified:** free API documentation advertises roughly 1,500 structured exercises.                                                                                                                                                   |
| Search/pagination      | **Verified:** endpoint documentation advertises structured exercise retrieval and pagination; exact behavior should be rechecked before integration.                                                                                  |
| Rate limits            | **Verified:** free documentation says strict limits apply. **Unknown:** exact current limits were not established.                                                                                                                    |
| Free-tier restrictions | **Verified:** non-commercial use, attribution, limited media, and strict rate limits.                                                                                                                                                 |
| Paid-tier restrictions | **Verified:** subscriber rights are revocable and tied to subscription under current terms.                                                                                                                                           |
| Fields                 | **Verified:** exercise ID, name, GIF URL, target/secondary muscles, body parts, equipment, and instructions.                                                                                                                          |
| Instructions/safety    | Instructions are present; a separate safety field was not verified.                                                                                                                                                                   |
| Images/animations      | **Verified:** GIF media is included.                                                                                                                                                                                                  |
| Image licensing        | **Verified:** proprietary/limited-use, not an open asset license.                                                                                                                                                                     |
| Localization           | **Unknown:** no localization contract was verified.                                                                                                                                                                                   |
| CORS                   | **Unknown:** not relied on.                                                                                                                                                                                                           |
| Offline strategy       | Rejected. Current terms prohibit persistent local/server storage and cache beyond a short operational window.                                                                                                                         |
| Update frequency       | **Unknown:** no snapshot revision contract was established.                                                                                                                                                                           |
| Data quality           | Richer media and taxonomy, but provenance cannot be independently reviewed as an open dataset.                                                                                                                                        |
| Duplicate names        | **Unknown:** not evaluated through a permitted bulk snapshot.                                                                                                                                                                         |
| Stability              | Terms and subscription continuity directly control continued rights.                                                                                                                                                                  |
| Vendor lock-in         | High for media/content; storage prohibition prevents a durable offline package.                                                                                                                                                       |
| Static PWA             | Poor fit for this objective.                                                                                                                                                                                                          |
| Future Android         | Poor fit for offline-first packaging; possible only through a terms-compliant live backend.                                                                                                                                           |

## Why the alternatives were not selected

- wger is the strongest future alternative, especially for localization and maintained taxonomies. It was not selected for the first prototype because per-entry licenses and translated identities require a license-aware importer and more product decisions than this reversible spike needs.
- API Ninjas requires a secret-bearing key and its currently documented Developer storage rights conflict with a committed offline snapshot. A serverless proxy alone would not solve caching rights.
- ExerciseDB/AscendAPI's current storage prohibition conflicts directly with the service-worker/offline requirement. Its free API also cannot be assumed suitable for later commercial use.

Revisit the decision if Free Exercise DB quality is not acceptable in testing, commercial review raises provenance concerns, localization becomes necessary, rich safety fields become a product requirement, or the app gains an approved secure backend and budget for licensed content.

## Normalized catalog contract

Every usable runtime entry has this provider-independent shape:

```js
{
  catalogId,        // deterministic `${normalizedSource}:${encodedSourceId}`
  source,           // normalized internal source key
  sourceId,         // provider identity, used only in disposable catalog data
  name,             // trimmed canonical display name
  category,         // string or null
  difficulty,       // string or null
  force,            // string or null
  mechanic,         // string or null
  equipment,        // always a deduplicated string array
  primaryMuscles,   // always a deduplicated string array
  secondaryMuscles, // always a deduplicated string array
  instructions,     // always a deduplicated string array
  imageReferences,  // always an array; empty in this prototype
  attribution: {
    label,
    url
  },
  license
}
```

Required minimum provider inputs are a stable source ID and non-empty name. Missing optional scalar values become `null`; missing list values become `[]`. Names and strings are trimmed. Normalization is pure and does not mutate provider input. Unknown provider fields and upstream image paths are not copied. IDs, records, and names are deduplicated deterministically after normalized name/ID sorting.

The contract is runtime/disposable catalog data, not application data schema 2. The UI imports only provider-neutral loader/search functions. Provider-specific field names are confined to `free-exercise-db-adapter.js` and the development refresh script.

## Catalog modules and interface

- `catalog-contract.js`: normalization, deterministic IDs, metadata validation, tolerant runtime parsing, and strict offline validation.
- `free-exercise-db-adapter.js`: provider field mapping, malformed-record reporting, and deterministic provider deduplication.
- `catalog-search.js`: exact/prefix/substring ranking, filters, local/catalog merge, canonical-name resolution, and filter option extraction.
- `exercise-aliases.js`: the small human-reviewed map from existing provider-neutral app names to deterministic catalog IDs.
- `exercise-catalog-resolver.js`: safe name normalization, exact/alias indexes, deterministic ambiguity handling, and structured match results. It is pure and has no DOM or storage access.
- `exercise-guide-adapter.js`: pure provider-neutral Guide/preview adaptation, instruction cleanup, reminders, and attribution construction.
- `catalog-loader.js`: static fetch, in-memory status, failure isolation, lookup, provider-neutral search access, and one resolver index per successfully loaded snapshot.

The runtime boundary exposes `loadCatalog`, `searchCatalog`, `getCatalogExercise`, `getCatalogStatus`, `getCatalogMetadata`, and `resolveLoadedCatalogExercise`, plus narrow pure helpers used by the picker and Guide. One malformed exercise is skipped and reported; an invalid envelope or zero usable records makes the catalog unavailable without affecting local exercise creation or generic guides.

## Runtime data flow

```text
committed Free Exercise DB revision
        |
        | development-only npm run catalog:refresh
        v
provider adapter + strict validation
        |
        v
src/data/exercise-catalog.json
        |
| static HTTP + service-worker app shell v17
        v
catalog loader -> pure local search -> Add Exercise picker
       |                              |
       |                              | name only
       |                              v
       |                    existing addExerciseToWorkout
       |                              |
       |                              v
       |                   existing { name, notes, sets }
       |
       +-> one in-memory resolver index <- saved exercise name
                                      |
                                      v
                         optional catalog Guide render
```

No exercise search, workout history, routine, draft, or personal data leaves the device. The only provider network access is the manually invoked development refresh command.

## Snapshot and size

| Item                      | Value                                      |
| ------------------------- | ------------------------------------------ |
| Source revision           | `b0eed061e1c832b3ed815fbaa4b45b3cdc14df49` |
| Source commit date        | May 24, 2026                               |
| Normalized exercise count | 873                                        |
| Catalog format            | 1                                          |
| Normalizer version        | 1                                          |
| Uncompressed asset        | 1,229,608 bytes                            |
| Approximate gzip transfer | 170,775 bytes                              |
| Images                    | Excluded                                   |

Included fields: source identity inside the disposable catalog, name, category, difficulty, force, mechanic, equipment, primary/secondary muscles, instructions, attribution, and license.

Excluded fields: provider unknown fields, raw provider records, upstream image paths/binaries, safety claims not present in the source, translations, recommendations, analytics, and every catalog field from saved user records/backups.

Development measurements on this Windows/Node environment (single runs, not CI promises): JSON parse about 2.9 ms, average filtered Node search about 0.12 ms over 1,000 iterations, and approximate post-GC heap increase about 2.2 MB. Final system-Chrome QA observed a roughly 4.7 ms same-origin catalog resource load, about 177 ms from picker activation to catalog-ready mobile state (412 x 915), about 90 ms at 1280 x 800, and about 0.10 ms average browser search over 1,000 iterations. The interaction remained responsive without debounce, fuzzy-search dependencies, virtualization, or image requests. Results are capped at 60 catalog rows per render; the total match count remains visible.

## Search, filters, and deduplication

- Query comparison is trimmed and case-insensitive.
- Exact names rank first, then prefixes, then substrings; ties use stable name and catalog-ID ordering.
- Empty query returns deterministic alphabetical catalog results.
- Primary muscle, equipment, and category filters are exact normalized matches and apply only to catalog results. Local matching results remain visible even while filters are active.
- Local options render before catalog loading completes.
- Mobile filters are collapsed behind one labeled button by default. The button exposes `aria-expanded`, displays the active-filter count, and keeps Reset in the expanded panel. Desktop uses the same disclosure with a wider three-column panel.
- Empty and search states show at most three local rows before the catalog section, followed by an explicit Show All control when more local names are available. Local exact matches still rank first.
- A case-insensitive local/catalog name collision renders once using the local spelling/capitalization.
- Creating a custom exercise uses a matching local name first, then matching catalog capitalization, otherwise the trimmed custom text.
- Catalog result rows show only name, one primary-muscle value, equipment, and a quiet Catalog label.

## Preview behavior

Catalog rows open a nested preview inside the existing dialog. It shows only available equipment, primary muscles, difficulty/category, at most the first two instruction steps, a count of the remaining Guide steps, quiet source attribution, and an Add to Workout action. The preview always withholds at least one available step, so it cannot duplicate the full Guide. The user can return to results with Back. Preview content is the single contained vertical scroller; the document remains locked behind the modal. Missing values do not create empty labels. Provider instructions are never copied into saved notes.

## Resolver and existing-exercise enrichment

Resolution is conservative and ordered:

1. Normalize the saved name by trimming, collapsing whitespace, comparing case-insensitively, applying Unicode compatibility normalization, removing apostrophe differences, and treating safe dashes/separators as spaces.
2. Use an exact normalized-name index only when exactly one catalog ID owns the normalized name.
3. If no exact name exists, consult the reviewed alias map and require exactly one present target ID.
4. Otherwise return `unmatched`; no fuzzy or high-confidence similarity layer is enabled in this branch.

The resolver returns `exact`, `alias`, or `unmatched` with the catalog record, internal confidence metadata, and a diagnostic `matchedBy` value. Confidence is not shown to users. Duplicate normalized catalog names, duplicate alias targets, missing alias targets, empty names, malformed records, and missing catalogs fail closed.

The initial alias set is deliberately small and based on existing app names whose target movement/equipment was reviewed against the snapshot. To add an alias, review the target's name, equipment, muscles, and instructions; add one provider-neutral `localName` plus one existing deterministic `catalogId`; explain any non-obvious equivalence in a comment; and run unit, catalog validation, browser, offline, and no-rewrite checks. Do not add aliases for broad names such as Bench Press, Incline Press, Squat, Shoulder Press, Row, Lat Pulldown, Calf Raise, or Shrugs without enough local specificity to choose one variant safely.

Saved names are inputs to resolution, never migration targets. Default routines, user routines, active drafts, completed history loaded for editing, manually created exercises, and shared/friend-created names all pass through the same render-only path. Stats grouping and historical names therefore remain unchanged.

## Catalog-backed Exercise Guide

A successful exact or alias resolution with non-empty instructions is adapted into the existing Exercise Details Guide surface. The local saved name remains the screen title. The Guide shows a quiet Catalog Guide label, available equipment/muscle/category/difficulty cards, a semantic heading, an ordered How to perform it list in provider order, broadly useful app reminders, and source/license attribution at the bottom. An alias may show the reviewed canonical Guide name for transparency without editing the local name.

Provider instruction text is trimmed, blank steps are removed, order is preserved, and all provider-derived strings are inserted with DOM `textContent`. Static app-owned SVG icons are the only Guide fragments inserted as markup. If the catalog is unavailable, resolution is ambiguous/unmatched, or the matched record has no usable instructions, Exercise Details silently renders the existing generic guide with its current equipment inference, tips, and mistakes. No catalog content is copied into Exercise Notes.

## Offline and failure behavior

- Local options and custom creation do not wait for catalog success.
- A compact live status distinguishes loading, ready, malformed-record skips, and unavailable states.
- An invalid envelope, failed request, or zero usable records results in a non-blocking unavailable state.
- A malformed individual record is skipped while valid records remain searchable.
- `exercise-catalog.json` and every production catalog, onboarding, and shared-picker module are in service-worker cache `hector-workout-tracker-pwa-v17`.
- After one controlled online load, automated coverage reloads offline, resumes the draft, searches the cached catalog, and opens a catalog-backed Guide for an existing alias name.
- If the cached asset cannot be loaded or parsed, local/custom picking and the generic Guide remain available without an error surface.
- The catalog is disposable app-shell data, not IndexedDB user data and not part of backup export/import.

## Refresh and review process

Run:

```powershell
npm.cmd run catalog:refresh
npm.cmd run catalog:validate
```

`catalog:refresh` fetches official GitHub commit metadata, then fetches the combined source JSON pinned to that returned commit. It normalizes, validates, sorts, deduplicates, records the revision/commit date, reports skipped/duplicate counts and byte sizes, writes a temporary file, and replaces the committed snapshot only after full success. Normal app startup and CI do not run this network command.

`catalog:validate` is offline. It strictly validates the committed envelope, every deterministic record, duplicate handling, metadata count, revision metadata, and image exclusion. Review a refresh by checking source revision, record counts, skipped/duplicate reports, licensing/provenance changes, data-quality changes, asset size, and the generated JSON diff in GitHub Desktop.

The generation timestamp intentionally uses the source commit timestamp so repeated refreshes of the same revision are deterministic.

## Security and privacy

- No API key, secret, account, authentication, cloud storage, tracking, or analytics was added.
- No provider receives workout history, routines, search text, or any personal data.
- No paid or secret-bearing API is called from the browser or GitHub Actions.
- Provider content is treated as display data, not executable markup. The picker builds provider text with DOM `textContent`.
- External source links use `noreferrer` and open only on explicit user action.
- A future secret-bearing provider requires an approved backend/serverless boundary, server-side key storage, request controls, provider terms that permit caching, and a privacy review.

## Schema and backup confirmation

- IndexedDB database version remains `2`.
- Application data-schema version remains `1`.
- Backup-file version remains `3`.
- Database/store/index names are unchanged.
- Workout, exercise, set, routine, draft, settings, goals, and backup envelope formats are unchanged.
- Catalog records are not stored in IndexedDB, localStorage, drafts, or backups.

Potential future application-schema fields—catalog identity, provider/source, custom/catalog discriminator, durable instruction/version references, media references, sync state, ownership, and Android-only fields—remain deferred. Add them only when a product requirement makes durable identity essential and a versioned migration is designed.

## Validation coverage

Direct Node tests cover provider normalization, missing optionals, malformed records, no input mutation, deterministic IDs, case-insensitive provider deduplication, unknown-field exclusion, metadata/envelope validation, skipped records, unavailable fallback, ranking, empty queries, muscle/equipment/category filters, stable ordering, local precedence, local/catalog merging, custom canonical matching, safe resolver normalization, reviewed/missing aliases, duplicate-name ambiguity, unsafe press/pulldown/row separation, Guide adaptation, instruction order/cleanup, attribution, preview truncation, and source non-mutation.

Playwright covers delayed catalog loading with immediate local options, compact filter disclosure/count/reset, limited local hierarchy with Show All, search-driven catalog access, no horizontal overflow, keyboard/focus behavior, exact/local duplicate handling, shortened preview/Back/Add/contained scrolling, name-only draft persistence, catalog-selected Guides, exact default-routine enrichment, alias enrichment after draft recovery, completed-history render-only enrichment, custom/broad-name generic fallback, malformed-record tolerance, invalid-envelope fallback, app-shell HTTP status, cached offline catalog search, and an offline catalog-backed Guide.

## Known data-quality and legal questions

- The source has incomplete optional classifications and five entries without instructions.
- Instruction quality and terminology vary and have not received clinical or professional editorial review.
- Names are unique in this revision but may still represent near-duplicates, variants, or inconsistent equipment specificity.
- Exact normalized names can still be semantically broad. Ambiguous app names deliberately remain on the generic Guide unless a reviewed alias is added.
- Singular/plural, token-order, equipment-word removal, and fuzzy similarity are not automatic matching rules. This creates intentional false negatives.
- Alias correctness depends on human review and can drift when a future snapshot changes a target record; catalog refresh review must re-run alias-target and browser tests.
- Repository license text is verified; inherited text/image provenance has not received formal legal review.
- Images remain excluded until source-by-source reuse rights, attribution, privacy/model concerns, and commercial suitability are formally reviewed.
- The prototype makes no warranty about exercise safety, medical suitability, or commercial rights.

## Deferred work

### Secure backend

- Secret-bearing paid APIs, proxying, key rotation, quotas, abuse controls, and commercial entitlement checks.
- Scheduled provider ingestion or automated catalog publication.
- License-aware wger ingestion or terms-compliant paid content caching.

### Friends-and-family testing

- Name quality, near-duplicate confusion, missing common exercises, filter usefulness, instruction readability, and preview discoverability.
- Whether the documented 200-character query boundary and 60 visible catalog-result cap feel generous without slowing repeated filtering.
- Whether category and difficulty are useful enough to retain in the picker.
- Whether users encounter enough safe false negatives to justify a future manual Link Exercise Guide workflow. No mapping UI or confidence score is included now.

The beta-hardening pass now rejects overlong search/custom names, caps provider-neutral record/instruction metadata before rendering, keeps catalog failure on the existing local/custom fallback, and preserves the existing one-selection guard when Add is tapped twice. These are runtime safety boundaries only: selected exercises still persist only the existing name/notes/sets shape. See [BETA_HARDENING.md](BETA_HARDENING.md) for the exact limits and adversarial journeys.

### Future schema changes

- Durable catalog identity, a custom/catalog discriminator, manual mappings, pinned instruction revisions, sync/ownership, or media references.
- Any such field requires a new application-schema and backup-file design, explicit migration/rollback behavior, compatibility tests, and a product reason to persist identity. This branch intentionally performs no migration.

### Android

- Package the normalized asset through an Android-specific repository/cache boundary.
- Reuse the contract and adapter concepts, not service-worker or DOM code.
- Decide resource packaging, background refresh, localization, accessibility, and device storage separately.
- Do not add Android-only fields to web schema 2.

## Manual Samsung Galaxy S24 Ultra checks

- Install/update to cache v17 online, enable airplane mode, cold-start, resume a draft, and search `Air Bike` from Active Workout and Routines.
- Confirm local results and Create New Exercise remain usable while catalog loading is delayed or unavailable.
- Confirm the filter panel is collapsed by default, its active count is announced, Reset works, and opening/closing it does not crowd or horizontally overflow the dialog.
- With an empty query, confirm three local rows plus Show All keep local names accessible and the catalog heading/first row reachable. Search `Air Bike` and confirm a catalog result appears immediately.
- Open preview, confirm only a short instruction preview appears, use Back, reopen, and add the exercise at default display scaling.
- Confirm the preview is the only vertical scroller, the software keyboard does not cover search/filter controls, and no horizontal overflow occurs.
- Open the added exercise's Guide and confirm full ordered provider steps, metadata, attribution, and unchanged empty Exercise Notes.
- Start a routine containing `Romanian Deadlift`, resume a draft containing `V-Bar Lat Pulldown`, and edit completed history containing an exact name; confirm each gains the correct Guide while its stored name stays unchanged.
- Create `My Saturday Row` and `Bench Press`; confirm both keep the generic Guide and show no catalog attribution.
- Double-tap Add to Workout and confirm one exercise is created.
- Reload/resume and inspect the exercise name, notes, and sets; confirm no provider metadata appears.
- Save/edit the workout, export/import a v3 backup, and confirm schema/database/backup versions remain unchanged.

## Sources consulted

- Free Exercise DB [repository](https://github.com/yuhonas/free-exercise-db), [license](https://github.com/yuhonas/free-exercise-db/blob/main/LICENSE.md), [schema](https://github.com/yuhonas/free-exercise-db/blob/main/schema.json), and pinned combined JSON at commit `b0eed061e1c832b3ed815fbaa4b45b3cdc14df49`.
- wger [repository/licensing summary](https://github.com/wger-project/wger), [API documentation](https://wger.readthedocs.io/en/latest/api/api.html), and public `https://wger.de/api/v2/exerciseinfo/` response/headers observed July 12, 2026.
- API Ninjas [Exercises documentation](https://api-ninjas.com/api/exercises), [pricing and usage rights](https://api-ninjas.com/pricing), and [terms](https://api-ninjas.com/tos).
- ExerciseDB/AscendAPI [free API documentation](https://oss.exercisedb.dev/docs), [developer documentation](https://exercisedb.dev/docs), and [API terms](https://exercisedb.notion.site/ExerciseDB-API-Terms-of-Use-226983b728ca8090bf7be79564e4b356).
