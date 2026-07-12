import "../core/globals.js";
import {
  backupAgeText,
  buildBackup,
  clearApplicationData,
  daysSinceBackup,
  restoreBackup,
  validateBackupStructure
} from "../application/backup.js";
import { refreshTemplateDropdowns } from "../components/routine-selectors.js";
import { applyAppSettings } from "../core/settings.js";
import { today, toast } from "../core/utils.js";
import { getLegacyWeights, getWorkouts, isDatabaseOpen } from "../storage/indexed-db.js";
import { getBackupMeta, setBackupMeta } from "../storage/local.js";
import { loadWorkoutTemplate, stopSessionElapsedTimer } from "./active-workout.js";

export async function renderBackupStatus() {
  if (!isDatabaseOpen()) return;

  const [workouts, legacyWeights] = await Promise.all([getWorkouts(), getLegacyWeights()]);
  // Legacy weight records still count for backup reminders so old data can be exported.
  const hasUserData = workouts.length > 0 || legacyWeights.length > 0;
  const meta = getBackupMeta();
  const age = daysSinceBackup(meta.lastExportedAt);

  let level;
  let pill;
  let text;
  let showToday = false;

  if (!hasUserData) {
    level = "good";
    pill = "No data";
    text = "No workout data yet. Backup reminders will appear once there is progress to protect.";
  } else if (age === null) {
    level = "warn";
    pill = "Backup";
    text = "No backup exported yet. Export once so your progress has a safe local copy.";
    showToday = true;
  } else if (age < 14) {
    level = "good";
    pill = "Current";
    text = `${backupAgeText(age)}. Your local backup is recent.`;
  } else if (age < 30) {
    level = "warn";
    pill = "Backup soon";
    text = `${backupAgeText(age)}. Export when convenient so your local progress stays protected.`;
    showToday = true;
  } else {
    level = "urgent";
    pill = "Backup now";
    text = `${backupAgeText(age)}. Export a fresh backup before adding much more data.`;
    showToday = true;
  }

  const todayCard = $("todayBackupReminder");
  const todayText = $("todayBackupText");
  const todayPill = $("todayBackupPill");
  if (todayCard && todayText && todayPill) {
    todayCard.classList.toggle("hidden", !showToday);
    todayCard.classList.toggle("urgent", level === "urgent");
    todayText.textContent = text;
    todayPill.textContent = pill;
    todayPill.className = `backup-status-pill ${level === "good" ? "good" : level === "urgent" ? "urgent" : ""}`;
  }

  const more = $("backupStatusMore");
  if (more) {
    more.textContent = text;
  }

  const backupCard = $("backupRestoreTool");
  if (backupCard) {
    backupCard.classList.remove("backup-status-good", "backup-status-warn", "backup-status-urgent");
    backupCard.classList.add(`backup-status-${level}`);
  }

  const backupPill = $("backupStatusPill");
  if (backupPill) {
    backupPill.textContent = pill;
    backupPill.className = `backup-page-status-pill ${level}`;
  }
}

export async function exportData() {
  const exportedAt = new Date().toISOString();
  const data = await buildBackup(exportedAt);
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `workout-tracker-backup-${today()}.json`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
  setBackupMeta({ ...getBackupMeta(), lastExportedAt: exportedAt });
  await renderBackupStatus();
  toast("Backup exported.");
}

export async function importData(file) {
  try {
    const data = JSON.parse(await file.text());
    validateBackupStructure(data);
    if (!confirm("Import this backup? Existing entries stay. Matching IDs will be overwritten.")) return;
    await restoreBackup(data);
    applyAppSettings();
    await refreshTemplateDropdowns();
    await renderAll();
    toast("Backup imported.");
  } catch {
    toast("Could not import backup.");
  }
}

export async function clearAllData() {
  if (!confirm("Clear all local app data from this browser?")) return;
  await clearApplicationData();
  stopSessionElapsedTimer();
  stopTodayActiveElapsedTimer();
  applyAppSettings();
  await refreshTemplateDropdowns();
  await loadWorkoutTemplate();
  await renderAll();
  toast("All local data cleared.");
}

