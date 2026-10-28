# Android APK downloads

Latest: [v34 GitHub Release](https://github.com/Teto200120/workout-tracker/releases/tag/android-v0.34.0-preview) · [Download APK](https://github.com/Teto200120/workout-tracker/releases/download/android-v0.34.0-preview/WorkoutTracker-0.34.0-preview.apk).

This signed, non-debuggable workout motion and card clarity preview uses version code 34 and the permanent signing identity. It updates permanently signed v33. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

Dragging retains the lifted card through release, preserves the list position and settles cards into place. Card disclosure, details, Log/Guide and screen changes use consistent short transitions. Clearer card headers and grouped set values keep logging ahead of supporting information. Narrow layouts and large text retain stacked controls.

Matching source is in the commit that adds this APK. Built locally with the pinned toolchain. SHA-256 is recorded in [SHA256SUMS](SHA256SUMS); release assets include signing and source details.

Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Validation: 114 unit tests, six device tests, formatting and debug/release builds pass. Android lint reports zero errors and 16 warnings in unchanged code or dependency declarations. The exact signed v33-to-v34 upgrade preserves the complete synthetic database and active draft through a cold restart, and the real keyboard works on Android 15.

Verification and limits: [workout motion](../../docs/ANDROID_WORKOUT_MOTION.md) and [v34 checks](../../docs/verification/android-v34-workout-motion.json). Physical-phone and TalkBack acceptance remain outstanding. Earlier [Android 15 keyboard recovery](../../docs/ANDROID_KEYBOARD_RECOVERY.md), [read-only draft recovery](../../docs/ANDROID_DRAFT_RECOVERY.md), and [native performance improvements](../../docs/ANDROID_NATIVE_PERFORMANCE.md) remain included.

## Future Android deliveries

Advance `versionCode` and `versionName` for every delivered update and create a new GitHub Release. Keep the published APK for each version unchanged.

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Also publish a GitHub Release (prerelease for previews) targeting the matching source commit, with the signed APK and SHA256SUMS attached as release assets. Include both the GitHub Release page and direct APK asset download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.

History now follows the PWA filter/card hierarchy with a native date picker and styled full-workout editor. See [History specification](../../docs/ANDROID_HISTORY_UI.md).

Stats now places weekly activity after the totals and includes exercise progress charts with detailed source rows. See [Stats specification](../../docs/ANDROID_STATS_UI.md).
