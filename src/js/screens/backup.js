import "../core/globals.js";

function getBackupMeta() {
  try {
    return JSON.parse(localStorage.getItem(BACKUP_META_KEY)) || {};
  } catch {
    return {};
  }
}

function setBackupMeta(meta) {
  localStorage.setItem(BACKUP_META_KEY, JSON.stringify(meta || {}));
}

function daysSinceBackup(lastExportedAt) {
  if (!lastExportedAt) return null;
  const then = new Date(lastExportedAt);
  if (Number.isNaN(then.getTime())) return null;
  const now = new Date();
  return Math.max(0, Math.floor((now - then) / 86400000));
}

function backupAgeText(age) {
  if (age === null) return "No backup exported yet";
  if (age === 0) return "Last backup: today";
  if (age === 1) return "Last backup: yesterday";
  return `Last backup: ${age} days ago`;
}

async function renderBackupStatus() {
  if (!db) return;

  const [workouts, legacyWeights] = await Promise.all([getItems("workouts"), getItems("weights")]);
  // Legacy weight records still count for backup reminders so old data can be exported.
  const hasUserData = workouts.length > 0 || legacyWeights.length > 0;
  const meta = getBackupMeta();
  const age = daysSinceBackup(meta.lastExportedAt);

  let level = "good";
  let pill = "Current";
  let text = "No workout data yet.";
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

async function exportData() {
  const exportedAt = new Date().toISOString();
  const data = {
    app: "Hector's Workout Tracker",
    version: 2,
    database: DB_NAME,
    exportedAt,
    workouts: await getItems("workouts"),
    weights: await getItems("weights"),
    templates: await getItems("templates"),
    goals: getGoals(),
    settings: getAppSettings(),
    backupMeta: { ...getBackupMeta(), lastExportedAt: exportedAt }
  };
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

async function importData(file) {
  try {
    const data = JSON.parse(await file.text());
    const legacyWeights = data.weights ?? [];
    const templates = data.templates ?? [];
    const hasValidIds = (items) => Array.isArray(items) && items.every((item) =>
      item && typeof item === "object" && Object.prototype.hasOwnProperty.call(item, "id")
    );
    if (!hasValidIds(data.workouts) || !hasValidIds(legacyWeights) || !hasValidIds(templates)) {
      throw new Error("Invalid backup file.");
    }
    if (!confirm("Import this backup? Existing entries stay. Matching IDs will be overwritten.")) return;

    await new Promise((resolve, reject) => {
      const transaction = db.transaction(STORES, "readwrite");
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error || new Error("Backup import was aborted."));
      data.workouts.forEach((workout) => transaction.objectStore("workouts").put(workout));
      legacyWeights.forEach((weight) => transaction.objectStore("weights").put(weight));
      templates.forEach((template) => transaction.objectStore("templates").put(template));
    });

    if (data.goals) localStorage.setItem("hector_workout_goals_v1", JSON.stringify(data.goals));
    if (data.settings) {
      setAppSettings({
        ...cloneDefaultSettings(),
        ...data.settings,
        schedule: { ...cloneDefaultSettings().schedule, ...(data.settings.schedule || {}) }
      });
    }
    if (data.backupMeta) setBackupMeta(data.backupMeta);
    await seedDefaultTemplates();
    await refreshTemplateDropdowns();
    await renderAll();
    toast("Backup imported.");
  } catch (error) {
    toast("Could not import backup.");
  }
}

async function clearAllData() {
  if (!confirm("Clear all local app data from this browser?")) return;
  for (const name of STORES) await clearStore(name);
  localStorage.removeItem(BACKUP_META_KEY);
  localStorage.removeItem("hector_workout_goals_v1");
  localStorage.removeItem("hector_workout_draft_v1");
  localStorage.removeItem(SETTINGS_KEY);
  stopSessionElapsedTimer();
  stopTodayActiveElapsedTimer();
  applyAppSettings();
  await seedDefaultTemplates();
  await refreshTemplateDropdowns();
  await loadWorkoutTemplate();
  await renderAll();
  toast("All local data cleared.");
}

Object.assign(globalThis, { getBackupMeta, setBackupMeta, daysSinceBackup, backupAgeText, renderBackupStatus, exportData, importData, clearAllData });
