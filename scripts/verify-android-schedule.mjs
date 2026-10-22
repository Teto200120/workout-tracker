import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";

const { data } = prepareBackupImport(
  JSON.parse(readFileSync(process.argv[2], "utf8")),
);
assert.equal(validateImportedApplicationData(data).valid, true);
for (let index = 0; index < 7; index++) {
  assert.equal(data.settings.schedule[index].kind, "gym");
  assert.equal(data.settings.schedule[index].routine, "Legs");
}
const fixture = JSON.parse(
  readFileSync(
    new URL("../tests/fixtures/android/training-backup.json", import.meta.url),
    "utf8",
  ),
);
for (const [key, value] of Object.entries(fixture.settings))
  if (key !== "schedule") assert.deepEqual(data.settings[key], value);
for (const original of fixture.workouts)
  assert.deepEqual(
    data.workouts.find(
      (w) => typeof w.id === typeof original.id && w.id === original.id,
    ),
    original,
  );
assert.deepEqual(data.goals, fixture.goals);
if (!process.argv.includes("--device")) {
  assert.deepEqual(data.settings.extension, { keep: true });
  assert.deepEqual(data.settings.schedule.extension, { keep: 1 });
  assert.equal(data.settings.schedule[0].extension, "day field");
}
console.log(
  "Native schedule export passes the PWA importer: seven day plans, unrelated preferences, extensions and existing workouts retained.",
);
