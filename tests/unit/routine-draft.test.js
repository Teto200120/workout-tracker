import assert from "node:assert/strict";
import test from "node:test";
import {
  applyRoutineExerciseSelection,
  hasRoutineExerciseDuplicate,
} from "../../src/js/domain/routine-draft.js";

test("routine add appends a normalized name without mutating existing rows", () => {
  const exercises = ["Flat Bench", "My Saturday Row"];
  const before = structuredClone(exercises);
  const next = applyRoutineExerciseSelection(exercises, {
    mode: "add",
    name: "  Bench Press  ",
  });
  assert.deepEqual(next, ["Flat Bench", "My Saturday Row", "Bench Press"]);
  assert.deepEqual(exercises, before);
});

test("routine replace changes only the intended row and preserves custom names", () => {
  const exercises = ["Flat Bench", "My Saturday Row", "V-Bar Pulldown"];
  const next = applyRoutineExerciseSelection(exercises, {
    mode: "replace",
    index: 1,
    name: "Air Bike",
  });
  assert.deepEqual(next, ["Flat Bench", "Air Bike", "V-Bar Pulldown"]);
  assert.deepEqual(exercises, [
    "Flat Bench",
    "My Saturday Row",
    "V-Bar Pulldown",
  ]);
});

test("duplicate checks exclude the row being replaced but preserve intentional duplicate semantics", () => {
  const exercises = ["Flat Bench", "Air Bike", "flat   bench"];
  assert.equal(hasRoutineExerciseDuplicate(exercises, "FLAT BENCH"), true);
  assert.equal(
    hasRoutineExerciseDuplicate(["Air Bike"], "air bike", { excludeIndex: 0 }),
    false,
  );
  assert.equal(
    hasRoutineExerciseDuplicate(exercises, "flat bench", { excludeIndex: 0 }),
    true,
  );
});

test("routine selection rejects invalid names, modes, and replacement indexes", () => {
  assert.throws(
    () => applyRoutineExerciseSelection([], { mode: "add", name: "   " }),
    /required/u,
  );
  assert.throws(
    () =>
      applyRoutineExerciseSelection(["Squat"], { mode: "swap", name: "Row" }),
    /Unsupported routine selection mode/u,
  );
  assert.throws(
    () =>
      applyRoutineExerciseSelection(["Squat"], {
        mode: "replace",
        index: 4,
        name: "Row",
      }),
    /out of range/u,
  );
});
