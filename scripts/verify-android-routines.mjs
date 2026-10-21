import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";

const { data } = prepareBackupImport(
  JSON.parse(readFileSync(process.argv[2], "utf8")),
);
assert.equal(validateImportedApplicationData(data).valid, true);
const fixture = JSON.parse(
  readFileSync(
    new URL("../tests/fixtures/android/training-backup.json", import.meta.url),
    "utf8",
  ),
);
assert.equal(data.routines.length, 1);
const routine = data.routines[0];
assert.equal(routine.id, 0);
assert.equal(routine.name, "Edited fixture");
assert.deepEqual(
  routine.exercises,
  process.argv.includes("--device")
    ? ["Fixture replacement", "Flat Bench Press", "Flat Bench Press"]
    : ["My custom lift", "Flat Bench Press", "Flat Bench Press"],
);
for (const [key, value] of Object.entries(fixture.templates[0])) {
  if (!["name", "exercises", "updatedAt"].includes(key))
    assert.deepEqual(routine[key], value);
}
for (const original of fixture.workouts)
  assert.deepEqual(
    data.workouts.find(
      (w) => typeof w.id === typeof original.id && w.id === original.id,
    ),
    original,
  );
assert.deepEqual(data.goals, fixture.goals);
console.log(
  "Edited native routine passes the PWA importer: typed ID, extensions, ordered repeated names and existing workouts retained.",
);
