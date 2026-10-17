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
  return runMaintenanceOperation(() => performRestoreBackup(data));
}

async function performRestoreBackup(data) {
  const prepared = prepareValidatedBackup(data);
  const originalRecords = await getAllApplicationRecords();
  const localSnapshot = captureApplicationLocalStorage();
  let indexedDbCommitted = false;
  try {
    await importBackupRecords({
      workouts: prepared.data.workouts,
      legacyWeights: prepared.data.legacyWeights,
      routines: prepared.data.routines
    });
    indexedDbCommitted = true;
    await seedDefaultTemplates();
    if (prepared.presence.goals) setGoals(prepared.data.goals);
    if (prepared.presence.settings) setAppSettings(prepared.data.settings);
    else setAppSettings({ ...getAppSettings(), displayName: null });
    if (prepared.presence.backupMeta) setBackupMeta(prepared.data.backupMeta);
    setApplicationSchemaVersionMarker(CURRENT_APPLICATION_SCHEMA_VERSION);
  } catch (cause) {
    const rollbackErrors = [];
    if (indexedDbCommitted) {
      try {
        await replaceApplicationRecords(originalRecords, { validate: false });
      } catch (error) {
        rollbackErrors.push(error);
      }
    }
    try {
      restoreApplicationLocalStorage(localSnapshot);
    } catch (error) {
      rollbackErrors.push(error);
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
  return runMaintenanceOperation(() => performClearApplicationData());
}

async function performClearApplicationData() {
  const prepared = await prepareDefaultTemplates();
  const originalRecords = await getAllApplicationRecords();
  const localSnapshot = captureApplicationLocalStorage();
  const educationSnapshot = getRawEducationRecord();
  let committed = false;
  try {
    await replaceApplicationRecords({ workouts: [], legacyWeights: [], routines: prepared.routines });
    committed = true;
    clearApplicationLocalStorage();
    setApplicationSchemaVersionMarker(CURRENT_APPLICATION_SCHEMA_VERSION);
    forgetEducationState();
  } catch (cause) {
    const rollbackErrors = [];
    if (committed) {
      try { await replaceApplicationRecords(originalRecords, { validate: false }); }
      catch (error) { rollbackErrors.push(error); }
    }
    try { restoreApplicationLocalStorage(localSnapshot); }
    catch (error) { rollbackErrors.push(error); }
    try { restoreRawEducationRecord(educationSnapshot); }
    catch (error) { rollbackErrors.push(error); }
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
