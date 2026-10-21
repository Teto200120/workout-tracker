import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  workoutVolume,
  totalSets,
  completedSets,
  workoutDurationMinutes,
} from "../../src/js/domain/workout-metrics.js";
import { validateImportedApplicationData } from "../../src/js/domain/input-guardrails.js";
import { prepareBackupImport } from "../../src/js/schema/migrations.js";

const fixture = JSON.parse(
  readFileSync(
    new URL("../fixtures/android/baseline.json", import.meta.url),
    "utf8",
  ),
);
test("Android golden fixture matches actual PWA calculations and retains its data contract", () => {
  const prepared = prepareBackupImport(fixture.backup);
  assert.deepEqual(prepared.data.workouts, fixture.backup.workouts);
  fixture.backup.workouts.forEach((workout, index) => {
    const expected = fixture.expected[index];
    assert.equal(workoutVolume(workout), expected.volume);
    assert.equal(totalSets(workout), expected.workingSets);
    assert.equal(completedSets(workout), expected.completedSets);
    assert.equal(workoutDurationMinutes(workout), expected.duration);
  });
  assert.notEqual(
    typeof prepared.data.workouts[0].id,
    typeof prepared.data.workouts[1].id,
  );
});

const migrations = JSON.parse(
  readFileSync(
    new URL("../fixtures/android/backup-migration.json", import.meta.url),
    "utf8",
  ),
);
test("Android migration fixtures are generated from the PWA migrations without data loss", () => {
  for (const fixture of migrations.cases) {
    assert.equal(
      validateImportedApplicationData(prepareBackupImport(fixture.backup).data)
        .valid,
      true,
    );
    assert.deepEqual(
      prepareBackupImport(fixture.backup).data,
      fixture.expected,
      fixture.name,
    );
  }
});
