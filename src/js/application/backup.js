import { beginRecoverySnapshot, finishRecoverySnapshot } from "./recovery.js";
import { runMaintenanceOperation } from "./maintenance.js";
import { DB_NAME } from "../core/constants.js";
import { forgetEducationState } from "./education.js";
import { prepareDefaultTemplates, seedDefaultTemplates } from "./routine-seeding.js";
import {
  validateBackupComplexity,
  validateImportedApplicationData
} from "../domain/input-guardrails.js";
import { DataSchemaError, createValidationError } from "../schema/errors.js";
import { prepareBackupImport } from "../schema/migrations.js";
import { normalizeApplicationData } from "../schema/normalize.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION
} from "../schema/versions.js";
import { assertValidApplicationData } from "../schema/validators.js";
import {
  getAllApplicationRecords,
  importBackupRecords,
  replaceApplicationRecords
} from "../storage/indexed-db.js";
import {
  captureApplicationLocalStorage,
  clearApplicationLocalStorage,
  getAppSettings,
  getBackupMeta,
  getGoals,
  getRawEducationRecord,
  restoreRawEducationRecord,
  restoreApplicationLocalStorage,
  setAppSettings,
  setApplicationSchemaVersionMarker,
  setBackupMeta,
  setGoals
} from "../storage/local.js";

function prepareValidatedBackup(data) {
  const complexity = validateBackupComplexity(data);
  if (!complexity.valid) {
    throw createValidationError(complexity.errors, {
      code: "backup_guardrail_failed",
      source: "backup"
    });
  }
  const prepared = prepareBackupImport(data);
  const guardrails = validateImportedApplicationData(prepared.data);
  if (!guardrails.valid) {
    throw createValidationError(guardrails.errors, {
      code: "backup_guardrail_failed",
      source: "backup"
    });
  }
  return prepared;
}

export function validateBackupStructure(data) {
  const prepared = prepareValidatedBackup(data);
  return {
    workouts: prepared.data.workouts,
    legacyWeights: prepared.data.legacyWeights,
    routines: prepared.data.routines
  };
}

export function buildBackup(exportedAt) {
  return runMaintenanceOperation(() => performBuildBackup(exportedAt));
}

async function performBuildBackup(exportedAt) {
  const applicationData = normalizeApplicationData({
    ...await getAllApplicationRecords(),
    goals: getGoals(),
    settings: getAppSettings(),
    draft: null,
    backupMeta: { ...getBackupMeta(), lastExportedAt: exportedAt }
  });
  assertValidApplicationData(applicationData, { source: "backup" });
  return {
    app: "Workout Tracker",
    backupFileVersion: CURRENT_BACKUP_FILE_VERSION,
    applicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
    database: DB_NAME,
    exportedAt,
    workouts: applicationData.workouts,
    weights: applicationData.legacyWeights,
    templates: applicationData.routines,
    goals: applicationData.goals,
    settings: applicationData.settings,
    backupMeta: applicationData.backupMeta
  };
}

export function restoreBackup(data) {
  return runMaintenanceOperation((operation) => performRestoreBackup(data, operation));
}

async function performRestoreBackup(data, operation) {
  const prepared = prepareValidatedBackup(data);
  const originalRecords = await getAllApplicationRecords();
  const localSnapshot = captureApplicationLocalStorage();
  let indexedDbCommitted = false;
  await beginRecoverySnapshot({ records: originalRecords, localSnapshot, education: getRawEducationRecord(), kind: "import" }, operation);
  try {
    await importBackupRecords({
      workouts: prepared.data.workouts,
      legacyWeights: prepared.data.legacyWeights,
      routines: prepared.data.routines
    }, { operation });
    indexedDbCommitted = true;
    await seedDefaultTemplates({ operation });
    if (prepared.presence.goals) await setGoals(prepared.data.goals, { operation });
    if (prepared.presence.settings) await setAppSettings(prepared.data.settings, { operation });
    else await setAppSettings({ ...getAppSettings(), displayName: null }, { operation });
    if (prepared.presence.backupMeta) await setBackupMeta(prepared.data.backupMeta, { operation });
    await setApplicationSchemaVersionMarker(CURRENT_APPLICATION_SCHEMA_VERSION, { operation });
    await finishRecoverySnapshot(operation);
  } catch (cause) {
    const rollbackErrors = [];
    if (indexedDbCommitted) {
      try {
        await replaceApplicationRecords(originalRecords, { validate: false, operation });
      } catch (error) {
        rollbackErrors.push(error);
      }
    }
    try {
      await restoreApplicationLocalStorage(localSnapshot, { operation });
    } catch (error) {
      rollbackErrors.push(error);
    }
    if (!rollbackErrors.length) {
      try { await finishRecoverySnapshot(operation); }
      catch (error) { rollbackErrors.push(error); }
    }
    throw new DataSchemaError("Backup restoration failed.", {
      cause,
      code: "backup_restore_failed",
      source: "backup",
      rollbackErrors
    });
  }
}

export function clearApplicationData() {
  return runMaintenanceOperation((operation) => performClearApplicationData(operation));
}

async function performClearApplicationData(operation) {
  const prepared = await prepareDefaultTemplates();
  const originalRecords = await getAllApplicationRecords();
  const localSnapshot = captureApplicationLocalStorage();
  const educationSnapshot = getRawEducationRecord();
  let committed = false;
  await beginRecoverySnapshot({ records: originalRecords, localSnapshot, education: educationSnapshot, kind: "clear" }, operation);
  try {
    await replaceApplicationRecords({ workouts: [], legacyWeights: [], routines: prepared.routines }, { operation });
    committed = true;
    await clearApplicationLocalStorage({ operation });
    await setApplicationSchemaVersionMarker(CURRENT_APPLICATION_SCHEMA_VERSION, { operation });
    forgetEducationState();
    await finishRecoverySnapshot(operation);
  } catch (cause) {
    const rollbackErrors = [];
    if (committed) {
      try { await replaceApplicationRecords(originalRecords, { validate: false, operation }); }
      catch (error) { rollbackErrors.push(error); }
    }
    try { await restoreApplicationLocalStorage(localSnapshot, { operation }); }
    catch (error) { rollbackErrors.push(error); }
    try { await restoreRawEducationRecord(educationSnapshot, { operation }); }
    catch (error) { rollbackErrors.push(error); }
    if (!rollbackErrors.length) {
      try { await finishRecoverySnapshot(operation); }
      catch (error) { rollbackErrors.push(error); }
    }
    throw new DataSchemaError("Local data clear failed.", {
      cause, code: "data_clear_failed", rollbackErrors,
    });
  }
}

export function daysSinceBackup(lastExportedAt, now = new Date()) {
  if (!lastExportedAt) return null;
  const then = new Date(lastExportedAt);
  if (Number.isNaN(then.getTime())) return null;
  return Math.max(0, Math.floor((now - then) / 86400000));
}

export function backupAgeText(age) {
  if (age === null) return "No backup exported yet";
  if (age === 0) return "Last backup: today";
  if (age === 1) return "Last backup: yesterday";
  return `Last backup: ${age} days ago`;
}
