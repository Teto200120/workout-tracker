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

The shared operation boundary now covers application local-storage mutations as well as IndexedDB writers (pass 7). Import, clear and startup migration use durable undo snapshots. Recovery runs before the next outer operation, including another tab's write. Cross-tab guarantees require Web Locks; destructive cross-storage operations fail before mutation if that facility is unavailable. Earlier app versions that bypass the boundary cannot participate.

Routine editor saves/deletes (pass 9), workout/history edits and deletes, restored workout drafts, completion details (pass 10), settings/goal forms and routine-library name/count constraints (pass 11) now have conflict checks. The cooperation and Web Locks boundaries below still apply.
The reviewed F08 commit-versus-view messages are delivered in passes 2, 6 and 8. F10 database lifecycle and read-only rescue are delivered in passes 3 and 5. Metric semantics (F06), render/session boundaries (F11–F12), stylesheet cleanup (F13), service-worker consistency (F14), quality/release tooling (F15/F18), feedback delivery (F16–F17), and remaining clutter (F19) are pending. The Android contract/design and native implementation phases remain pending.

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
- Final validation at `59f38d3`: all 83 browser tests passed without retries, including the backup timing regression and new recovery/concurrency/post-commit tests. All 208 unit tests, 15 Worker tests, lint, formatting and catalog validation passed. The backup test explicitly waits for clear completion before inspecting storage.

## Pass 7: coordinated local writes and durable undo recovery

- Preference, goal, draft, backup-metadata, schema-marker and education writes now return promises and share the database/maintenance coordinator. Callers await persistence before reporting success; education keeps immediate in-memory transitions and resolves its persistence outcome asynchronously. Submitted values are copied before queuing. Draft saves track pending persistence and request browser unload confirmation while a draft write remains pending.
- Physical database version 3 adds a dedicated `recovery` store. Application-schema and backup-file versions are unchanged. Import, clear and startup migration commit an exact original snapshot before changing application records or local strings. They acknowledge completion only after all effects finish and the journal entry is removed.
- Every outer coordinated operation checks the journal under the shared Web Lock. An interrupted operation restores original records, exact local strings and education data before the waiting action can write. Recovery can repeat after another interruption. Failed/unsupported recovery retains the journal and blocks the waiting write, with rescue export available on startup failure. A failed snapshot write prevents the destructive operation from beginning.
- Caught-error compensation removes the journal only after rollback succeeds. Failure to finish rollback or journal cleanup keeps the snapshot for recovery. Browsers without Web Locks may continue ordinary writes when no recovery is pending; import, clear and migrations requiring a snapshot fail before mutation rather than promise cross-tab safety without a lock.
- Tests cover local write ordering/value capture, asynchronous failures, recovery-before-write and fail-closed retries, actual tab termination after database replacement and during local rollback, recovery retry, failed snapshot persistence, and queued cross-tab preference/draft writes.
- Final CI validation at `31c9a90`: all 88 browser tests passed without retries, alongside 214 unit tests, 15 Worker tests, catalog validation, lint and formatting. Existing browser tests now await persisted state or session readiness before inspecting data or reloading.

The durable snapshot covers the reviewed cross-storage maintenance operations. This does not add stale-editor conflict detection or change historical metric semantics. The feedback outbox remains separate from application-data clear/backup, as before.

## Pass 8: preference and backup commit messages

- Settings save/reset and weekly-goal save acknowledge successful persistence even if applying preferences or refreshing the screen fails. Import likewise distinguishes a completed restore from a later UI failure.
- Export distinguishes failure before a download starts, failure to save reminder metadata after starting it, and failure to refresh the status afterward. Messages describe a started download rather than claiming the browser finished saving the file. Download links are cleaned up in all paths; object URLs are released after a grace period.
- Added browser failure-injection coverage for goal refresh, settings application/reset, import refresh, and export metadata/status failures. Service-worker shell cache advances to v57.
- Final CI validation at `a6a7f17`: 214 unit tests, 93 browser tests (no retries), 15 Worker tests, lint, formatting and catalog validation passed. The local browser runner remains unavailable because http-server fails during startup with `uv_interface_addresses`.

Stale-editor conflict detection (F09) remains the next correctness task. These changes do not close the broader performance, organization or Android migration work listed above.

## Pass 9: routine optimistic conflict detection

- Routine editors retain the original saved snapshot. Updates and intentional same-name replacements compare the expected record and write in one IndexedDB readwrite transaction. New IDs expect absence. Library deletes compare the record shown by the rendered card. Conflicts abort without modifying storage and retain the editor draft with an explanatory message.
- The comparison checks complete nested content and identifier types rather than timestamps; object property order does not create false conflicts. Conditional writes still acknowledge only transaction completion and share the maintenance/recovery boundary. Ordinary routine edits preserve original creation time and extension fields.
- Routine additions offered after a workout use a conditional save of the latest routine they read, so a subsequent change cannot be overwritten by that write.
- Added unit coverage for comparison, colliding creates, changed/deleted snapshots and late aborts; browser coverage exercises stale edits across tabs, stale deletes, extension preservation and two competing saves. At `ad16250`, all 219 unit tests and 15 Worker tests passed, alongside lint, formatting and catalog validation. All five new browser tests passed; the 98-test browser suite had one unrelated coach-mark test pass on retry. Shell cache advances to v58.

F09 remains open for workout/history editors and restored edit drafts, settings/goal forms, and library-wide constraints such as simultaneous same-name creates with different IDs. Bulk restore/reset remain intentional replacement operations. Unguarded storage calls retained for those operations and test setup do not provide editor conflict detection.

The coach-mark retry was an observed test race: the overlay can become visible before its asynchronous transition calls `scrollIntoView`. The reduced-motion regression now waits for that observable call instead of reading its capture array immediately after visibility. Final CI validation at `cf8db71`: all 98 browser tests passed without retries, alongside 219 unit tests, 15 Worker tests, lint, formatting and catalog validation.

## Pass 10: workout edits, drafts and completion conflicts

- History edits retain the original saved workout. Saves compare that snapshot and write in one IndexedDB transaction. New workouts expect their ID to be absent; stale updates cannot resurrect a deleted workout or replace a newer record. History deletes compare the snapshot shown by their rendered card.
- New edit drafts retain an optional `editingWorkoutBase` snapshot across save, reload and resume. The snapshot is validated against the complete workout contract and typed ID, and removed from the workout submitted to History. Application-schema and backup-file versions are unchanged. Older historical edit drafts remain readable, but cannot update History without an original snapshot; the user is instructed to retain a copy and reopen the saved workout. Older new-workout drafts still expect absence.
- Completion tags/notes use the saved popup snapshot as their expected version. A conflict preserves entered details, while a new Skip details action allows closing the popup without changing storage. Skip is disabled while a completion save is pending. Completion saves also distinguish committed details from a later refresh failure.
- Added browser regressions for live and restored stale edits after another tab updates/deletes the record, stale History deletes, legacy edit drafts, new-ID collisions, completion conflicts/dismissal and committed completion refresh failures. Existing metadata/typed-ID regressions explicitly finish draft persistence before reload.
- Final CI validation at `67f80fe`: 220 unit tests, 107 browser tests without retries, 15 Worker tests, lint, formatting and catalog validation passed. Connected Browser control is unavailable in this session, and the local http-server runner has the previously recorded startup failure; rendered-flow validation used the repository Playwright workflow with its 412 × 915 mobile Chromium viewport. Native Android/device behavior and a fresh visual screenshot review were not tested. Shell cache advances to v59.

F09 remains open for settings/goal forms and library-wide constraints such as simultaneous same-name routine creates with different IDs. Older app versions and intentionally unguarded bulk replacement APIs retain their documented boundaries. The remaining performance, organization, feedback and native Android work above is unchanged.

## Pass 11: preference snapshots and routine-library constraints

- Settings save/reset and weekly-goal save compare the exact local-storage string captured with the rendered form before writing. Comparison runs inside the shared operation/recovery boundary. Callers capture expected values before queuing and receive the exact newly persisted string, so a successful save advances its own baseline without accidentally adopting another writer's later version. Settings and goals retain extension fields on ordinary saves.
- Conflicting settings/goals remain visible with an explanation. Goal rendering preserves an unsaved value and its original baseline even if another tab changed the saved goal before a background refresh. Successful goal saves update the rendered baseline before refresh.
- Guarded routine saves read the library, compare the expected typed-ID snapshot, enforce normalized-name uniqueness and check the maximum count in one readwrite transaction. Competing creates with different IDs cannot claim the same name or exceed the final slot. Edits at capacity and explicitly confirmed replacements of the same ID remain allowed. Intentional import/reset and unguarded compatibility/test writers retain existing replacement behavior; existing imported duplicates are not merged or deleted.
- These changes cover the remaining reviewed settings/goal and routine-library races under F09. Cross-tab local-storage guarantees still require Web Locks; without them, the shared queue only coordinates the current module/tab. The single shared autosave draft slot retains its existing last-writer policy across separate sessions. Bulk replacement APIs remain intentional replacements, and older app versions cannot participate in the new boundary.
- Added six local-storage unit cases, collection-transaction validation coverage, four preference browser cases and four routine-library browser cases. Local validation: 227 unit tests, 15 Worker tests, catalog validation, lint and formatting pass. Full browser CI validation is pending. The local browser server still fails with `uv_interface_addresses`; connected Browser control is unavailable, so rendered-flow verification uses the repository Playwright workflow. Shell cache advances to v60.

The next review work remains metric semantics (F06), rendering/session separation (F11–F12), CSS/cache/release consistency, feedback delivery, proven clutter and the native Android baseline described above.

## Pass 12: proven clutter, first cleanup

- Removed the obsolete `README.txt` canvas-copy instructions; `README.md` remains the setup guide. Removed the unused `history.js::renderWorkout` export after repository-wide reference checks; the active `renderWorkoutCard` is retained. Simplified profile backup-summary code by removing existence checks on statically imported functions. Removed content remains recoverable in Git history.
- F19 remains partial: the legacy HTML archive, transitional globals, settings ownership and test-injection boundaries still need their respective archival/structural work. No compatibility store, catalog asset or backup adapter was removed.
- Validation shares the pass 11 CI run, including existing History and Profile journeys. No extra tests mirror the deleted code.
