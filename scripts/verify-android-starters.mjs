import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";

const { data } = prepareBackupImport(
  JSON.parse(readFileSync(process.argv[2], "utf8")),
);
assert.equal(validateImportedApplicationData(data).valid, true);
const manifest = JSON.parse(
  readFileSync(
    new URL(
      "../android/app/src/main/assets/starter-routines.json",
      import.meta.url,
    ),
    "utf8",
  ),
);
assert.equal(data.routines.length, 6);
for (const starter of manifest.routines) {
  const actual = data.routines.find((routine) => routine.id === starter.id);
  assert.ok(actual, starter.name);
  assert.deepEqual(Object.keys(actual).sort(), [
    "createdAt",
    "exercises",
    "id",
    "name",
    "updatedAt",
  ]);
  assert.equal(actual.name, starter.name);
  assert.deepEqual(
    actual.exercises,
    starter.exercises.map((exercise) => exercise.name),
  );
}
console.log(
  "Native starter export passes the PWA importer: six matching IDs, names and ordered name-only exercise arrays.",
);
