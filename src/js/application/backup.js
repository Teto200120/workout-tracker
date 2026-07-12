import { DB_NAME } from "../core/constants.js";
import {
  clearApplicationStores,
  getLegacyWeights,
  getRoutines,
  getWorkouts,
  importBackupRecords,
  seedDefaultTemplates
} from "../storage/indexed-db.js";
import {
  clearApplicationLocalStorage,
  cloneDefaultSettings,
  getAppSettings,
  getBackupMeta,
  getGoals,
  setAppSettings,
  setBackupMeta,
  setGoals
} from "../storage/local.js";

function hasValidIds(items) {
  return Array.isArray(items) && items.every((item) => item && typeof item === "object" && Object.prototype.hasOwnProperty.call(item, "id"));
}

export function validateBackupStructure(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new Error("Invalid backup file.");
  }

  const workouts = data.workouts;
  const legacyWeights = data.weights ?? [];
  const routines = data.templates ?? [];
  if (!hasValidIds(workouts) || !hasValidIds(legacyWeights) || !hasValidIds(routines)) {
    throw new Error("Invalid backup file.");
  }

  return { workouts, legacyWeights, routines };
}

export async function buildBackup(exportedAt) {
  return {
    app: "Hector's Workout Tracker",
    version: 2,
    database: DB_NAME,
    exportedAt,
    workouts: await getWorkouts(),
    weights: await getLegacyWeights(),
    templates: await getRoutines(),
    goals: getGoals(),
    settings: getAppSettings(),
    backupMeta: { ...getBackupMeta(), lastExportedAt: exportedAt }
  };
}

export async function restoreBackup(data) {
  const { workouts, legacyWeights, routines } = validateBackupStructure(data);

  await importBackupRecords({ workouts, legacyWeights, routines });
  if (data.goals) setGoals(data.goals);
  if (data.settings) {
    setAppSettings({
      ...cloneDefaultSettings(),
      ...data.settings,
      schedule: { ...cloneDefaultSettings().schedule, ...(data.settings.schedule || {}) }
    });
  }
  if (data.backupMeta) setBackupMeta(data.backupMeta);
  await seedDefaultTemplates();
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
