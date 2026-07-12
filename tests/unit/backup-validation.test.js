import assert from "node:assert/strict";
import test from "node:test";
import { validateBackupStructure } from "../../src/js/application/backup.js";
import { prepareBackupImport } from "../../src/js/schema/migrations.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION,
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
