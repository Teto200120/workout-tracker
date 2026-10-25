import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { prepareBackupImport } from "../src/js/schema/migrations.js";
import { validateImportedApplicationData } from "../src/js/domain/input-guardrails.js";
import { DEFAULT_APP_SETTINGS } from "../src/js/core/constants.js";

const [mode, path] = process.argv.slice(2);
const raw = JSON.parse(readFileSync(path, "utf8"));
const { data } = prepareBackupImport(raw);
assert.equal(validateImportedApplicationData(data).valid, true);
assert.equal(JSON.stringify(raw).includes("native_data_epoch"), false);
if (mode === "reset") {
  assert.deepEqual(data.settings, {
    ...DEFAULT_APP_SETTINGS,
    displayName: process.argv.includes("--device")
      ? "Native preferences"
      : "Reset user",
  });
  assert.equal(data.goals.weeklyGoal, 6);
  assert.equal(data.workouts.length, process.argv.includes("--device") ? 3 : 2);
  assert.equal(
    data.legacyWeights.length,
    process.argv.includes("--device") ? 0 : 1,
  );
} else if (mode === "clear") {
  assert.deepEqual(data.settings, DEFAULT_APP_SETTINGS);
  assert.equal(data.workouts.length, 0);
  assert.equal(data.legacyWeights.length, 0);
  assert.deepEqual(data.goals, { weeklyGoal: 4 });
  assert.deepEqual(data.backupMeta, { lastExportedAt: raw.exportedAt });
  const starters = JSON.parse(
    readFileSync(
      new URL(
        "../android/app/src/main/assets/starter-routines.json",
        import.meta.url,
      ),
      "utf8",
    ),
  ).routines;
  assert.deepEqual(
    data.routines.map((r) => r.id).sort(),
    starters.map((r) => r.id).sort(),
  );
  for (const r of data.routines) {
    const source = starters.find((s) => s.id === r.id);
    assert.equal(r.name, source.name);
    assert.deepEqual(
      r.exercises,
      source.exercises.map((e) => e.name),
    );
  }
} else throw new Error("Use reset or clear, then an export path.");
console.log(
  `Native ${mode} export passes the actual PWA importer and retained/default data checks.`,
);
