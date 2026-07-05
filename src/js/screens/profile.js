import "../core/globals.js";

function setProfileText(id, value) {
  const target = $(id);
  if (target) target.textContent = value;
}

function sortByDateDesc(items) {
  return [...items].sort((a, b) =>
    String(b.date || "").localeCompare(String(a.date || "")) ||
    String(b.createdAt || "").localeCompare(String(a.createdAt || ""))
  );
}

function countRecentWorkouts(workouts) {
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const sevenDayKey = sevenDaysAgo.toISOString().slice(0, 10);
  return workouts.filter((workout) => workout.date >= sevenDayKey).length;
}

function getProfileBackupSummary() {
  if (typeof getBackupMeta !== "function" || typeof daysSinceBackup !== "function" || typeof backupAgeText !== "function") {
    return "Export or import local data";
  }

  const age = daysSinceBackup(getBackupMeta().lastExportedAt);
  return backupAgeText(age);
}

async function renderProfile() {
  if (!$("profile") || !db) return;

  const [workoutsRaw, templates] = await Promise.all([
    getItems("workouts"),
    getTemplates()
  ]);
  const workouts = sortByDateDesc(workoutsRaw);
  const goals = getGoals();
  const storedWeeklyGoal = Number(goals.weeklyGoal || 4);
  const weeklyGoal = Number.isFinite(storedWeeklyGoal) && storedWeeklyGoal > 0 ? storedWeeklyGoal : 4;
  const workoutsThisWeek = countRecentWorkouts(workouts);
  const weeklyPercent = Math.min(100, Math.round((workoutsThisWeek / weeklyGoal) * 100));
  const workoutLabel = workouts.length === 1 ? "session" : "sessions";
  const routineLabel = templates.length === 1 ? "saved routine" : "saved routines";

  setProfileText("profileWeeklyGoal", `${weeklyGoal} workouts/week`);
  setProfileText("profileGoalSummary", `${workoutsThisWeek} logged in the last 7 days.`);
  setProfileText("profileSessionCount", `${workouts.length}`);
  setProfileText("profileSessionSummary", `${workoutLabel} on this device.`);
  setProfileText("profileGoalMeterLabel", workoutsThisWeek >= weeklyGoal ? "Weekly goal complete" : "Weekly goal progress");
  setProfileText("profileGoalMeterCount", `${workoutsThisWeek}/${weeklyGoal}`);
  setProfileText("profileRoutineSummary", `${templates.length} ${routineLabel}`);
  setProfileText("profileBackupSummary", getProfileBackupSummary());
  setProfileText("profileDataSummary", `${workouts.length} ${workoutLabel} on this device`);

  const fill = $("profileGoalMeterFill");
  if (fill) fill.style.width = `${weeklyPercent}%`;
}

Object.assign(globalThis, { renderProfile });
