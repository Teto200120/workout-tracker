import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";
const root = JSON.parse(readFileSync(process.argv[2], "utf8"));
const { data } = prepareBackupImport(root);
assert.equal(validateImportedApplicationData(data).valid, true);
assert.equal(data.workouts.length, process.argv.includes("--device") ? 3 : 2);
for (const forbidden of [
  "native_guidance",
  "homeTour",
  "activeWorkoutBasics",
  "draft",
])
  assert.equal(JSON.stringify(root).includes(`"${forbidden}"`), false);
assert.ok(data.backupMeta.lastExportedAt);
if (!process.argv.includes("--device")) {
  const source = JSON.parse(
    readFileSync(
      new URL(
        "../tests/fixtures/android/training-backup.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  assert.deepEqual(data.workouts, source.workouts);
  assert.deepEqual(data.routines, source.templates);
  assert.deepEqual(data.settings, source.settings);
  assert.deepEqual(data.goals, source.goals);
}
console.log(
  "Native guidance export passes the PWA importer with saved data retained and device-only tips/drafts excluded.",
);
