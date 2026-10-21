import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import {
  validateImportedApplicationData,
  validateBackupComplexity,
} from "../src/js/domain/input-guardrails.js";
const path = process.argv[2];
assert.ok(path, "Pass the native-generated backup path");
const backup = JSON.parse(readFileSync(path, "utf8"));
assert.equal(validateBackupComplexity(backup).valid, true);
const prepared = prepareBackupImport(backup);
assert.equal(validateImportedApplicationData(prepared.data).valid, true);
assert.equal(backup.backupFileVersion, 3);
assert.equal(backup.applicationSchemaVersion, 2);
const fixtures = JSON.parse(
  readFileSync(
    new URL("../tests/fixtures/android/backup-migration.json", import.meta.url),
    "utf8",
  ),
);
const expected = fixtures.cases.at(-1).expected;
assert.equal(prepared.data.workouts.length, 3);
for (const workout of expected.workouts)
  assert.deepEqual(
    prepared.data.workouts.find(
      (w) => typeof w.id === typeof workout.id && w.id === workout.id,
    ),
    workout,
  );
assert.deepEqual(prepared.data.routines, expected.routines);
assert.deepEqual(prepared.data.legacyWeights, expected.legacyWeights);
assert.deepEqual(prepared.data.goals, expected.goals);
assert.deepEqual(prepared.data.settings, {
  ...expected.settings,
  displayName: "Android User",
  rpeAware: false,
});
assert.deepEqual(prepared.data.backupMeta, {
  ...expected.backupMeta,
  lastExportedAt: "2026-10-21T18:00:00Z",
});
const native = prepared.data.workouts.find(
  (w) => w.exercises[0]?.name === "Native exercise",
);
assert.equal(typeof native.id, "string");
assert.equal(native.durationMinutes, 0);
assert.deepEqual(native.exercises[0].sets[0], {
  weight: "0",
  reps: "10",
  rpe: "8.5",
  done: false,
  warmup: false,
});
assert.equal(backup.draft, undefined);
console.log(
  "Native backup passes the real PWA importer and semantic guardrails; records, extensions, preferences and native sets round-trip.",
);
