# Pre-Android completion checklist

Fixed scope: the original F01–F19 review. Additional optional web redesigns are not part of this milestone. Historical passes in REVIEW_PROGRESS.md are an audit trail, not new open tasks.

| Finding | Disposition before Android                                                                                                                                                                                                      |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| F01–F05 | Delivered: transaction acknowledgement, typed IDs, metadata/drafts, local dates and independent personal records.                                                                                                               |
| F06     | Metrics contract documented; working-set completion population and exercise session counting corrected. Remaining historical grouping differences are explicit compatibility rules.                                             |
| F07–F10 | Delivered within documented cooperating-client/Web Locks boundaries: atomic seeds, truthful action messages, coordinated writes, crash recovery, stale-editor protection and raw rescue.                                        |
| F11     | Destination-only startup/post-write/navigation rendering and shared history snapshots for hints delivered. A persistent derived-view cache is unnecessary for this baseline.                                                    |
| F12     | Pure metadata-preserving draft/save serialization boundary delivered. Full replacement of DOM-backed live controls is assigned to native state architecture.                                                                    |
| F13     | Stylesheet ownership and cascade order made explicit; source formatted. Further CSS consolidation and visual redesign assigned to native UI work.                                                                               |
| F14     | Integrity-verified immutable shell generations, waiting updates and tracker-only cache cleanup delivered.                                                                                                                       |
| F15     | Full source formatting, shell/release checks, catalog, unit, backend and browser gates included in normal checks.                                                                                                               |
| F16–F17 | Backend fixes implemented and tested: immutable digest batches, provider keys, owner-fenced leases; client deadlines and coordinated outbox delivered. Separate Cloudflare deployment remains a release gate.                   |
| F18     | Current architecture/data docs corrected. Package/UI labels agree, content hashes identify deployments. Next semantic version waits for compatible receiver deployment.                                                         |
| F19     | Obsolete HTML archive removed; DOM helper globals replaced by imports; Settings screen owns its UI; test transport is limited to local origins. Six narrow web navigation commands remain as documented compatibility adapters. |

## Release gates

- [ ] Full repository checks and browser regressions pass for the published commit.
- [ ] GitHub Pages serves the expected shell and runtime files.
- [ ] Apply Cloudflare migration 0005 and deploy the updated Worker; do not send real digest test emails.

The native Android implementation has not begun. Android foundation can start once these release gates are recorded, without another general PWA cleanup pass. The first Android task should define native storage/import, session state, navigation and the Material Expressive theme around the existing navy/coral identity. It must preserve the versioned data contracts and metrics contract above.
