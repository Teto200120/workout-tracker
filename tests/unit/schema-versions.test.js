import assert from "node:assert/strict";
import test from "node:test";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION,
  PREVIOUS_APPLICATION_SCHEMA_VERSION,
  detectApplicationSchemaVersion,
  detectBackupApplicationSchemaVersion,
  detectBackupFileVersion,
} from "../../src/js/schema/versions.js";

test("missing schema marker means legacy version zero", () => {
  assert.equal(detectApplicationSchemaVersion(null), 0);
  assert.equal(detectApplicationSchemaVersion(undefined), 0);
});

test("current schema marker is accepted", () => {
  assert.equal(
    detectApplicationSchemaVersion(String(CURRENT_APPLICATION_SCHEMA_VERSION)),
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );
  assert.equal(
    detectApplicationSchemaVersion(CURRENT_APPLICATION_SCHEMA_VERSION),
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );
});

test("the previous display-name schema is accepted for migration", () => {
  assert.equal(
    detectApplicationSchemaVersion(PREVIOUS_APPLICATION_SCHEMA_VERSION),
    PREVIOUS_APPLICATION_SCHEMA_VERSION,
  );
  assert.equal(
    detectApplicationSchemaVersion(String(PREVIOUS_APPLICATION_SCHEMA_VERSION)),
    PREVIOUS_APPLICATION_SCHEMA_VERSION,
  );
});

test("future and invalid schema markers are rejected with stable codes", () => {
  assert.throws(
    () =>
      detectApplicationSchemaVersion(CURRENT_APPLICATION_SCHEMA_VERSION + 1),
    (error) => error.code === "future_application_schema_version",
  );
  for (const marker of ["", " ", "01", "one", -1, 1.5]) {
    assert.throws(
      () => detectApplicationSchemaVersion(marker),
      (error) => error.code === "invalid_application_schema_version",
    );
  }
});

test("backup version detection supports unversioned, v1, v2, and current files", () => {
  assert.equal(detectBackupFileVersion({}), 0);
  assert.equal(detectBackupFileVersion({ version: 1 }), 1);
  assert.equal(detectBackupFileVersion({ version: 2 }), 2);
  assert.equal(
    detectBackupFileVersion({ backupFileVersion: CURRENT_BACKUP_FILE_VERSION }),
    CURRENT_BACKUP_FILE_VERSION,
  );
});

test("backup version detection rejects conflicts and future versions", () => {
  assert.throws(
    () => detectBackupFileVersion({ backupFileVersion: 3, version: 2 }),
    (error) => error.code === "conflicting_backup_file_versions",
  );
  assert.throws(
    () =>
      detectBackupFileVersion({
        backupFileVersion: CURRENT_BACKUP_FILE_VERSION + 1,
      }),
    (error) => error.code === "future_backup_file_version",
  );
});

test("current backups require a separate application schema declaration", () => {
  assert.throws(
    () => detectBackupApplicationSchemaVersion({}, CURRENT_BACKUP_FILE_VERSION),
    (error) => error.code === "missing_application_schema_version",
  );
  assert.equal(detectBackupApplicationSchemaVersion({}, 2), 0);
  assert.equal(
    detectBackupApplicationSchemaVersion(
      { applicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION },
      CURRENT_BACKUP_FILE_VERSION,
    ),
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );
});
