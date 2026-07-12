import assert from "node:assert/strict";
import test from "node:test";
import { DEFAULT_APP_SETTINGS } from "../../src/js/core/constants.js";
import {
  buildTargetFromLastSets,
  estimateWorkoutDuration,
  getExerciseProfile,
  getProgressionRecommendation,
  getWorkoutTags,
  inferMuscleTag,
  workSetsOnly,
} from "../../src/js/domain/training-rules.js";

test("exercise classification and routine tags preserve current matching order", () => {
  assert.equal(inferMuscleTag("Incline Bench Press"), "Chest");
  assert.equal(inferMuscleTag("Rear Delt Fly"), "Rear Delts");
  assert.equal(inferMuscleTag("Sprint Work"), "Conditioning");
  assert.equal(inferMuscleTag("Unknown Movement"), "");
  assert.deepEqual(
    getWorkoutTags("Push", { exercises: ["Bench Press", "Tricep Pushdown"] }),
    ["Chest", "Triceps"],
  );
  assert.deepEqual(getWorkoutTags("Leg Day", { exercises: [] }), ["Legs"]);
});

test("duration estimates retain the existing bounds and rounding", () => {
  assert.equal(estimateWorkoutDuration({ exercises: [] }), "~30 min");
  assert.equal(
    estimateWorkoutDuration({ exercises: ["A", "B", "C", "D"] }),
    "~45 min",
  );
  assert.equal(
    estimateWorkoutDuration({ exercises: Array.from({ length: 20 }) }),
    "~100 min",
  );
});

test("working-set filtering excludes warm-ups and empty rows", () => {
  assert.deepEqual(
    workSetsOnly([
      { weight: "45", reps: "10", warmup: true },
      { weight: "100", reps: "8" },
      { weight: "", reps: "" },
    ]),
    [{ weight: "100", reps: "8" }],
  );
});

test("exercise profiles use explicit settings", () => {
  const settings = { ...DEFAULT_APP_SETTINGS, defaultWeightJump: 2.5 };
  assert.deepEqual(getExerciseProfile("Flat Bench Press", settings), {
    keys: [
      "bench",
      "squat",
      "deadlift",
      "romanian",
      "rdl",
      "leg press",
      "shoulder press",
      "press",
    ],
    min: 6,
    max: 10,
    increment: 2.5,
    type: "compound",
  });
  assert.equal(getExerciseProfile("Unknown", settings).type, "general");
});

test("target building preserves warm-up exclusion, RPE handling, and stored set fields", () => {
  const settings = {
    ...DEFAULT_APP_SETTINGS,
    defaultWeightJump: 5,
    rpeAware: true,
  };
  const target = buildTargetFromLastSets(
    "Bench Press",
    [
      { weight: "45", reps: "10", rpe: "5", warmup: true, done: true },
      { weight: "100", reps: "10", rpe: "8", warmup: false, done: true },
      { weight: "100", reps: "10", rpe: "8.5", warmup: false, done: true },
    ],
    settings,
  );
  assert.equal(target.weight, "105 lb");
  assert.deepEqual(
    target.targetSets.map((set) => ({
      weight: set.weight,
      reps: set.reps,
      done: set.done,
    })),
    [
      { weight: "105", reps: "6", done: false },
      { weight: "105", reps: "6", done: false },
    ],
  );
  assert.equal(
    buildTargetFromLastSets("Bench Press", [], settings).weight,
    "-",
  );
});

test("progression recommendations preserve high-RPE and rep-range behavior", () => {
  const settings = {
    ...DEFAULT_APP_SETTINGS,
    defaultWeightJump: 5,
    rpeAware: true,
  };
  assert.equal(
    getProgressionRecommendation(
      { weight: "100", reps: "8", rpe: "9.5" },
      "Bench",
      settings,
    ),
    "repeat 100 × 8; last RPE was high",
  );
  assert.equal(
    getProgressionRecommendation(
      { weight: "100", reps: "8", rpe: "8" },
      "Bench",
      settings,
    ),
    "try 100 × 9",
  );
  assert.equal(
    getProgressionRecommendation(
      { weight: "100", reps: "10", rpe: "8" },
      "Bench",
      settings,
    ),
    "try 105 × 6",
  );
  assert.equal(getProgressionRecommendation(null, "Bench", settings), "");
});
