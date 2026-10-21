# Completed PWA baseline and Android handoff

Recorded 2026-10-21 UTC / 2026-10-20 Eastern. Repository: `Teto200120/workout-tracker`. Original review baseline: `8e0fcb9` (2026-10-16). PWA baseline signoff: `16da1553c271b2bf04efb16fbd1982050fe12e43`; full checked source: `d5ac9c83839e5e09fc454c83bba73946ec8e6f4b`. Native Android implementation has not started.

## Current conclusion

The finite F01–F19 review is complete within the documented native deferrals. The current PWA is published; Cloudflare migration 0005 and the updated Worker have now been deployed manually. Feedback submission was confirmed in the app and independently observed in D1. No additional general PWA cleanup pass is required before native Android foundation.

This is a bounded baseline signoff, not a claim of exhaustive absence of bugs. Email digest delivery was explicitly left unverified at the user's direction; screenshot submission was not part of the live smoke test. Physical-device update behavior, fresh visual QA and native performance are not covered by the recorded web CI results.

## Work delivered

| Area                          | Completed work and retained behavior                                                                                                                                                                                                                                                                    |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Record integrity              | Writes acknowledge transaction completion; typed string/numeric IDs (including zero), local workout dates, duration zero, nested metadata and historical extension fields survive editing and serialization.                                                                                            |
| PRs and metrics               | Load, volume and estimated-1RM winners retain independent source sets/dates. Completed working sets exclude warm-ups; repeated exercise rows count as one exercise session per workout. Other compatibility semantics are explicit in METRICS_CONTRACT.md.                                              |
| Routine setup                 | Full library preparation before atomic replacement, deterministic starter IDs and ownership-aware fallback/retry prevent partial resets or overwriting user customization.                                                                                                                              |
| Write coordination            | Database and local preference/goal/draft writes share a queue and cooperating-tab Web Lock. Maintenance uses explicit scoped operation tokens.                                                                                                                                                          |
| Crash recovery                | Physical IndexedDB version 3 adds durable undo snapshots for import, clear and schema migration. Recovery precedes subsequent writes; failed recovery retains the journal and blocks mutation.                                                                                                          |
| Conflicts                     | Routine/workout/history saves and deletes compare original snapshots; restored edit drafts retain their baseline. Settings/goals detect changed local values; routine uniqueness/capacity checks share the write transaction.                                                                           |
| Truthful action results       | Commit success is distinguished from later view failure. Committed saves cannot invite duplicate retries. Backup messages distinguish starting a download from actually saving the file.                                                                                                                |
| Database lifecycle and rescue | Blocked opens, upgrades/connection closure and concurrent opens are handled. Separate raw rescue export preserves malformed strings/newer stores without writing or pretending to repair data.                                                                                                          |
| Rendering                     | Startup and mutations refresh the visible destination; navigation reads fresh relevant data. Shared history snapshots avoid repeated hint calculations. Superseded navigation is ignored; loading screens cannot accept edits before their saved baseline is ready.                                     |
| Session boundary              | Pure draft/save serialization preserves metadata. Full replacement of DOM-backed live controls belongs to native state architecture.                                                                                                                                                                    |
| Organization                  | Explicit DOM imports replace ambient helpers; Settings owns its UI with a narrow compatibility facade; CSS ownership/file names and cascade order are documented. Obsolete HTML archive/instructions and proven unused code were removed; source formatting is comprehensive.                           |
| Offline updates               | Content-derived, integrity-verified immutable shell generations keep installed assets together. Waiting updates do not force takeover of an open workout; only tracker-owned caches are cleaned.                                                                                                        |
| Feedback client               | Verification/network/body deadlines, queue-before-send semantics and a separate cooperating-tab outbox lock prevent false delivery acknowledgement. Test transport injection is confined to local origins.                                                                                              |
| Feedback backend              | Owner-fenced expiring claims prevent stale invocations from inserting or releasing successor work. Private per-attempt screenshot keys and ambiguous-commit checks protect referenced images. Durable immutable daily digest batches reuse provider keys; unresolved ambiguity requires reconciliation. |
| Quality and contracts         | Normal checks include lint, formatting, release/shell integrity, catalog, Worker, domain and browser tests. Architecture, schemas, metrics and review dispositions are documented.                                                                                                                      |

The detailed pass-by-pass chronology and intermediate validation remain in [REVIEW_PROGRESS.md](REVIEW_PROGRESS.md). Earlier “remaining” statements are historical, not new tasks. [PRE_ANDROID_CHECKLIST.md](PRE_ANDROID_CHECKLIST.md) is the finite disposition list.

## Validation and publication evidence

- Full source validation at `d5ac9c8`: **235 unit tests, 124 browser tests without retries, 18 Worker tests**, plus lint, formatting, catalog and release/shell checks. [Quality run](https://github.com/Teto200120/workout-tracker/actions/runs/39346091854).
- [Pages run](https://github.com/Teto200120/workout-tracker/actions/runs/39346091714) succeeded; all **82 live shell assets** matched source integrity hashes. Browser coverage used mobile Chromium at 412 × 915, not physical Android.
- Live PWA: <https://teto200120.github.io/workout-tracker/>. UI/package label remains **0.4.0**; shell content hashes identify actual deployed assets. This handoff does not bump the product version.
- Single-file ES-module Worker bundle generated from the signed-off source with esbuild; **all 18 backend tests passed against that bundle** using fake provider transport and SQLite migration fixtures before delivery to the user.
- Worker: <https://workout-tracker-beta-feedback.hector-workout-tracker.workers.dev>. User pasted the bundle in Cloudflare and deployed it; dashboard showed **Active (latest)**. No deployment version ID or server-side source hash was captured, so active status plus functional smoke evidence is the available deployment record.
- Public OPTIONS smoke check returned **204** with the exact allowed PWA origin, methods and headers. No feedback or email was sent by that connectivity check.
- User submitted one no-screenshot test through the PWA and confirmed success. D1 Console query of the newest report showed `received_at = 2026-10-21T02:04:23.651Z` (**2026-10-20 22:04:23 Eastern**), confirming actual report persistence. Feedback message content is intentionally omitted here.

## Manual Cloudflare migration record

The user executed the additive `0005_delivery_ownership.sql` changes in the D1 Console before deploying the Worker:

```sql
ALTER TABLE feedback_report_claims ADD COLUMN owner TEXT;
CREATE TABLE IF NOT EXISTS feedback_digest_batches (
  id TEXT PRIMARY KEY NOT NULL,
  payload_json TEXT NOT NULL,
  report_ids_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  completed_at TEXT
);
```

A `pragma_table_info` check returned **1** matching owner column. A table-existence check returned **4** for reports, claims, deliveries and batches. Existing bindings/secrets were retained; no credentials are recorded in this repository.

These changes were applied manually, not through Wrangler's migration runner. Do not blindly rerun migration 0005: its ALTER is not idempotent. Before future `wrangler d1 migrations apply --remote`, inspect the actual schema and migration ledger and reconcile the already-applied migration using the tooling's supported procedure. Never drop data or repeat ALTER just to satisfy the ledger. See the [backend operating instructions](../backend/cloudflare-worker/README.md).

The existing scheduled digest configuration was not changed or live-tested. Automated digest tests use a fake provider; user explicitly declined further email verification. Provider reconciliation remains an operational procedure if an ambiguous batch occurs, not a newly discovered cleanup task.

## Boundaries carried forward

- Cross-tab guarantees cover cooperating current clients using Web Locks. Older clients that bypass the protocol cannot be coordinated. Destructive cross-storage work fails before mutation without the required lock; ordinary no-lock behavior is scoped to the current tab.
- The single shared web draft slot retains its last-writer policy across independent sessions. Legacy edit drafts without an original baseline cannot overwrite saved History. Intentional bulk import/reset remains a separate replacement/merge operation.
- Application schema **2**, backup envelope **3**, education schema **1** and physical IndexedDB **3** have independent responsibilities. Normal backups exclude drafts, education, recovery journals and feedback outbox. Raw rescue is not a normal import format.
- Catalog data is disposable content, not user history; custom and catalog-selected exercise names remain compatible. No alias merge or silent metric reinterpretation was introduced.
- Further CSS/visual redesign, a persistent derived-view cache and full web session/navigation replacement are deferred or unnecessary for this milestone. Native state and UI replace the relevant web mechanisms.

## Next authorized planning outcome

[ANDROID_TRANSITION.md](ANDROID_TRANSITION.md) contains the executable native handoff: stack and boundaries, import/export contract, session recovery, Material Expressive identity, feedback integration constraint, phased acceptance gates and first implementation instruction. This documentation update does not create an Android project or APK.
