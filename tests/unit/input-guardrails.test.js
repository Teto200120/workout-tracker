import assert from "node:assert/strict";
import test from "node:test";
import {
  INPUT_LIMITS,
  nameComparisonKey,
  textLength,
  validateBackupComplexity,
  validateBackupFileSize,
  validateCollectionSize,
  validateCompletionNote,
  validateDateInput,
  validateDefaultWeightJump,
  validateExerciseName,
  validateExerciseNotes,
  validateImportedApplicationData,
  validateRepRange,
  validateRepetitions,
  validateRpe,
  validateRoutineInput,
  validateTimeInput,
  validateWeeklyGoal,
  validateWeight,
  validateWorkoutInput,
  validateWorkoutNotes,
  validateWorkoutTiming,
} from "../../src/js/domain/input-guardrails.js";
import {
  canonicalApplicationData,
  canonicalRoutine,
  canonicalWorkout,
} from "../fixtures/schema-data.js";

test("weight parsing accepts blank, zero, negative zero, decimals, leading zeros, whitespace, and decimal comma", () => {
  for (const value of ["", "0", "-0", "12.25", "00045", " 45 ", "12,5"]) {
    assert.equal(validateWeight(value).valid, true, value);
  }
  assert.equal(validateWeight("12,5").normalized, "12.5");
});

test("weight parsing rejects negatives, non-finite values, scientific notation, text, excess precision, and the hard maximum", () => {
  for (const value of [
    "-1",
    Number.NaN,
    Infinity,
    -Infinity,
    "1e6",
    "abc",
    "12lb",
    "1.234",
  ]) {
    assert.equal(validateWeight(value).valid, false, String(value));
  }
  assert.equal(validateWeight(String(INPUT_LIMITS.weight)).valid, true);
  assert.equal(validateWeight(String(INPUT_LIMITS.weight + 0.01)).valid, false);
  assert.equal(validateWeight("300000").valid, false);
});

test("repetitions preserve blank and zero while requiring a finite whole number within the hard maximum", () => {
  assert.equal(validateRepetitions("").valid, true);
  assert.equal(validateRepetitions("0").valid, true);
  assert.equal(validateRepetitions("0008").valid, true);
  assert.equal(
    validateRepetitions(String(INPUT_LIMITS.repetitions)).valid,
    true,
  );
  for (const value of [
    "-1",
    "8.5",
    "1e3",
    "NaN",
    Infinity,
    String(INPUT_LIMITS.repetitions + 1),
    "300000",
  ]) {
    assert.equal(validateRepetitions(value).valid, false, String(value));
  }
});

test("RPE uses the optional 1-10 half-step UI scale while retaining an explicit historical-zero mode", () => {
  assert.equal(validateRpe("").valid, true);
  assert.equal(validateRpe("1").valid, true);
  assert.equal(validateRpe("8.5").valid, true);
  assert.equal(validateRpe("10").valid, true);
  assert.equal(validateRpe("0").valid, false);
  assert.equal(validateRpe("0", { allowHistoricalZero: true }).valid, true);
  for (const value of ["-0.5", "8.3", "10.5", "11", "1e1", Infinity]) {
    assert.equal(validateRpe(value).valid, false, String(value));
  }
});

test("settings numbers enforce weight-jump, weekly-goal, and ordered rep-range bounds", () => {
  assert.equal(validateDefaultWeightJump("0.5").valid, true);
  assert.equal(validateDefaultWeightJump("0.25").valid, false);
  assert.equal(validateDefaultWeightJump("1000").valid, true);
  assert.equal(validateDefaultWeightJump("1000.01").valid, false);
  assert.equal(validateWeeklyGoal("1").valid, true);
  assert.equal(validateWeeklyGoal("100").valid, true);
  assert.equal(validateWeeklyGoal("0").valid, false);
  assert.equal(validateWeeklyGoal("101").valid, false);
  assert.equal(validateWeeklyGoal("4.5").valid, false);
  assert.equal(validateRepRange("6", "10").valid, true);
  assert.equal(validateRepRange("10", "6").valid, false);
  assert.equal(validateRepRange("1", "1000").valid, true);
  assert.equal(validateRepRange("1", "1001").valid, false);
});

test("name validation trims boundaries, preserves Unicode, and rejects invisible, control-character, or oversized names", () => {
  const unicode = "  Élévation 肩 💪  ";
  assert.equal(validateExerciseName(unicode).normalized, "Élévation 肩 💪");
  assert.equal(validateExerciseName("   ").valid, false);
  assert.equal(validateExerciseName("\u200b\u200d").valid, false);
  assert.equal(validateExerciseName("Row\nPress").valid, false);
  assert.equal(validateExerciseName("<script>alert(1)</script>").valid, true);
  assert.equal(
    validateExerciseName("x".repeat(INPUT_LIMITS.exerciseNameLength)).valid,
    true,
  );
  assert.equal(
    validateExerciseName("x".repeat(INPUT_LIMITS.exerciseNameLength + 1)).valid,
    false,
  );
  assert.equal(textLength("💪"), 1);
});

test("name comparison is case-insensitive and spacing-insensitive without changing display text", () => {
  assert.equal(nameComparisonKey("  Flat   Bench Press "), "flat bench press");
  assert.equal(
    nameComparisonKey("Flat Bench Press"),
    nameComparisonKey(" flat   BENCH press "),
  );
});

test("notes preserve line breaks, emoji, punctuation, and HTML-like text while rejecting unsafe controls and overflow", () => {
  const accepted = "Line one\nQuotes ' \" & <script>text only</script> 💪";
  assert.equal(validateWorkoutNotes(accepted).valid, true);
  assert.equal(validateExerciseNotes("مرحبا — café — 日本語").valid, true);
  assert.equal(validateCompletionNote("emoji ✅").valid, true);
  assert.equal(validateExerciseNotes("bad\u0000value").valid, false);
  assert.equal(
    validateWorkoutNotes("x".repeat(INPUT_LIMITS.workoutNoteLength)).valid,
    true,
  );
  assert.equal(
    validateWorkoutNotes("x".repeat(INPUT_LIMITS.workoutNoteLength + 1)).valid,
    false,
  );
});

test("collection guardrails accept the exact boundary and reject one above it", () => {
  const exactExercises = validateCollectionSize(
    Array(INPUT_LIMITS.exercisesPerWorkout).fill(null),
    { label: "Exercises", maximum: INPUT_LIMITS.exercisesPerWorkout },
  );
  const tooManyExercises = validateCollectionSize(
    Array(INPUT_LIMITS.exercisesPerWorkout + 1).fill(null),
    { label: "Exercises", maximum: INPUT_LIMITS.exercisesPerWorkout },
  );
  assert.equal(exactExercises.valid, true);
  assert.equal(tooManyExercises.valid, false);

  const exactRoutine = canonicalRoutine({
    exercises: Array(INPUT_LIMITS.exercisesPerRoutine).fill("Air Bike"),
  });
  const oversizedRoutine = canonicalRoutine({
    exercises: Array(INPUT_LIMITS.exercisesPerRoutine + 1).fill("Air Bike"),
  });
  assert.equal(validateRoutineInput(exactRoutine).valid, true);
  assert.equal(validateRoutineInput(oversizedRoutine).valid, false);
});

test("date and time validation handles real dates, leap days, invalid clocks, optional time, and midnight crossover", () => {
  assert.equal(validateDateInput("2028-02-29").valid, true);
  assert.equal(validateDateInput("2027-02-29").valid, false);
  assert.equal(validateDateInput("2026-13-01").valid, false);
  assert.equal(validateDateInput("").valid, false);
  assert.equal(validateTimeInput("").valid, true);
  assert.equal(validateTimeInput("23:59").valid, true);
  assert.equal(validateTimeInput("24:00").valid, false);
  assert.equal(validateTimeInput("24:99").valid, false);
  const overnight = validateWorkoutTiming({
    date: "2026-07-13",
    startTime: "23:30",
    endTime: "00:15",
  });
  assert.equal(overnight.valid, true);
  assert.equal(overnight.warnings[0].code, "overnight_time");
});

test("workout validation rejects extreme values without mutating the workout", () => {
  const workout = canonicalWorkout();
  workout.exercises[0].sets[0].weight = "300000";
  const before = structuredClone(workout);
  const result = validateWorkoutInput(workout);
  assert.equal(result.valid, false);
  assert.equal(result.errors[0].path, "workout.exercises[0].sets[0].weight");
  assert.deepEqual(workout, before);
});

test("backup file, depth, and node limits are checked independently of JSON parsing", () => {
  assert.equal(validateBackupFileSize(0).valid, false);
  assert.equal(
    validateBackupFileSize(INPUT_LIMITS.backupFileBytes).valid,
    true,
  );
  assert.equal(
    validateBackupFileSize(INPUT_LIMITS.backupFileBytes + 1).valid,
    false,
  );
  assert.equal(validateBackupComplexity({ workouts: [] }).valid, true);
  let deep = {};
  let cursor = deep;
  for (let index = 0; index < INPUT_LIMITS.backupDepth + 2; index += 1) {
    cursor.next = {};
    cursor = cursor.next;
  }
  assert.equal(validateBackupComplexity(deep).valid, false);
  assert.equal(
    validateBackupComplexity([1, 2, 3], { maximumNodes: 2 }).valid,
    false,
  );
});

test("import guardrails reject duplicate IDs, extreme values, and long text without mutating prepared data", () => {
  const duplicate = canonicalApplicationData();
  duplicate.workouts.push(structuredClone(duplicate.workouts[0]));
  const duplicateBefore = structuredClone(duplicate);
  assert.equal(validateImportedApplicationData(duplicate).valid, false);
  assert.equal(
    validateImportedApplicationData(duplicate).errors.some(
      (error) => error.code === "duplicate_id",
    ),
    true,
  );
  assert.deepEqual(duplicate, duplicateBefore);

  const extreme = canonicalApplicationData();
  extreme.workouts[0].exercises[0].sets[0].reps = "300000";
  assert.equal(validateImportedApplicationData(extreme).valid, false);

  const longText = canonicalApplicationData();
  longText.workouts[0].notes = "x".repeat(INPUT_LIMITS.workoutNoteLength + 1);
  assert.equal(validateImportedApplicationData(longText).valid, false);

  const routineBoundary = canonicalApplicationData();
  const routine = routineBoundary.routines[0];
  routineBoundary.routines = Array.from(
    { length: INPUT_LIMITS.routines },
    (_, index) => ({
      ...structuredClone(routine),
      id: `routine-${index}`,
      name: `Routine ${index}`,
    }),
  );
  assert.equal(validateImportedApplicationData(routineBoundary).valid, true);
  routineBoundary.routines.push({
    ...structuredClone(routine),
    id: "routine-one-above",
    name: "Routine one above",
  });
  const tooManyRoutines = validateImportedApplicationData(routineBoundary);
  assert.equal(tooManyRoutines.valid, false);
  assert.equal(
    tooManyRoutines.errors.some(
      (error) => error.code === "collection_too_large",
    ),
    true,
  );
});
