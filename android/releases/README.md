# Android APK downloads

Latest: [v36 GitHub Release](https://github.com/Teto200120/workout-tracker/releases/tag/android-v0.36.0-preview) | [Download APK](https://github.com/Teto200120/workout-tracker/releases/download/android-v0.36.0-preview/WorkoutTracker-0.36.0-preview.apk).

Version code 36, signed with the permanent identity. Updates permanently signed v35 and keeps existing workouts. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

Workout cards expand from the top, their spacing closes continuously, and focus scrolling follows the changing layout. Card-center drag thresholds prevent early swaps and small finger reversals from making the order flicker. Drop settling starts at the visible position and shares a 220ms curve with neighboring cards. The v35 PWA icons and controls remain.

Validation: 114 unit tests, 12 device tests, formatting, debug/release builds and lint (0 errors, 17 warnings). The signed upgrade and final APK replacement preserve the original v35 synthetic database and unfinished workout through a cold restart. Real keyboard checks pass. Physical-phone animation feel and TalkBack remain unverified.

Source matches the commit adding this APK. Built locally with the pinned toolchain. [SHA256SUMS](SHA256SUMS) records the APK checksum; GitHub assets include signing, source and verification details. Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Details: [motion tuning](../../docs/ANDROID_WORKOUT_MOTION_TUNING.md) | [v36 verification](../../docs/verification/android-v36-motion-tuning.json) | [PWA alignment](../../docs/ANDROID_WORKOUT_PWA_ALIGNMENT.md).

## Future Android deliveries

Advance `versionCode` and `versionName` for every delivered update and create a new GitHub Release. Keep the published APK for each version unchanged.

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Also publish a GitHub Release (prerelease for previews) targeting the matching source commit, with the signed APK and SHA256SUMS attached as release assets. Include both the GitHub Release page and direct APK asset download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.

History now follows the PWA filter/card hierarchy with a native date picker and styled full-workout editor. See [History specification](../../docs/ANDROID_HISTORY_UI.md).

Stats now places weekly activity after the totals and includes exercise progress charts with detailed source rows. See [Stats specification](../../docs/ANDROID_STATS_UI.md).
