# Android APK downloads

Latest: [v21 GitHub Release](https://github.com/Teto200120/workout-tracker/releases/tag/android-v0.21.0-preview) · [Download APK](https://github.com/Teto200120/workout-tracker/releases/download/android-v0.21.0-preview/WorkoutTracker-0.21.0-preview.apk).

This is the signed, non-debuggable Exercise picker and guide UI preview (version code 21). It uses the permanent signing identity and can update permanently signed v20. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

Matching source is in the commit that adds this APK. Built locally with the pinned toolchain; this is not a downloaded CI artifact. SHA-256 is recorded in [SHA256SUMS](SHA256SUMS).

Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Validation: 103 JVM tests pass; Android lint has zero errors and 10 warnings; the maintained end-to-end workflow passes. Focused emulator checks cover this screen's interactions, light/dark rendering, large text and landscape. The delivered APK passes a v20-to-v21 upgrade retaining profile and active draft, installed-byte verification, cold restart and keyboard checks. Physical-phone and TalkBack acceptance remain outstanding.

UI specifications: [Home](../../docs/ANDROID_HOME_UI.md) · [Active workout](../../docs/ANDROID_SESSION_UI.md) · [History](../../docs/ANDROID_HISTORY_UI.md) · [Stats](../../docs/ANDROID_STATS_UI.md) · [You and preferences](../../docs/ANDROID_YOU_UI.md) · [Routines](../../docs/ANDROID_ROUTINES_UI.md) · [Weekly schedule](../../docs/ANDROID_SCHEDULE_UI.md) · [Exercise picker and guide](../../docs/ANDROID_CATALOG_UI.md).

## Future Android deliveries

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Also publish a GitHub Release (prerelease for previews) targeting the matching source commit, with the signed APK and SHA256SUMS attached as release assets. Include both the GitHub Release page and direct APK asset download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.

History now follows the PWA filter/card hierarchy with a native date picker and styled full-workout editor. See [History specification](../../docs/ANDROID_HISTORY_UI.md).

Stats now places weekly activity after the totals and includes exercise progress charts with detailed source rows. See [Stats specification](../../docs/ANDROID_STATS_UI.md).
