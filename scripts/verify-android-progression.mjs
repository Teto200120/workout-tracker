import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";
const { data } = prepareBackupImport(
  JSON.parse(readFileSync(process.argv[2], "utf8")),
);
assert.equal(validateImportedApplicationData(data).valid, true);
assert.equal(data.workouts.length, 3);
const device = process.argv.includes("--device");
const saved = data.workouts.find((w) =>
  w.exercises.some(
    (e) =>
      e.name === (device ? "Bench Press" : "bench press") &&
      e.sets.some(
        (s) =>
          s.weight === (device ? "102.5" : "100") &&
          s.reps === (device ? "7" : "6"),
      ),
  ),
);
assert.ok(saved);
const sets = saved.exercises.find(
  (e) => e.name === (device ? "Bench Press" : "bench press"),
).sets;
assert.equal(sets.length, 1);
assert.equal(sets[0].done, false);
assert.equal(sets[0].warmup, false);
assert.equal(sets[0].rpe, "8");
assert.deepEqual(Object.keys(sets[0]).sort(), [
  "done",
  "reps",
  "rpe",
  "warmup",
  "weight",
]);
if (!device) {
  const fixture = JSON.parse(
    readFileSync(
      new URL(
        "../tests/fixtures/android/training-backup.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  for (const id of [0, "0"])
    assert.deepEqual(
      data.workouts.find((w) => w.id === id),
      fixture.workouts.find((w) => w.id === id),
    );
  assert.deepEqual(data.settings, fixture.settings);
  assert.deepEqual(data.goals, fixture.goals);
  assert.equal(saved.notes, "Keep session notes");
  assert.equal(saved.exercises[0].notes, "Keep exercise note");
} else {
  assert.equal(data.settings.rpeAware, false);
  assert.equal(data.settings.defaultWeightJump, 2.5);
  assert.equal(data.settings.compoundMin, 7);
  assert.equal(saved.exercises.length, 5);
}
console.log(
  "Native progression export passes the PWA importer: copied targets, unchecked sets, RPE, notes and retained records.",
);
