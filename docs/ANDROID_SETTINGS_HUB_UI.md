# Settings - grouped hub and focused editors

The user's reference is a settings list with rounded navy cards, coral uppercase section labels, subtle dividers, tinted circular outline icons, muted saved values and chevrons. The first attached PNG was truncated; later supplied JPEGs clarify the app preferences, support and danger groups. Borrow the hierarchy and visual treatment while presenting settings the tracker actually supports. Keep existing native Material controls and the app's light and dark palettes.

## Structure

- Profile: Edit profile, with the saved display name on the right.
- Workout preferences: weekly workout goal, RPE tracking, weekly schedule and progression defaults. Blue icon accents distinguish this group.
- App preferences: haptics and animations open the app behavior editor; version is a read-only row without a chevron. Violet icon accents.
- Help and support: existing backup/data, offline guidance and beta feedback destinations, using teal icon accents.
- Danger zone: a red Manage local data row opens the existing backup/data review page. It does not execute a reset or clear operation. Existing review dialogs, active-draft guards and import protections still apply.

Do not add placeholder account/logout, body metrics, language, notification or unit controls from the mockup. These require separate functional work. Settings remains a secondary destination from Profile with its own pinned round Back action and title; the primary bottom bar does not appear on it.

## Editors and state

Profile edits the name; workout preferences edit the rolling seven-day goal and RPE tracking; app behavior edits haptics and animations; progression edits the existing load jump and rep ranges. Each page keeps a separate saved list position. A clean Back returns to the hub; a second Back returns to Profile. Returning from support or schedule restores the hub position and refreshes unchanged stored preferences.

A changed editor shows an explicit Save changes button. Saving stays on the editor and confirms Settings saved. Dirty Back offers Keep editing or Discard edits; discarding reloads saved values and returns to the hub. Preserve validation, unusually-high-value review, saved draft/dialog/page restoration, concurrent-write rejection and reviewed reload. No auto-save is introduced. Repository, schema, IDs, exports, history, routines and active workout formats remain unchanged.

## Responsive and accessibility behavior

Rows are at least 76dp high; the pinned Back target is 48dp. Every navigation row is a native button with label/value semantics; decorative icons and chevrons are excluded from announcement. Category labels expose heading semantics. Version has no click action. At large text sizes or below 360dp of available row width, saved values stack below labels; long names truncate in the hub and remain fully editable on the profile page. Editors retain normal scrolling. Keep text at the user's font scale in narrow and landscape layouts.

## Verification

Adapt the maintained end-to-end navigation to enter the relevant editor and save each group explicitly. Retain its assertions for invalid numbers, saved values, live profile/RPE/interactions updates, unsaved edits and warning dialogs across recreation, concurrent conflict/reload, opaque backup data, workout history and active draft preservation.

Focused emulator checks cover hub and editor routes, read-only browsing with exact database row comparisons, light/dark render, 200% text, landscape, compact width and guarded data links. Run the pinned offline build, unit tests, formatting and lint. Verify the permanently signed v27-to-v28 replacement retains profile and an active draft, installed APK bytes, non-debuggable version and real keyboard/cold launch behavior. Physical-device and TalkBack acceptance remain separate.
