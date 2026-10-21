import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";
const backup = JSON.parse(readFileSync(process.argv[2], "utf8"));
const { data } = prepareBackupImport(backup);
assert.equal(validateImportedApplicationData(data).valid, true);
assert.equal(data.workouts.length, 2);
assert.equal(data.routines.length, 2);
for (const id of [0, "0"]) {
  const workout = data.workouts.find(
    (w) => typeof w.originRoutineId === typeof id && w.originRoutineId === id,
  );
  assert.ok(workout);
  assert.deepEqual(
    workout.exercises.map((e) => e.name),
    ["Flat Bench Press", "My custom lift", "Flat Bench Press"],
  );
  for (const exercise of workout.exercises) {
    assert.deepEqual(Object.keys(exercise).sort(), ["name", "notes", "sets"]);
    assert.equal(exercise.notes, "");
    assert.equal(exercise.sets[0].weight, "0");
  }
  assert.equal(
    data.routines.find((r) => typeof r.id === typeof id && r.id === id)
      .extension,
    "retained",
  );
}
console.log(
  "Native routine sessions pass the PWA importer with typed origins, original ordered names and no catalog payload.",
);
