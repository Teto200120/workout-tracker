import assert from "node:assert/strict";
import test from "node:test";
import {
  validateApplicationData,
  validateDraft,
  validateExercise,
  validateGoals,
  validateRoutine,
  validateSet,
  validateSettings,
  validateWorkout,
} from "../../src/js/schema/validators.js";
import {
  canonicalApplicationData,
  canonicalDraft,
  canonicalExercise,
  canonicalGoals,
  canonicalRoutine,
  canonicalSet,
  canonicalSettings,
  canonicalWorkout,
  getSchemaFixture,
} from "../fixtures/schema-data.js";

test("canonical application and record contracts validate", () => {
  assert.equal(validateApplicationData(canonicalApplicationData()).valid, true);
  assert.equal(validateSet(canonicalSet()).valid, true);
  assert.equal(validateExercise(canonicalExercise()).valid, true);
  assert.equal(validateWorkout(canonicalWorkout()).valid, true);
  assert.equal(validateRoutine(canonicalRoutine()).valid, true);
  assert.equal(validateSettings(canonicalSettings()).valid, true);
  assert.equal(validateGoals(canonicalGoals()).valid, true);
  assert.equal(validateDraft(canonicalDraft()).valid, true);
});

test("valid zero values and missing optional duration are accepted", () => {
  assert.equal(
    validateApplicationData(getSchemaFixture("validZeroValues")).valid,
    true,
  );
  assert.equal(
    validateApplicationData(getSchemaFixture("missingOptionalFields")).valid,
    true,
  );
});

test("absent local data is represented by null rather than undefined", () => {
  const data = canonicalApplicationData({ draft: undefined });
  const result = validateApplicationData(data);
  assert.equal(result.valid, false);
  assert.deepEqual(result.errors[0], {
    path: "draft",
    code: "expected_object_or_null",
    message: "draft must be an object or null.",
  });
});

test("required arrays and nested malformed values report exact paths", () => {
  const workout = canonicalWorkout({ exercises: "invalid" });
  assert.deepEqual(validateWorkout(workout).errors[0], {
    path: "workout.exercises",
    code: "expected_array",
    message: "Exercises must be an array.",
  });

  const exercise = canonicalExercise({ sets: [canonicalSet(), "invalid"] });
  assert.equal(validateExercise(exercise).errors[0].path, "exercise.sets[1]");
  assert.equal(validateExercise(exercise).errors[0].code, "expected_object");
});

test("missing IDs fail schema validation while deferred null IDs remain a storage constraint", () => {
  const missing = canonicalWorkout();
  delete missing.id;
  assert.equal(validateWorkout(missing).errors[0].path, "workout.id");
  assert.equal(validateWorkout(canonicalWorkout({ id: null })).valid, false);
  assert.equal(
    validateWorkout(canonicalWorkout({ id: null }), {
      deferIdConstraints: true,
    }).valid,
    true,
  );
});

test("empty strings and null are handled deliberately", () => {
  assert.equal(
    validateExercise(canonicalExercise({ name: "", notes: "" })).valid,
    true,
  );
  assert.equal(
    validateSet(canonicalSet({ weight: "", reps: "", rpe: "" })).valid,
    true,
  );
  assert.equal(
    validateSet(canonicalSet({ weight: null })).errors[0].path,
    "set.weight",
  );
  assert.equal(
    validateDraft(canonicalDraft({ editingWorkoutId: null })).valid,
    true,
  );
});

test("unknown fields are allowed without weakening known-field validation", () => {
  const workout = canonicalWorkout({ unknownRecordField: { retained: true } });
  workout.exercises[0].unknownExerciseField = "retained";
  workout.exercises[0].sets[0].unknownSetField = 0;
  assert.equal(validateWorkout(workout).valid, true);
  assert.equal(
    validateSet(canonicalSet({ rpe: "11", unknown: true })).errors[0].code,
    "out_of_range",
  );
});

test("malformed fixture paths distinguish workout, exercise, set, and routine failures", () => {
  const cases = [
    ["malformedWorkout", "workouts[0].exercises"],
    ["malformedExercise", "workouts[0].exercises[0]"],
    ["malformedSet", "workouts[0].exercises[0].sets[0]"],
    ["malformedRoutine", "routines[0].exercises"],
  ];
  for (const [fixtureName, expectedPath] of cases) {
    const backup = getSchemaFixture(fixtureName);
    const data = {
      workouts: backup.workouts,
      legacyWeights: backup.weights || [],
      routines: backup.templates || [],
      settings: backup.settings || null,
      goals: backup.goals || null,
      draft: null,
      backupMeta: backup.backupMeta || null,
    };
    const result = validateApplicationData(data, { legacy: true });
    assert.equal(result.valid, false);
    assert.equal(result.errors[0].path, expectedPath);
  }
});
