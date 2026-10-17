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

Maintenance locks do not yet cover ordinary workout, routine-edit, preference or draft writes. Full concurrent-writer protection and a durable journal for process termination between IndexedDB and localStorage remain open under F09. Caught-error rollback is not crash atomicity.

F08 still includes truthful post-commit UI failure handling. F10 still includes blocked database upgrades and read-only rescue. Metric semantics/corrections (F05–F06), render/session boundaries (F11–F12), stylesheet cleanup (F13), service-worker consistency (F14), quality/release tooling (F15/F18), feedback delivery (F16–F17), and remaining clutter (F19) are pending. The Android contract/design and native implementation phases remain pending.

## Pass 3: database lifecycle and continuous checks

- F10, partial: blocked opens fail promptly with instructions to close other tracker tabs and reload. An abandoned request aborts a late upgrade or closes a late connection. Version changes close the active connection and require reload; unexpected connection closure also invalidates the handle. Concurrent opens in one module share a promise. Read-only rescue export remains pending.
- F15, partial: `npm run check` now includes the catalog validator and all Cloudflare Worker tests, so the normal main/PR workflow covers them. Broad source-formatting coverage remains pending.
- Local validation: 204 unit tests, 15 Worker tests, lint, formatting and catalog validation pass. Added browser coverage for connection invalidation and reload; browser validation is pending CI.

The concurrency and durable-journal boundaries above remain open. This pass does not make cross-storage operations crash atomic.
