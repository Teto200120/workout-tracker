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

  const [workouts, weights] = await Promise.all([getItems("workouts"), getItems("weights")]);
  const hasUserData = workouts.length > 0 || weights.length > 0;
  const meta = getBackupMeta();
  const age = daysSinceBackup(meta.lastExportedAt);

  let level = "good";
  let pill = "Current";
  let text = "No workout or body-weight data yet.";
  let showToday = false;

  if (!hasUserData) {
    level = "good";
    pill = "No data";
    text = "No workout or body-weight data yet. Backup reminders will appear once there is progress to protect.";
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
    more.innerHTML = `<strong>${cleanText(pill)}</strong><br>${cleanText(text)}`;
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
    if (!Array.isArray(data.workouts) || !Array.isArray(data.weights)) throw new Error("Invalid backup file.");
    if (!confirm("Import this backup? Existing entries stay. Matching IDs will be overwritten.")) return;
    for (const workout of data.workouts) await saveItem("workouts", workout);
    for (const weight of data.weights) await saveItem("weights", weight);
    if (Array.isArray(data.templates)) for (const template of data.templates) await saveItem("templates", template);
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
  if (!confirm("Clear all workout, weight, and template data from this browser?")) return;
  for (const name of STORES) await clearStore(name);
  localStorage.removeItem(BACKUP_META_KEY);
  await seedDefaultTemplates();
  await refreshTemplateDropdowns();
  await loadWorkoutTemplate();
  await renderAll();
  toast("All local data cleared.");
}

Object.assign(globalThis, { getBackupMeta, setBackupMeta, daysSinceBackup, backupAgeText, renderBackupStatus, exportData, importData, clearAllData });
