import assert from "node:assert/strict";
import test from "node:test";
import { validateBackupStructure } from "../../src/js/application/backup.js";
import { prepareBackupImport } from "../../src/js/schema/migrations.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION,
  SUPPORTED_BACKUP_FILE_VERSIONS,
} from "../../src/js/schema/versions.js";
import {
  canonicalWorkout,
  currentBackup,
  getSchemaFixture,
  legacyBackup,
} from "../fixtures/schema-data.js";

test("backup validation migrates legacy arrays and keeps legacy weights optional", () => {
  const withoutWeights = getSchemaFixture("backupWithoutLegacyWeights");
  const validated = validateBackupStructure(withoutWeights);
  assert.equal(validated.workouts[0].id, "legacy-workout");
  assert.deepEqual(validated.legacyWeights, []);
  assert.equal(validated.routines[0].id, "legacy-routine");

  const withWeights = validateBackupStructure(legacyBackup());
  assert.equal(withWeights.legacyWeights[0].weight, 175.5);
  assert.equal(
    prepareBackupImport(legacyBackup()).data.settings.displayName,
    null,
  );
});

test("backups without settings stay settings-less across every supported file version", () => {
  for (const version of SUPPORTED_BACKUP_FILE_VERSIONS) {
    const backup =
      version === CURRENT_BACKUP_FILE_VERSION
        ? currentBackup()
        : legacyBackup({ version });
    if (version === 0) delete backup.version;
    delete backup.settings;

    const prepared = prepareBackupImport(backup);
    assert.equal(prepared.backupFileVersion, version);
    assert.equal(prepared.presence.settings, false);
    assert.equal(prepared.data.settings, null);
    assert.doesNotThrow(() => validateBackupStructure(backup));
  }
});

test("a backup with null settings stays settings-less", () => {
  const prepared = prepareBackupImport(currentBackup({ settings: null }));
  assert.equal(prepared.presence.settings, false);
  assert.equal(prepared.data.settings, null);
});

test("a backup with a null display name preserves the null name", () => {
  const backup = currentBackup();
  backup.settings.displayName = null;
  const prepared = prepareBackupImport(backup);
  assert.equal(prepared.presence.settings, true);
  assert.equal(prepared.data.settings.displayName, null);
});

test("a backup with a valid display name normalizes and preserves the name", () => {
  const backup = currentBackup();
  backup.settings.displayName = "  Zoë 🏋️  ";
  const prepared = prepareBackupImport(backup);
  assert.equal(prepared.presence.settings, true);
  assert.equal(prepared.data.settings.displayName, "Zoë 🏋️");
});

test("backup validation preserves valid names and rejects malformed names atomically", () => {
  const named = currentBackup();
  named.settings.displayName = "  Zoë 🏋️  ";
  const validated = prepareBackupImport(named);
  assert.equal(validated.data.settings.displayName, "Zoë 🏋️");

  for (const displayName of [42, "x".repeat(81), "\u200B\u200C"]) {
    const malformed = currentBackup();
    malformed.settings.displayName = displayName;
    assert.throws(
      () => validateBackupStructure(malformed),
      (error) =>
        ["schema_validation_failed", "backup_guardrail_failed"].includes(
          error.code,
        ) && error.path === "settings.displayName",
    );
  }
});

test("backup validation rejects non-array record collections", () => {
  assert.throws(
    () => validateBackupStructure({ workouts: "not-an-array" }),
    (error) => error.path === "workouts",
  );
  assert.throws(
    () => validateBackupStructure({ workouts: [], templates: {} }),
    (error) => error.path === "routines",
  );
  assert.throws(
    () => validateBackupStructure({ workouts: [], weights: {} }),
    (error) => error.path === "legacyWeights",
  );
});

test("backup validation requires workouts and record IDs", () => {
  assert.throws(
    () => validateBackupStructure({ templates: [] }),
    (error) => error.path === "workouts",
  );
  const missingId = legacyBackup({
    workouts: [{ date: "2026-06-01", type: "Missing ID", exercises: [] }],
  });
  assert.throws(
    () => validateBackupStructure(missingId),
    (error) => error.path === "workouts[0].id",
  );
});

test("structural validation leaves key-path failures to the atomic transaction", () => {
  const backup = getSchemaFixture("nullIdTransactionFailure");
  const validated = validateBackupStructure(backup);
  assert.equal(validated.workouts[0].id, null);
});

test("backup guardrails reject duplicate IDs, extreme numeric values, and oversized text", () => {
  const duplicate = currentBackup();
  duplicate.workouts.push(structuredClone(duplicate.workouts[0]));
  assert.throws(
    () => validateBackupStructure(duplicate),
    (error) =>
      error.code === "backup_guardrail_failed" &&
      error.validationErrors[0].code === "duplicate_id",
  );

  const extreme = currentBackup();
  extreme.workouts[0].exercises[0].sets[0].weight = "300000";
  assert.throws(
    () => validateBackupStructure(extreme),
    (error) =>
      error.code === "backup_guardrail_failed" &&
      error.path === "workouts[0].exercises[0].sets[0].weight",
  );

  const longNote = currentBackup();
  longNote.workouts[0].notes = "x".repeat(4_001);
  assert.throws(
    () => validateBackupStructure(longNote),
    (error) =>
      error.code === "backup_guardrail_failed" &&
      error.path === "workouts[0].notes",
  );
});

test("backup guardrail validation is non-mutating", () => {
  const backup = currentBackup();
  const before = structuredClone(backup);
  validateBackupStructure(backup);
  assert.deepEqual(backup, before);
});

test("old and current backups resolve their separate version concepts", () => {
  const oldPrepared = prepareBackupImport(legacyBackup());
  assert.equal(oldPrepared.backupFileVersion, 2);
  assert.equal(oldPrepared.applicationSchemaVersion, 0);
  assert.equal(
    oldPrepared.currentApplicationSchemaVersion,
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );

  const currentPrepared = prepareBackupImport(currentBackup());
  assert.equal(currentPrepared.backupFileVersion, CURRENT_BACKUP_FILE_VERSION);
  assert.equal(
    currentPrepared.applicationSchemaVersion,
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );
  assert.deepEqual(currentPrepared.data.workouts, [canonicalWorkout()]);
});

test("unversioned and v1 legacy backup envelopes remain importable", () => {
  const unversioned = legacyBackup();
  delete unversioned.version;
  assert.equal(prepareBackupImport(unversioned).backupFileVersion, 0);
  assert.equal(
    prepareBackupImport({ ...legacyBackup(), version: 1 }).backupFileVersion,
    1,
  );
});

test("future backup and application schema versions are rejected distinctly", () => {
  assert.throws(
    () => prepareBackupImport(getSchemaFixture("futureBackupFileVersion")),
    (error) => error.code === "future_backup_file_version",
  );
  assert.throws(
    () =>
      prepareBackupImport(
        currentBackup({
          applicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION + 1,
        }),
      ),
    (error) => error.code === "future_application_schema_version",
  );
});
