import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";
const backup = JSON.parse(readFileSync(process.argv[2], "utf8"));
const { data } = prepareBackupImport(backup);
assert.equal(validateImportedApplicationData(data).valid, true);
assert.equal(data.workouts.length, 5);
assert.equal(data.settings.displayName, "Upgrade Fixture");
assert.equal(data.settings.rpeAware, false);
const fixture = JSON.parse(
  readFileSync(
    new URL("../tests/fixtures/android/training-backup.json", import.meta.url),
    "utf8",
  ),
);
for (const original of fixture.workouts) {
  assert.deepEqual(
    data.workouts.find(
      (w) => typeof w.id === typeof original.id && w.id === original.id,
    ),
    original,
  );
}
assert.deepEqual(data.routines, fixture.templates);
assert.deepEqual(data.goals, fixture.goals);
const native = data.workouts.filter(
  (w) => typeof w.id === "string" && w.id !== "0",
);
assert.equal(native.length, 3);
assert.equal(native.filter((w) => w.originRoutineId === 0).length, 1);
for (const workout of native) {
  assert.deepEqual(
    workout.exercises.map((e) => e.name),
    ["Flat Bench Press", "My custom lift", "Flat Bench Press"],
  );
  assert.equal(workout.exercises[0].sets[1].weight, "0");
  assert.equal(workout.exercises[0].sets[1].rpe, "7.5");
  assert.equal(workout.exercises[0].sets[0].warmup, true);
  assert.equal(workout.exercises[0].sets[1].done, false);
  assert.equal(workout.exercises[1].sets[0].reps, "");
  for (const exercise of workout.exercises)
    assert.deepEqual(Object.keys(exercise).sort(), ["name", "notes", "sets"]);
}
console.log(
  "Lifecycle export passes the PWA importer: installed upgrade, retained typed records and recovered workout saved once.",
);
