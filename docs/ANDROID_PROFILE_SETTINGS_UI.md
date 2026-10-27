# Profile, settings and navigation — PWA hierarchy

Replace the four-destination floating bar with the PWA's Stats / Home / Profile order, outlined icons, compact labels and a full-width navy navigation surface. The selected destination uses a coral capsule around both icon and label. Keep all labels at user font scale, with minimum 48dp touch targets and selected-tab/group semantics. History is a secondary Stats destination with an explicit back action.

Profile owns identity, goal/session summaries and weekly progress. A small Settings icon in its header opens a separate Settings page. Routines and backup/data remain profile shortcuts; no preference editor, reset controls or long backup instructions appear inline on Profile.

Settings is a normal scrolling form: name, training goal/RPE, weekly schedule, app feel and data/support links. Short row descriptions replace explanatory paragraphs; progression defaults stay collapsed until requested. Show Save changes when there are edits. Keep explicit saves, validation, dirty-back confirmation, restoration and revision/epoch conflict checks. Refresh unchanged preferences when returning from a secondary page; never reload over edited values.

Backup/data has its own page, retaining existing document pickers, import review, active-draft guards and reviewed reset/clear operations. No workout, routine, date, backup, metrics or preference storage formats change.

The v25 profile polish introduces an asymmetric monogram header, a distinct round Settings button, tonal contours and a native goal ring with readable counts. Logged sessions is compact and profile shortcuts use the existing native action rows. Settings and data-page workflows remain as established here. See [primary-screen polish](ANDROID_PRIMARY_LOOK_UI.md).

The v26 expressive treatment follows the shared navy/coral shapes, tonal depth and compact/summary heading hierarchy. Existing flows and data contracts remain intact. See [remaining-screen polish](ANDROID_REMAINING_LOOK_UI.md).
