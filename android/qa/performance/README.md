# Native release UI timing

Use a disposable emulator containing synthetic workout history, a completed profile, at least one saved routine, and no active workout. Pass the seeded history count with `--workouts` (default 1,003); Stats readiness expects that exact count. Do not use personal app data.

The helper runs in its own process and measures the real installed release APK. It does not load production classes, need a debuggable target, or change the target APK. It force-stops the app before each launch, waits for Home's weekly count and enabled Start button, taps Start, and waits for the first enabled set-completion control. Each newly started workout is discarded outside the timed interval. Stats mode measures the first visit after a cold launch. Readiness includes a 32 ms frame allowance; these are interaction timings, not exact frame presentation timestamps.

Install the release APK and prepare the same database for both versions before running. Use identical emulator graphics, resolution, font size and animation settings. Avoid builds or other heavy work during measurements. Record the emulator renderer, API level, APK hashes, database counts and compile mode. The helper uses Android's `speed` compile mode, two excluded warmups and nine measured trials. Retain all samples and compare medians, including both full launch-to-workout and Start-to-workout results. Repeat the baseline after the candidate to detect host slowdown. Emulator results do not establish physical-phone speed.

Set `ANDROID_HOME` and `JAVA_HOME` to the installed Android SDK and JDK. Requirements are Python's standard library, platform 36, build tools 36.0.0, and the standard local Android debug keystore. No downloaded packages are needed. From `android/`:

```sh
python qa/performance/run.py --serial emulator-5554 --output /absolute/path/to/results --mode workout
python qa/performance/run.py --serial emulator-5554 --output /absolute/path/to/stats-results --mode stats --workouts 1003
```

Each output directory contains the helper build, instrumentation log and all timing samples. Remove the helper afterward with `adb -s emulator-5554 uninstall com.hector.workoutbenchmark`.

`PerformanceProbeTest` separately times repository reads and catalog parsing on the debug variant, compares repeated outputs and writes fingerprints to the target app's internal `files/qa-performance/probe.json`. It is skipped by default. After preparing a disposable debug installation with matching test APK and synthetic history, run:

```sh
adb -s emulator-5554 shell am instrument -w -e performance true -e class com.hector.workouttracker.PerformanceProbeTest com.hector.workouttracker.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as com.hector.workouttracker cat files/qa-performance/probe.json
```

Method timings from the debug probe explain code changes; use the independent release UI results for user-visible speed claims. The probe briefly creates and discards drafts, and never saves a workout. History, statistics, catalog and copied-set fingerprints must match the baseline.
