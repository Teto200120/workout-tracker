# Android APK downloads

Latest: [v35 GitHub Release](https://github.com/Teto200120/workout-tracker/releases/tag/android-v0.35.0-preview) · [Download APK](https://github.com/Teto200120/workout-tracker/releases/download/android-v0.35.0-preview/WorkoutTracker-0.35.0-preview.apk).

This signed, non-debuggable active-workout PWA alignment preview uses version code 35 and the permanent signing identity. It updates permanently signed v34. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

The active workout returns to the PWA's icon shapes, rotating chevrons, information action, round steppers, status badges and motion. The toolbar remains visible as cards move, numeric values have their own input targets, and details fade without horizontal travel. Existing data, set entry, drag continuity and cancellation remain intact.

Matching source is in the commit that adds this APK. Built locally with the pinned toolchain. SHA-256 is recorded in [SHA256SUMS](SHA256SUMS); GitHub release assets include source, signing and verification details.

Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Validation: 114 unit tests, eight device tests, formatting and debug/release builds pass. Lint reports zero errors and 17 warnings. The signed upgrade and final APK replacement preserve the original v34 synthetic database, profile and unfinished workout through a cold restart. Android 15 keyboard checks pass. Physical-phone and TalkBack acceptance remain outstanding.

Details: [PWA alignment](../../docs/ANDROID_WORKOUT_PWA_ALIGNMENT.md) · [v35 verification](../../docs/verification/android-v35-pwa-alignment.json) · [retained drag continuity](../../docs/ANDROID_WORKOUT_MOTION.md) · [Android 15 keyboard recovery](../../docs/ANDROID_KEYBOARD_RECOVERY.md).

## Future Android deliveries

Advance `versionCode` and `versionName` for every delivered update and create a new GitHub Release. Keep the published APK for each version unchanged.

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Also publish a GitHub Release (prerelease for previews) targeting the matching source commit, with the signed APK and SHA256SUMS attached as release assets. Include both the GitHub Release page and direct APK asset download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.

History now follows the PWA filter/card hierarchy with a native date picker and styled full-workout editor. See [History specification](../../docs/ANDROID_HISTORY_UI.md).

Stats now places weekly activity after the totals and includes exercise progress charts with detailed source rows. See [Stats specification](../../docs/ANDROID_STATS_UI.md).
