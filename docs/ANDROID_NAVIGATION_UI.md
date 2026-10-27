# Navigation — PWA structure

The primary bar follows the PWA: Stats, Home, Profile. Use a full-width navy surface, outlined icons and compact labels; a coral capsule contains the active icon and label. Preserve user font scaling, minimum 48dp targets, selectable-group semantics and selected-tab state. Limit the row width on wide windows without turning the bar into a floating card.

History opens from Stats and keeps Stats selected. The History back action returns to Stats; tapping a primary tab closes that secondary view. Reopening History restores its search, routine filter and list position. Editors and active workouts retain their existing Back/save/discard flows. Navigation keeps the existing single-top and destination state behavior for primary pages.

Profile's header icon opens the separate Settings page. Backup/data, settings, schedule and support are secondary pages with explicit Back navigation. See [Profile/settings specification](ANDROID_PROFILE_SETTINGS_UI.md).

Emulator checks cover light/dark, 200% text, landscape, selected tabs and stored-data stability. Physical-phone and TalkBack acceptance remain separate.
