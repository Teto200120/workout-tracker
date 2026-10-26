# Android APK downloads

Latest: [WorkoutTracker 0.13.0-preview APK](WorkoutTracker-0.13.0-preview.apk?raw=true).

This is the signed, non-debuggable native UI preview (version code 13). It uses the permanent signing identity and can update the permanently signed v12 installation. Export a backup before updating. Older temporary-key previews require a new installation after exporting their data.

The matching source is included in the commit that adds this APK. This APK was built locally with the pinned toolchain, not downloaded from CI. SHA-256 is recorded in [SHA256SUMS](SHA256SUMS).

Certificate SHA-256: `e469e4014a3adce256a292033433291b891f668aa9f243073790ba6b9db52a88`.

Validation: 103 JVM tests pass; Android lint has zero errors and 10 warnings; the maintained end-to-end workout flow passes. The delivered APK passed a v12-to-v13 upgrade retaining profile and active draft, cold restart, keyboard, light/dark rendering and landscape checks on the owned emulator. Physical-phone and TalkBack acceptance remain outstanding.

## Future Android deliveries

At the user's request, every future delivered Android preview must include its signed APK in this repository so it is remotely downloadable. Add the versioned APK here, update this latest-download link and SHA256SUMS, and commit/push it with its matching source. Verify its version, non-debuggable flag, permanent signing certificate and checksum before publishing. Include the remote APK download URL in the handoff. Do not rely solely on local files or expiring CI artifacts. Never commit signing keys or credentials.
