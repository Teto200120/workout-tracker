# Android APK downloads

Latest: [v32 GitHub Release](https://github.com/Teto200120/workout-tracker/releases/tag/android-v0.32.0-preview) · [Download APK](https://github.com/Teto200120/workout-tracker/releases/download/android-v0.32.0-preview/WorkoutTracker-0.32.0-preview.apk).

This is the signed, non-debuggable read-only workout recovery preview (version code 32). It uses the permanent signing identity and can update permanently signed v31. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

Matching source is in the commit that adds this APK. Built locally with the pinned toolchain; this is not a downloaded CI artifact. SHA-256 is recorded in [SHA256SUMS](SHA256SUMS).

Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Validation: 114 JVM tests pass; Android lint has zero errors and 10 warnings; the maintained end-to-end workflow passes. Emulator checks cover unchanged draft recovery, inline dragging, focused Log/Guide, real keyboard input, notes and light/dark rendering. The delivered APK passes a v31-to-v32 upgrade retaining profile and active draft, installed-byte verification, cold restart and keyboard checks. Physical-phone and TalkBack acceptance remain outstanding.

Recovery details: [read-only draft recovery](../../docs/ANDROID_DRAFT_RECOVERY.md). The shrinking, caches and timer improvements from v31 remain included; [earlier performance measurements](../../docs/ANDROID_NATIVE_PERFORMANCE.md) are recorded separately.

UI specifications: [Home](../../docs/ANDROID_HOME_UI.md) · [Active workout](../../docs/ANDROID_SESSION_UI.md) · [History](../../docs/ANDROID_HISTORY_UI.md) · [Stats](../../docs/ANDROID_STATS_UI.md) · [Profile and settings](../../docs/ANDROID_PROFILE_SETTINGS_UI.md) · [Routines](../../docs/ANDROID_ROUTINES_UI.md) · [Weekly schedule](../../docs/ANDROID_SCHEDULE_UI.md) · [Exercise picker and guide](../../docs/ANDROID_CATALOG_UI.md) · [Setup and support](../../docs/ANDROID_SUPPORT_UI.md) · [Navigation and accessibility](../../docs/ANDROID_NAVIGATION_UI.md).

## Future Android deliveries

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Also publish a GitHub Release (prerelease for previews) targeting the matching source commit, with the signed APK and SHA256SUMS attached as release assets. Include both the GitHub Release page and direct APK asset download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.

History now follows the PWA filter/card hierarchy with a native date picker and styled full-workout editor. See [History specification](../../docs/ANDROID_HISTORY_UI.md).

Stats now places weekly activity after the totals and includes exercise progress charts with detailed source rows. See [Stats specification](../../docs/ANDROID_STATS_UI.md).
