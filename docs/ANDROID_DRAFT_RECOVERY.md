# Android draft recovery — v32

Opening a legacy workout with several expanded cards previously saved a presentation adjustment immediately. That changed the draft's payload and revision during an otherwise read-only upgrade or restart. The version-2 upgrade check caught this by comparing the complete saved database checkpoint.

The ViewModel now keeps the exact recovered draft. The workout screen derives a single current card for display, and an explicit edit uses that same presentation before applying and saving the user's change. Opening the app performs no presentation write. Existing current-card selection and intentional all-collapsed drafts are preserved. Entered values, notes, stable row keys, routine origins, revision checks and the backup format keep their existing behavior.

The production fix is in [TrackerViewModel](../android/app/src/main/java/com/hector/workouttracker/feature/session/TrackerViewModel.kt) and [SessionScreen](../android/app/src/main/java/com/hector/workouttracker/feature/session/SessionScreen.kt). The existing lifecycle assertions remain unchanged. Its host runner now selects the correct SDK executable names on Windows, so the same check runs locally and on Linux CI.

Validation includes all 114 JVM tests and Android lint with zero errors and the same 10 warnings. The installed version-2-to-v32 test compares every saved row, payload and revision after upgrade and cold restart. It also verifies a current draft's numeric-zero routine origin, selected card and exactly-once finish after another cold restart. Its exported result passes the PWA importer. The full workout workflow passes with airplane mode on and Wi-Fi/mobile data disabled.

v32 carries forward the smaller APK, history/statistics caches and timer isolation introduced in v31. Those performance measurements are recorded separately in the [v31 report](ANDROID_NATIVE_PERFORMANCE.md); this correction has its own upgrade and recovery checks. Physical-phone and TalkBack acceptance remain unverified.
