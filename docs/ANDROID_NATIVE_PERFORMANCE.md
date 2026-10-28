# Android native performance — v31

The APK is 78% smaller. Median workout entry takes 51.1% less time with 2,003 saved workouts and 38.1% less time with 1,003. Performance depends on the workload; cold launch and navigation have separate results below. Physical-phone measurements are still needed.

## Release interaction measurements

| Saved workouts | Operation | v30 baseline | v31 | Less time |
| --- | --- | --- | --- | --- |
| 1,003 | Cold launch to usable Home | 1,104 ms | 929 ms | 15.9% |
| 1,003 | Tap Start to usable workout | 519 ms | 322 ms | 38.1% |
| 1,003 | Cold launch through workout entry | 1,656 ms | 1,246 ms | 24.8% |
| 1,003 | First Stats visit after Home | 181 ms | 165 ms | 9.1% |
| 2,003 | Cold launch to usable Home | 1,352 ms | 1,074 ms | 20.6% |
| 2,003 | Tap Start to usable workout | 770 ms | 376 ms | 51.1% |
| 2,003 | Cold launch through workout entry | 2,134 ms | 1,453 ms | 31.9% |
| 2,003 | First Stats visit after Home | 195 ms | 162 ms | 16.6% |

Each comparison uses nine measured process-cold launches after two excluded warmups. Both versions use the same synthetic database, default routine selection, helper code, screen settings and `speed` compile mode. The v30 baseline includes Home readiness reporting for startup measurement; its UI and data behavior follow commit `d13ea94`. The fixtures contain three base workouts plus 1,000 or 2,000 synthetic rows. A fresh MigrationQA emulator boot precedes each version/history-size group. The Android 16 emulator uses a 1080×2400 display at 420 dpi, normal text size and host NVIDIA RTX 5070 graphics. No build runs during an accepted timing group.

An independent Android test app launches the installed, non-debuggable release, waits for Home's weekly progress and enabled Start action, taps Start, then waits for the first enabled set-completion action. Each newly created draft is discarded outside the timed interval. Stats timing waits for the exact saved-workout count. A 32 ms frame allowance follows readiness checks. These are accessibility interaction timings, rather than exact display presentation timestamps. The helper uses installed SDK tools and Python's standard library; see [reproduction instructions](../android/qa/performance/README.md).

The measured APK uses a local QA certificate to preserve synthetic databases between debug seeding and release installation. Every file from the unsigned release matches both the measured APK and the permanent-key APK delivered to users. Application code, resources and assets match byte for byte. [All samples, hashes and validation details](verification/android-v31-performance.json) are committed with the APK.

Earlier measurements used software graphics or a long-running emulator. Graphics-service CPU and original-app launch times rose during the latter session; a fresh boot restored first-frame times from roughly 1.4 seconds to 0.55 seconds. Those runs remain in local verification artifacts, outside the accepted comparison. A diagnostic startup/workout Perfetto trace parsed with zero error/data-loss counters and showed repeated composition, drawing and background history work. Profiler timings are separate from the release comparison.

## Build and runtime changes

[Release settings](../android/app/build.gradle.kts) enable Android's standard code and resource shrinking with the optimized default rules. The unused Compose preview dependency is removed. The permanent signing identity, minimum Android version and database schema stay the same. CI keeps the matching R8 mapping needed to read obfuscated crash reports; the release also includes that mapping as an asset.

The generated native catalog contains the eight fields read by Android, along with source metadata. It is 798,464 uncompressed bytes instead of 1,496,074. Every catalog exercise, instruction, filter value and source revision matches the canonical PWA data in a packaging test. The shared PWA catalog stays intact. Guide normalization reuses compiled regular expressions.

[Workout history caching](../android/app/src/main/java/com/hector/workouttracker/data/WorkoutHistoryCache.kt) shares one JSON parse between Stats and workout guidance. It retains at most one snapshot containing 4,194,304 source characters; larger histories remain readable without retaining the parse. Full record contents and revisions are compared, including delete/reinsert cases with reused IDs. No derived values are saved into backups.

[Routine loading](../android/app/src/main/java/com/hector/workouttracker/data/TrackerRepository.kt) shares a lightweight date/name index with library loading. Starting a routine rechecks the current database snapshot and decodes sets only from matching previous workouts. Duplicate names, blank latest rows, typed-ID tie ordering, unknown saved fields and fresh working-set keys keep their existing behavior. Guidance calculates all selected exercises in one history pass.

[Stats](../android/app/src/main/java/com/hector/workouttracker/data/StatsRepository.kt) reuses an unchanged coherent result. Calendar, goal and preference changes reuse lifetime calculations where possible; actual history changes rebuild them. Duplicate database notifications are ignored. Full statistics remain available from Home, and the first Stats visit is measured above.

[The active workout](../android/app/src/main/java/com/hector/workouttracker/feature/session/SessionScreen.kt) starts its list at the current card before centering it. Timer updates belong to the clock component, so ticking does not recompose entry fields or the focused exercise view. Elapsed time still uses the existing monotonic clock and reboot handling. Dragging, touch-to-expand, focused Log/Guide, rest timing and keyboard behavior keep their existing design.

Supporting debug probes on 1,003 workouts show repeated statistics reads dropping from roughly 412 to 55 ms and routine preparation from 354 to 65 ms. History, statistics, catalog and copied-workout fingerprints match exactly for both 3 and 1,003 workouts. These probes precede only the final initial-scroll-position adjustment and are recorded separately from release interaction timings.

## Verification and delivery

All 114 JVM tests pass. Android lint reports zero errors and the same 10 warnings. The complete maintained workout workflow passes in airplane mode with Wi-Fi and mobile data disabled. Eleven JVM exports and eight emulator exports pass the PWA importer checks.

The permanent-key v30-to-v31 update retains the profile and active exercises, including the selected current card. Installed APK bytes match the delivered file. Real release checks cover focused Log/Guide, keyboard input, immediate inline dragging, notes, light/dark rendering and a cold restart after reordering.

The visible workout clock advances correctly. At 360dp phone width and 200% text, workout actions remain reachable, the focused view retains its notes, and Home/Stats retain navigation and values. Screenshots are included with the release.

The native application has no internet permission. Logging, local history, text guides, routines, schedule and backups work without a connection. Hosted browser feedback uses the existing online service. Physical-phone speed and TalkBack use remain unverified.

APK SHA-256: `f2d853d691a688c85bb929f7e3f0f423199b9cc58b4544412eb046c6ccb03fd8`.

Matching source is the commit targeted by the v31 GitHub prerelease. This APK is built locally with the pinned toolchain; it is not a downloaded CI artifact.

The build approach follows [Android's R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) and the standard release approach in [Now in Android](https://github.com/android/nowinandroid), an Apache-2.0 reference application. No external application code or new dependency is included.
