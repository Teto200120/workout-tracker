import { beginRecoverySnapshot, finishRecoverySnapshot } from "./recovery.js";
import { runMaintenanceOperation } from "./maintenance.js";
import { DataSchemaError } from "../schema/errors.js";
import { migrateApplicationData } from "../schema/migrations.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  detectApplicationSchemaVersion,
} from "../schema/versions.js";
import {
  getAllApplicationRecords,
  replaceApplicationRecords,
} from "../storage/indexed-db.js";
import {
  captureApplicationLocalStorage,
  getApplicationSchemaVersionMarker,
  getRawEducationRecord,
  readPersistedApplicationLocalData,
  restoreApplicationLocalStorage,
  setApplicationSchemaVersionMarker,
  writePersistedApplicationLocalData,
} from "../storage/local.js";

function applicationRecords(data) {
  return {
    workouts: data.workouts,
    legacyWeights: data.legacyWeights,
    routines: data.routines,
  };
}

export async function coordinateApplicationSchemaMigration(options) {
  const migratedData = migrateApplicationData(
    options.sourceData,
    options.sourceVersion,
    {
      source: "application",
    },
  );
  if (options.sourceVersion === CURRENT_APPLICATION_SCHEMA_VERSION) {
    return {
      migrated: false,
      fromVersion: options.sourceVersion,
      toVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
      data: migratedData,
    };
  }

  let indexedDbCommitted = false;
  await options.prepareRecovery?.();
  try {
    await options.replaceRecords(applicationRecords(migratedData));
    indexedDbCommitted = true;
    await options.writeLocalData(migratedData);
    await options.writeMarker(CURRENT_APPLICATION_SCHEMA_VERSION);
    await options.finishRecovery?.();
  } catch (cause) {
    const rollbackErrors = [];
    if (indexedDbCommitted) {
      try {
        await options.replaceRecords(applicationRecords(options.sourceData), {
          validate: false,
        });
      } catch (error) {
        rollbackErrors.push(error);
      }
    }
    try {
      await options.restoreLocalSnapshot(options.localSnapshot);
    } catch (error) {
      rollbackErrors.push(error);
    }
    if (!rollbackErrors.length) {
      try {
        await options.finishRecovery?.();
      } catch (error) {
        rollbackErrors.push(error);
      }
    }
    throw new DataSchemaError(
      "Application data migration could not be persisted.",
      {
        cause,
        code: "migration_persistence_failed",
        source: "application",
        fromVersion: options.sourceVersion,
        toVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
        rollbackErrors,
      },
    );
  }

  return {
    migrated: true,
    fromVersion: options.sourceVersion,
    toVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
    data: migratedData,
  };
}

export function ensureCurrentApplicationSchema() {
  return runMaintenanceOperation((operation) =>
    performEnsureCurrentApplicationSchema(operation),
  );
}

async function performEnsureCurrentApplicationSchema(operation) {
  const sourceVersion = detectApplicationSchemaVersion(
    getApplicationSchemaVersionMarker(),
  );
  const localSnapshot = captureApplicationLocalStorage();
  const [records, localData] = await Promise.all([
    getAllApplicationRecords(),
    Promise.resolve().then(() =>
      readPersistedApplicationLocalData(localSnapshot),
    ),
  ]);
  const sourceData = { ...records, ...localData };
  return coordinateApplicationSchemaMigration({
    sourceVersion,
    sourceData,
    localSnapshot,
    prepareRecovery: () =>
      beginRecoverySnapshot(
        {
          records,
          localSnapshot,
          education: getRawEducationRecord(),
          kind: "migration",
        },
        operation,
      ),
    finishRecovery: () => finishRecoverySnapshot(operation),
    replaceRecords: (records, options) =>
      replaceApplicationRecords(records, { ...options, operation }),
    writeLocalData: (data) =>
      writePersistedApplicationLocalData(data, { operation }),
    writeMarker: (version) =>
      setApplicationSchemaVersionMarker(version, { operation }),
    restoreLocalSnapshot: (snapshot) =>
      restoreApplicationLocalStorage(snapshot, { operation }),
  });
}
