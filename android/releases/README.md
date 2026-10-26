# Android APK downloads

Latest: [v15 GitHub Release](https://github.com/Teto200120/workout-tracker/releases/tag/android-v0.15.0-preview) · [Download APK](https://github.com/Teto200120/workout-tracker/releases/download/android-v0.15.0-preview/WorkoutTracker-0.15.0-preview.apk).

This is the signed, non-debuggable active-workout UI preview (version code 15). It uses the permanent signing identity and can update the permanently signed v14 installation. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

The matching source is included in the commit that adds this APK. This APK was built locally with the pinned toolchain, not downloaded from CI. SHA-256 is recorded in [SHA256SUMS](SHA256SUMS).

Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Validation: 103 JVM tests pass; Android lint has zero errors and 10 warnings; the maintained end-to-end workout flow passes. The delivered APK passed a v14-to-v15 upgrade retaining profile and active draft, cold restart, keyboard, light/dark rendering and landscape checks on the owned emulator. Physical-phone and TalkBack acceptance remain outstanding.

Home follows the PWA greeting/week/workout/primary-action hierarchy. Routine selection previews first and starts only through the bottom action. See [Home UI specification](../../docs/ANDROID_HOME_UI.md).

Active logging now focuses on one set with vertical numeric controls, a set selector/All sets review, elapsed time and a context-aware completion action. See [active workout specification](../../docs/ANDROID_SESSION_UI.md).

## Future Android deliveries

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Also publish a GitHub Release (prerelease for previews) targeting the matching source commit, with the signed APK and SHA256SUMS attached as release assets. Include both the GitHub Release page and direct APK asset download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.
