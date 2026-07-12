import { DataSchemaError } from "../schema/errors.js";
import { migrateApplicationData } from "../schema/migrations.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  detectApplicationSchemaVersion
} from "../schema/versions.js";
import {
  getAllApplicationRecords,
  replaceApplicationRecords
} from "../storage/indexed-db.js";
import {
  captureApplicationLocalStorage,
  getApplicationSchemaVersionMarker,
  readPersistedApplicationLocalData,
  restoreApplicationLocalStorage,
  setApplicationSchemaVersionMarker,
  writePersistedApplicationLocalData
} from "../storage/local.js";

function applicationRecords(data) {
  return {
    workouts: data.workouts,
    legacyWeights: data.legacyWeights,
    routines: data.routines
  };
}

export async function coordinateApplicationSchemaMigration(options) {
  const migratedData = migrateApplicationData(options.sourceData, options.sourceVersion, {
    source: "application"
  });
  if (options.sourceVersion === CURRENT_APPLICATION_SCHEMA_VERSION) {
    return {
      migrated: false,
      fromVersion: options.sourceVersion,
      toVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
      data: migratedData
    };
  }

  let indexedDbCommitted = false;
  try {
    await options.replaceRecords(applicationRecords(migratedData));
    indexedDbCommitted = true;
    options.writeLocalData(migratedData);
    options.writeMarker(CURRENT_APPLICATION_SCHEMA_VERSION);
  } catch (cause) {
    const rollbackErrors = [];
    if (indexedDbCommitted) {
      try {
        await options.replaceRecords(applicationRecords(options.sourceData), { validate: false });
      } catch (error) {
        rollbackErrors.push(error);
      }
    }
    try {
      options.restoreLocalSnapshot(options.localSnapshot);
    } catch (error) {
      rollbackErrors.push(error);
    }
    throw new DataSchemaError("Application data migration could not be persisted.", {
      cause,
      code: "migration_persistence_failed",
      source: "application",
      fromVersion: options.sourceVersion,
      toVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
      rollbackErrors
    });
  }

  return {
    migrated: true,
    fromVersion: options.sourceVersion,
    toVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
    data: migratedData
  };
}

export async function ensureCurrentApplicationSchema() {
  const sourceVersion = detectApplicationSchemaVersion(getApplicationSchemaVersionMarker());
  const localSnapshot = captureApplicationLocalStorage();
  const [records, localData] = await Promise.all([
    getAllApplicationRecords(),
    Promise.resolve().then(() => readPersistedApplicationLocalData(localSnapshot))
  ]);
  const sourceData = { ...records, ...localData };
  return coordinateApplicationSchemaMigration({
    sourceVersion,
    sourceData,
    localSnapshot,
    replaceRecords: replaceApplicationRecords,
    writeLocalData: writePersistedApplicationLocalData,
    writeMarker: setApplicationSchemaVersionMarker,
    restoreLocalSnapshot: restoreApplicationLocalStorage
  });
}
