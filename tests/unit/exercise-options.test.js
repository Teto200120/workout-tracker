import test from "node:test";
import assert from "node:assert/strict";

import {
  buildExerciseOptions,
  dedupeExerciseNames,
  normalizeExerciseName,
  resolveExerciseName,
  searchExerciseOptions,
} from "../../src/js/domain/exercise-options.js";

test("exercise names are trimmed and empty names are rejected", () => {
  assert.equal(normalizeExerciseName("  Landmine Press  "), "Landmine Press");
  assert.equal(normalizeExerciseName("   "), "");
  assert.equal(normalizeExerciseName(null), "");
});

test("exercise names are deduplicated case-insensitively with the first display name preserved", () => {
  assert.deepEqual(
    dedupeExerciseNames([
      " Flat Bench Press ",
      "flat bench press",
      "FLAT BENCH PRESS",
      "Incline Press",
      "",
      "   ",
    ]),
    ["Flat Bench Press", "Incline Press"],
  );
});

test("exercise options combine defaults, saved routines, history, and the active workout", () => {
  const options = buildExerciseOptions({
    defaultRoutines: [
      { name: "Push", exercises: ["Flat Bench Press", "Incline Press"] },
    ],
    routines: [
      { name: "Custom", exercises: ["Landmine Press", " incline press "] },
    ],
    workouts: [
      {
        exercises: [
          { name: "Cable Fly" },
          { name: "landmine press" },
          { name: "  " },
        ],
      },
    ],
    currentExercises: ["Push-Up", "cable fly"],
  });

  assert.deepEqual(options, [
    "Cable Fly",
    "Flat Bench Press",
    "Incline Press",
    "Landmine Press",
    "Push-Up",
  ]);
});

test("exercise search uses a case-insensitive substring match", () => {
  const options = ["Flat Bench Press", "Incline Press", "Cable Bicep Curl"];
  assert.deepEqual(searchExerciseOptions(options, "PRESS"), [
    "Flat Bench Press",
    "Incline Press",
  ]);
  assert.deepEqual(searchExerciseOptions(options, "  bicep  "), [
    "Cable Bicep Curl",
  ]);
  assert.deepEqual(searchExerciseOptions(options, "missing"), []);
});

test("a custom name matching an existing option uses its canonical display name", () => {
  const options = ["Flat Bench Press", "Landmine Press"];
  assert.equal(
    resolveExerciseName("  flat bench press  ", options),
    "Flat Bench Press",
  );
  assert.equal(
    resolveExerciseName("  Half-Kneeling Press  ", options),
    "Half-Kneeling Press",
  );
  assert.equal(resolveExerciseName("   ", options), "");
});
