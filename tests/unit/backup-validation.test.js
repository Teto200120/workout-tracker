import assert from "node:assert/strict";
import test from "node:test";
import { validateBackupStructure } from "../../src/js/application/backup.js";

const workout = { id: "workout-1" };
const routine = { id: "routine-1" };
const legacyWeight = { id: "weight-1" };

test("backup validation accepts arrays and keeps legacy weights optional", () => {
  assert.deepEqual(
    validateBackupStructure({ workouts: [workout], templates: [routine] }),
    {
      workouts: [workout],
      legacyWeights: [],
      routines: [routine],
    },
  );
  assert.deepEqual(
    validateBackupStructure({ workouts: [workout], weights: [legacyWeight] }),
    {
      workouts: [workout],
      legacyWeights: [legacyWeight],
      routines: [],
    },
  );
});

test("backup validation rejects non-array record collections", () => {
  assert.throws(
    () => validateBackupStructure({ workouts: "not-an-array" }),
    /Invalid backup file/,
  );
  assert.throws(
    () => validateBackupStructure({ workouts: [], templates: {} }),
    /Invalid backup file/,
  );
  assert.throws(
    () => validateBackupStructure({ workouts: [], weights: {} }),
    /Invalid backup file/,
  );
});

test("backup validation requires workouts and record IDs", () => {
  assert.throws(
    () => validateBackupStructure({ templates: [] }),
    /Invalid backup file/,
  );
  assert.throws(
    () => validateBackupStructure({ workouts: [{}] }),
    /Invalid backup file/,
  );
});

test("structural validation leaves key-path failures to the atomic transaction", () => {
  const invalidKeyWorkout = { id: null };
  assert.deepEqual(validateBackupStructure({ workouts: [invalidKeyWorkout] }), {
    workouts: [invalidKeyWorkout],
    legacyWeights: [],
    routines: [],
  });
});
