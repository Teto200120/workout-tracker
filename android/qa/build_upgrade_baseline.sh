#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
baseline_ref=$(cat qa/upgrade-baseline.txt)
baseline_dir="${RUNNER_TEMP:?CI temporary directory is required}/workout-upgrade-baseline"
# Keep the historical app source intact; only the synthetic test driver/assets are added.
mkdir "$baseline_dir"
git fetch --no-tags --depth=1 origin "$baseline_ref"
git archive "$baseline_ref" | tar -x -C "$baseline_dir"
cp app/src/androidTest/java/com/hector/workouttracker/LifecycleAcceptanceTest.kt "$baseline_dir/android/app/src/androidTest/java/com/hector/workouttracker/"
cp ../tests/fixtures/android/training-backup.json "$baseline_dir/tests/fixtures/android/"
cd "$baseline_dir/android"
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
