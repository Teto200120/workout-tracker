import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";
import { workoutDurationMinutes } from "../src/js/domain/workout-metrics.js";

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
const edited = data.workouts.find(
  (w) => typeof w.id === "number" && w.id === 0,
);
assert.ok(edited);
assert.equal(edited.type, "Reviewed workout");
assert.equal(edited.notes, "History reviewed");
assert.equal(edited.exercises[0].sets[0].reps, "6");
assert.deepEqual(edited.tags, ["baseline", "Good session"]);
assert.equal(workoutDurationMinutes(edited), 20);
const expected = structuredClone(fixture.workouts[0]);
expected.type = "Reviewed workout";
expected.notes = "History reviewed";
expected.exercises[0].sets[0].reps = "6";
expected.tags.push("Good session");
delete expected.durationMinutes;
assert.deepEqual(edited, expected);
assert.deepEqual(
  data.workouts.find((w) => typeof w.id === "string" && w.id === "0"),
  fixture.workouts[1],
);
assert.deepEqual(data.goals, fixture.goals);
if (!process.argv.includes("--device")) {
  assert.deepEqual(data.settings, fixture.settings);
  assert.deepEqual(data.templates, fixture.templates);
}
console.log(
  "Native History export passes the PWA importer: reviewed fields, nested extensions, typed IDs and other records retained.",
);
