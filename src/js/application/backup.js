import { DB_NAME } from "../core/constants.js";
import { DataSchemaError } from "../schema/errors.js";
import { prepareBackupImport } from "../schema/migrations.js";
import { normalizeApplicationData } from "../schema/normalize.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION
} from "../schema/versions.js";
import { assertValidApplicationData } from "../schema/validators.js";
import {
  clearApplicationStores,
  getAllApplicationRecords,
  getLegacyWeights,
  getRoutines,
  getWorkouts,
  importBackupRecords,
  replaceApplicationRecords,
  seedDefaultTemplates
} from "../storage/indexed-db.js";
import {
  captureApplicationLocalStorage,
  clearApplicationLocalStorage,
  getAppSettings,
  getBackupMeta,
  getGoals,
  restoreApplicationLocalStorage,
  setAppSettings,
  setApplicationSchemaVersionMarker,
  setBackupMeta,
  setGoals
} from "../storage/local.js";

export function validateBackupStructure(data) {
  const prepared = prepareBackupImport(data);
  return {
    workouts: prepared.data.workouts,
    legacyWeights: prepared.data.legacyWeights,
    routines: prepared.data.routines
  };
}

export async function buildBackup(exportedAt) {
  const applicationData = normalizeApplicationData({
    workouts: await getWorkouts(),
    legacyWeights: await getLegacyWeights(),
    routines: await getRoutines(),
    goals: getGoals(),
    settings: getAppSettings(),
    draft: null,
    backupMeta: { ...getBackupMeta(), lastExportedAt: exportedAt }
  });
  assertValidApplicationData(applicationData, { source: "backup" });
  return {
    app: "Hector's Workout Tracker",
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

export async function restoreBackup(data) {
  const prepared = prepareBackupImport(data);
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

export async function clearApplicationData() {
  await clearApplicationStores();
  clearApplicationLocalStorage();
  await seedDefaultTemplates();
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
