import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";

const { data } = prepareBackupImport(
  JSON.parse(readFileSync(process.argv[2], "utf8")),
);
assert.equal(validateImportedApplicationData(data).valid, true);
assert.equal(data.goals.weeklyGoal, 6);
assert.equal(data.settings.defaultWeightJump, 2.5);
assert.equal(data.settings.rpeAware, false);
assert.equal(data.settings.haptics, false);
assert.equal(data.settings.animations, false);
if (!process.argv.includes("--device")) {
  const fixture = JSON.parse(
    readFileSync(
      new URL(
        "../tests/fixtures/android/training-backup.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  assert.equal(data.settings.displayName, "Preferences user");
  assert.deepEqual(data.settings.extension, { keep: [0, null] });
  assert.equal(data.settings.compoundMin, 6);
  assert.deepEqual(data.settings.schedule, fixture.settings.schedule);
  assert.deepEqual(data.goals, {
    weeklyGoal: 6,
    targetWeight: 165,
    extension: "keep goal",
  });
  assert.deepEqual(data.workouts, fixture.workouts);
  assert.deepEqual(data.routines, fixture.templates);
  assert.deepEqual(data.legacyWeights, fixture.weights);
} else {
  assert.equal(data.settings.displayName, "Native preferences");
  assert.equal(data.settings.compoundMin, 7);
  assert.equal(data.workouts.length, 2);
}
console.log(
  "Native preferences export passes the PWA importer with settings, goal and retained records.",
);
