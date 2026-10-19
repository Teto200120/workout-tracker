# PWA metrics contract and Android handoff

This contract defines current display calculations without migrating stored workouts.

| Metric                                            | Definition                                                                                                                                                            |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Workout count                                     | Saved workout records, including compatibility numeric IDs.                                                                                                           |
| Working sets                                      | All saved non-warm-up set rows.                                                                                                                                       |
| Completed working sets                            | Non-warm-up rows with `done: true`; the numerator and working-set denominator cover the same population.                                                              |
| Working volume                                    | Sum of saved weight × repetitions for non-warm-up rows, including unchecked rows. The checkbox measures completion separately.                                        |
| Load, set-volume and estimated-1RM records        | Independent winning positive loaded working sets; unchecked saved sets are eligible. Epley estimate is weight × (1 + reps / 30). First encountered source wins a tie. |
| Exercise identity in Stats                        | Trimmed exact-case saved name. Catalog aliases enrich guides only; they never merge history.                                                                          |
| Last-set suggestions and completion PR comparison | Existing case-insensitive trimmed-name matching, retained for compatibility. This is intentionally distinct from Stats grouping.                                      |
| Exercise sessions                                 | Number of saved workouts containing that exact trimmed name; repeated rows within one workout count once.                                                             |
| Exercise history points                           | Each saved exercise occurrence with an eligible best set; multiple occurrences may share one date.                                                                    |
| Duration                                          | Explicit saved duration wins, including zero; otherwise clock difference with midnight rollover.                                                                      |
| Calendar buckets                                  | Local `YYYY-MM-DD` workout dates; no implicit UTC date conversion.                                                                                                    |

The completed-working-set numerator and repeated-exercise session count were corrected in the baseline cleanup. These affect derived displays only. Warm-ups remain in saved history. Numeric IDs, unknown extension fields and historical metadata remain intact.

Android must preserve these semantics at import. Any later move to completed-only volume/PRs, canonical exercise IDs, case-folded grouping or per-day chart aggregation needs an explicit product/data decision and its own compatibility tests. Do not silently reinterpret imported history.
