import { DataSchemaError } from "./errors.js";

export const LEGACY_APPLICATION_SCHEMA_VERSION = 0;
export const PREVIOUS_APPLICATION_SCHEMA_VERSION = 1;
export const CURRENT_APPLICATION_SCHEMA_VERSION = 2;

export const UNVERSIONED_BACKUP_FILE_VERSION = 0;
export const CURRENT_BACKUP_FILE_VERSION = 3;
export const SUPPORTED_BACKUP_FILE_VERSIONS = Object.freeze([0, 1, 2, 3]);

function parseVersion(value, options) {
  if (typeof value === "string" && !/^(0|[1-9]\d*)$/.test(value)) {
    throw new DataSchemaError(options.message, {
      code: options.code,
      path: options.path,
      source: options.source
    });
  }
  const version = typeof value === "number" ? value : Number(value);
  if (value === "" || !Number.isInteger(version) || version < 0) {
    throw new DataSchemaError(options.message, {
      code: options.code,
      path: options.path,
      source: options.source
    });
  }
  return version;
}

export function detectApplicationSchemaVersion(marker) {
  if (marker === null || marker === undefined) return LEGACY_APPLICATION_SCHEMA_VERSION;
  const version = parseVersion(marker, {
    message: "Invalid application data-schema version marker.",
    code: "invalid_application_schema_version",
    path: "applicationSchemaVersion",
    source: "application"
  });
  if (version > CURRENT_APPLICATION_SCHEMA_VERSION) {
    throw new DataSchemaError("Application data was created by a newer schema version.", {
      code: "future_application_schema_version",
      path: "applicationSchemaVersion",
      source: "application",
      fromVersion: version,
      toVersion: CURRENT_APPLICATION_SCHEMA_VERSION
    });
  }
  if (![LEGACY_APPLICATION_SCHEMA_VERSION, PREVIOUS_APPLICATION_SCHEMA_VERSION, CURRENT_APPLICATION_SCHEMA_VERSION].includes(version)) {
    throw new DataSchemaError("Unsupported application data-schema version.", {
      code: "unsupported_application_schema_version",
      path: "applicationSchemaVersion",
      source: "application",
      fromVersion: version,
      toVersion: CURRENT_APPLICATION_SCHEMA_VERSION
    });
  }
  return version;
}

export function detectBackupFileVersion(data) {
  const hasExplicit = Object.prototype.hasOwnProperty.call(data, "backupFileVersion");
  const hasLegacy = Object.prototype.hasOwnProperty.call(data, "version");
  if (hasExplicit && hasLegacy && Number(data.backupFileVersion) !== Number(data.version)) {
    throw new DataSchemaError("Backup version fields disagree.", {
      code: "conflicting_backup_file_versions",
      path: "backupFileVersion",
      source: "backup"
    });
  }

  const rawVersion = hasExplicit ? data.backupFileVersion : hasLegacy ? data.version : UNVERSIONED_BACKUP_FILE_VERSION;
  const version = parseVersion(rawVersion, {
    message: "Invalid backup-file version.",
    code: "invalid_backup_file_version",
    path: hasExplicit ? "backupFileVersion" : hasLegacy ? "version" : "backupFileVersion",
    source: "backup"
  });
  if (version > CURRENT_BACKUP_FILE_VERSION) {
    throw new DataSchemaError("Backup was created by a newer application version.", {
      code: "future_backup_file_version",
      path: hasExplicit ? "backupFileVersion" : "version",
      source: "backup",
      fromVersion: version,
      toVersion: CURRENT_BACKUP_FILE_VERSION
    });
  }
  if (!SUPPORTED_BACKUP_FILE_VERSIONS.includes(version)) {
    throw new DataSchemaError("Unsupported backup-file version.", {
      code: "unsupported_backup_file_version",
      path: hasExplicit ? "backupFileVersion" : "version",
      source: "backup",
      fromVersion: version,
      toVersion: CURRENT_BACKUP_FILE_VERSION
    });
  }
  return version;
}

export function detectBackupApplicationSchemaVersion(data, backupFileVersion) {
  if (!Object.prototype.hasOwnProperty.call(data, "applicationSchemaVersion")) {
    if (backupFileVersion >= CURRENT_BACKUP_FILE_VERSION) {
      throw new DataSchemaError("Current backup files must declare an application schema version.", {
        code: "missing_application_schema_version",
        path: "applicationSchemaVersion",
        source: "backup"
      });
    }
    return LEGACY_APPLICATION_SCHEMA_VERSION;
  }
  return detectApplicationSchemaVersion(data.applicationSchemaVersion);
}
