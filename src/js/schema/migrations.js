import { DataSchemaError, createValidationError } from "./errors.js";
import { clonePersistedValue, normalizeApplicationData } from "./normalize.js";
import { assertValidApplicationData, isPlainObject, validateApplicationData } from "./validators.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION,
  LEGACY_APPLICATION_SCHEMA_VERSION,
  detectApplicationSchemaVersion,
  detectBackupApplicationSchemaVersion,
  detectBackupFileVersion
} from "./versions.js";

const hasOwn = (value, key) => Object.prototype.hasOwnProperty.call(value, key);

export function migrateLegacyApplicationDataToV1(value, options = {}) {
  assertValidApplicationData(value, {
    legacy: true,
    deferIdConstraints: options.deferIdConstraints,
    source: options.source || "application",
    fromVersion: LEGACY_APPLICATION_SCHEMA_VERSION,
    toVersion: CURRENT_APPLICATION_SCHEMA_VERSION
  });
  const migrated = normalizeApplicationData(value);
  assertValidApplicationData(migrated, {
    deferIdConstraints: options.deferIdConstraints,
    source: options.source || "application",
    fromVersion: LEGACY_APPLICATION_SCHEMA_VERSION,
    toVersion: CURRENT_APPLICATION_SCHEMA_VERSION
  });
  return migrated;
}

export const APPLICATION_MIGRATIONS = Object.freeze({
  [LEGACY_APPLICATION_SCHEMA_VERSION]: migrateLegacyApplicationDataToV1
});

export function migrateApplicationData(value, fromVersion, options = {}) {
  const detectedVersion = detectApplicationSchemaVersion(fromVersion);
  if (detectedVersion === CURRENT_APPLICATION_SCHEMA_VERSION) {
    assertValidApplicationData(value, {
      deferIdConstraints: options.deferIdConstraints,
      source: options.source || "application",
      fromVersion: detectedVersion,
      toVersion: CURRENT_APPLICATION_SCHEMA_VERSION
    });
    return clonePersistedValue(value);
  }

  let version = detectedVersion;
  let current = clonePersistedValue(value);
  const migrations = options.migrations || APPLICATION_MIGRATIONS;
  while (version < CURRENT_APPLICATION_SCHEMA_VERSION) {
    const migrate = migrations[version];
    if (!migrate) {
      throw new DataSchemaError(`No application migration is registered for schema version ${version}.`, {
        code: "missing_application_schema_migration",
        source: options.source || "application",
        fromVersion: version,
        toVersion: version + 1
      });
    }
    current = migrate(current, options);
    version += 1;
    const validation = validateApplicationData(current, { deferIdConstraints: options.deferIdConstraints });
    if (!validation.valid) {
      throw createValidationError(validation.errors, {
        code: "invalid_migration_output",
        source: options.source || "application",
        fromVersion: version - 1,
        toVersion: version
      });
    }
  }
  return current;
}

function assertBackupEnvelopeObject(data) {
  if (!isPlainObject(data)) {
    throw createValidationError([{
      path: "",
      code: "expected_object",
      message: "Backup must be an object."
    }], { source: "backup" });
  }
  if (!hasOwn(data, "workouts")) {
    throw createValidationError([{
      path: "workouts",
      code: "required_field",
      message: "workouts is required."
    }], { source: "backup" });
  }
  if (hasOwn(data, "exportedAt") && (typeof data.exportedAt !== "string" || Number.isNaN(new Date(data.exportedAt).getTime()))) {
    throw createValidationError([{
      path: "exportedAt",
      code: "invalid_timestamp",
      message: "exportedAt must be an ISO timestamp."
    }], { source: "backup" });
  }
}

export function prepareBackupImport(data) {
  assertBackupEnvelopeObject(data);
  const backupFileVersion = detectBackupFileVersion(data);
  const applicationSchemaVersion = detectBackupApplicationSchemaVersion(data, backupFileVersion);
  if (backupFileVersion === CURRENT_BACKUP_FILE_VERSION && !hasOwn(data, "backupFileVersion")) {
    throw new DataSchemaError("Current backup files must use backupFileVersion.", {
      code: "missing_backup_file_version",
      path: "backupFileVersion",
      source: "backup"
    });
  }

  const presence = {
    legacyWeights: hasOwn(data, "weights"),
    routines: hasOwn(data, "templates"),
    settings: hasOwn(data, "settings") && data.settings !== null,
    goals: hasOwn(data, "goals") && data.goals !== null,
    backupMeta: hasOwn(data, "backupMeta") && data.backupMeta !== null
  };
  const applicationData = {
    workouts: data.workouts,
    legacyWeights: hasOwn(data, "weights") ? data.weights : [],
    routines: hasOwn(data, "templates") ? data.templates : [],
    settings: presence.settings ? data.settings : null,
    goals: presence.goals ? data.goals : null,
    draft: null,
    backupMeta: presence.backupMeta ? data.backupMeta : null
  };
  const migratedData = migrateApplicationData(applicationData, applicationSchemaVersion, {
    deferIdConstraints: true,
    source: "backup"
  });
  return {
    backupFileVersion,
    applicationSchemaVersion,
    currentApplicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
    presence,
    data: migratedData
  };
}
