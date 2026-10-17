# Repository review implementation progress

Baseline review: 2026-10-16, `8e0fcb9`. Work stays on `main`; no Android migration or visual redesign has started.

## Pass 1: record integrity

Delivered through `88d7fc1`: F01 transaction completion, F02 typed UI identifiers (including zero), F03 historical metadata and edit-draft recovery, F04 local calendar dates. GitHub validated 191 unit tests and 69 browser tests. Backend tests and catalog validation passed separately.

## Pass 2: routine and recovery safety

- F07: prepare the complete library before reset; apply replacement in one transaction. Starter IDs are deterministic. A catalog failure on bootstrap creates only a marked Custom placeholder; a later successful retry fills missing defaults without replacing user-owned records. Concurrent seed decisions run inside the write transaction. Existing unmarked libraries, including older partial libraries, are left alone because they cannot safely be distinguished from user customization.
- Empty libraries retain the existing bootstrap-defaults policy. This pass does not introduce a user-facing “intentionally empty” setting.
- F09, partial: all database stores are captured in one readonly transaction. Export, import, clear and routine reset use a FIFO maintenance coordinator, plus one shared Web Lock across cooperating tabs. Without Web Locks, serialization is limited to the current tab.
- Clear now replaces database records and prepared defaults atomically, then clears local data. Caught local-storage failures restore the prior database, exact local strings and guidance data.
- F08, partial: recovery messages distinguish failed compensation from a successfully restored snapshot. Failed compensation must never be reported as unchanged data. Clear/reset also distinguish a committed operation from a later view-refresh failure.

Validation covers pure bootstrap decisions, FIFO failure recovery, rollback messaging, middle-write aborts, unavailable catalog/reset preservation, deferred bootstrap retry, concurrent seeds, clear compensation, consistent snapshots and the cross-tab maintenance lock. Run `npm run check`, the Worker tests and `npm run catalog:validate`.

## Remaining boundaries

Maintenance locks now cover ordinary IndexedDB mutations (see pass 5). Synchronous preference, education and draft writes are not yet inside that boundary. Read/modify/write sequences in ordinary editors also need application-level coordination to prevent stale edits. Full concurrent-writer protection and a durable journal for process termination between IndexedDB and localStorage remain open under F09. Caught-error rollback is not crash atomicity.

F08 still includes truthful post-commit UI failure handling. F10 database lifecycle and read-only rescue are delivered in passes 3 and 5. Metric semantics (F06), render/session boundaries (F11–F12), stylesheet cleanup (F13), service-worker consistency (F14), quality/release tooling (F15/F18), feedback delivery (F16–F17), and remaining clutter (F19) are pending. The Android contract/design and native implementation phases remain pending.

## Pass 3: database lifecycle and continuous checks

- F10, partial: blocked opens fail promptly with instructions to close other tracker tabs and reload. An abandoned request aborts a late upgrade or closes a late connection. Version changes close the active connection and require reload; unexpected connection closure also invalidates the handle. Concurrent opens in one module share a promise. Read-only rescue export remains pending.
- F15, partial: `npm run check` now includes the catalog validator and all Cloudflare Worker tests, so the normal main/PR workflow covers them. Broad source-formatting coverage remains pending.
- Validation: 204 unit tests, 76 browser tests and 15 Worker tests passed in CI, along with lint, formatting and catalog validation. Browser coverage includes connection invalidation and reload.

The concurrency and durable-journal boundaries above remain open. This pass does not make cross-storage operations crash atomic.

## Pass 4: independent exercise records

- F05: maximum load, set volume and estimated 1RM now select independently and retain their own source sets and dates. Progress rows and recent estimated-1RM PRs use the matching source set. Ties retain the first encountered source; warm-ups and invalid/nonpositive loaded sets are excluded. Stored workouts are unchanged.
- F06 semantics (including unchecked saved sets, exercise identity and session counting) remain unchanged pending an explicit analytics contract.
- Validation: 206 unit tests, 77 browser tests and 15 Worker tests passed in CI at `c7aa920`, along with lint, formatting and catalog validation. Browser coverage verifies all three metric/source-set pairings.

## Pass 5: shared database writer boundary and rescue export

- F09, partial: ordinary IndexedDB puts, deletes, clears, routine-library changes and bulk writes share the maintenance queue/Web Lock. Startup schema migration also holds that boundary through compensation. Explicit, expiring operation tokens allow only the participating nested database writes to proceed; unrelated writes wait through rollback. There is no global maintenance bypass. Web Locks coordinate cooperating tabs; without them, protection remains limited to this module instance/tab.
- F10: startup failures offer a separate raw recovery download. It reads the existing database without a requested version, using one readonly transaction across its stores; it does not validate, migrate or seed. It retains malformed local JSON as exact strings and includes guidance data. It can read newer database versions and unknown JSON record stores. An absent database creation is aborted. This recovery file is distinct from a normal importable backup; downloading it does not repair or modify persisted data.
- Tests cover queued ordinary writes surviving cross-tab maintenance compensation, token expiration/forgery and failure release, malformed local-data rescue, and rescue of a newer database version.
- Validation at `6b9be56`: lint, formatting, catalog validation, 208 unit tests and 15 Worker tests passed. All new concurrency/rescue browser tests passed.

Full cross-storage crash atomicity remains unfinished: synchronous local writes still require a coordinated API before a durable journal can safely cover them. No durable journal has been added in this pass. Old app versions that do not participate in the shared lock cannot be coordinated by this release.

## Pass 6: committed actions and view failures

- F08: workout save, routine save, workout delete and routine delete now distinguish a database commit from a later UI failure. Committed operations return success and tell the user what was saved/deleted. A workout whose post-save UI failed requires reload before another save, preventing a retry from creating a duplicate while session state is partially reset.
- Browser regressions inject refresh failures after commits and check persisted results, messages and duplicate-retry prevention. Also corrected numeric fixture inputs in the new concurrency/rescue regressions before final browser validation.
- These changes complete the reviewed workout/routine action messages; exported-backup status/render failures and settings view errors still require review before treating F08 as fully closed.
- CI at `6b9be56` completed 83 browser checks: 82 first-pass successes and one existing backup test passing on retry. All new post-commit tests passed. The backup test now explicitly waits for clear completion before inspecting storage; this test-only timing fix is being revalidated.
