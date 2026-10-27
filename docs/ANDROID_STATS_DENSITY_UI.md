# Stats — overview first, details on demand

Replace the long combined feed with a compact overview and clear local detail destinations. Retain the established navy/coral palette, contour surface, native controls and bottom navigation. The default overview should expose the weekly goal, calendar-week progress, lifetime totals, strength preview, exercise-records entry and latest session without scrolling on the reference phone at its default font size.

## Information hierarchy

- **Overview:** rolling seven-day workout count/goal; seven compact calendar markers and the actual planned gym-day count; lifetime workouts, working sets, volume, streak and average duration; the first source-backed strength insight; a direct exercise-records row; the latest saved session linking to History.
- **Weekly details:** a native scrollable dialog with the full Monday–Sunday dates, statuses and routine titles. Keep this calendar scope distinct from the rolling seven-day goal.
- **Training insights:** all existing insights and the three recent saved sessions, with their typed identities, dates, exercise counts and duration. Keep a direct History action.
- **Exercise records:** search is at the top of a dedicated list reached in one tap. Compact rows retain exact exercise identity and the existing estimated-1RM ordering. Preserve the search and list position when returning from progress.
- **Exercise progress:** metric selection first, followed by that metric's independent record winner and source weight/reps/date/RPE together with its zero-baseline chart. The same last seven entries, source order, ties and straight segments remain. Entry-by-entry text expands on request; no values are discarded or merged.
- **About the numbers:** average-duration rules, qualifying loaded sets, warm-up exclusion, Epley estimate and exact-spelling/repeated-entry behavior stay available in a native dialog.

Give overview, exercise list, insights and progress independent saved scroll states. Toolbar/system Back returns to the preceding local page. Closing a record dismisses its keyboard; returning restores the query and overview position. History stays reachable from the header on every Stats page. Preserve loading, empty, retry, deletion and live-refresh states.

Do not shrink text or touch targets to force large-font layouts into one screen. At 200% text and in landscape, allow normal scrolling and wrap content while retaining access to every destination and dialog. No repository, database, backup, metric formula or data-format changes belong here.

## Verification

The v26 reference fixture required four middle-third upward scroll gestures to reach exercise search. Verify the same fixture reaches search through one tap and zero scrolls, and that overview information and the selected record/chart are visible before scrolling at default text size. Check all three source winners, repeated-date entry details, dialogs, keyboard dismissal, independent Back restoration, large text/landscape, empty history and a larger exercise list. Stored rows must remain exact during browsing.

Adapt the maintained workflow's navigation while keeping its typed-ID, record, live-update, restoration and draft assertions. Add visibility assertions for the compact overview and chart. Run JVM tests/lint/full workflow and a permanent-key v26-to-v27 signed upgrade before delivering the APK. Physical-device/TalkBack acceptance remains separate.
