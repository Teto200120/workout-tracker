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

function formatProfileWeight(weight) {
  const value = Number(weight);
  return Number.isFinite(value) ? `${value.toFixed(1)} lb` : "-";
}

function getProfileWeightSummary(latestWeight, goals) {
  const targetWeight = Number(goals.targetWeight || 0);
  if (!latestWeight && !targetWeight) return "Log body weight to track the trend.";
  if (!latestWeight && targetWeight) return `Target: ${targetWeight.toFixed(1)} lb. Add a weigh-in to start tracking.`;

  const latest = Number(latestWeight.weight || 0);
  const date = latestWeight.date ? dateLabel(latestWeight.date) : "latest weigh-in";
  if (!targetWeight) return `Last logged ${date}. Set a target in Stats.`;

  const remaining = targetWeight - latest;
  if (remaining >= 0) return `${remaining.toFixed(1)} lb to target. Last logged ${date}.`;
  return `${Math.abs(remaining).toFixed(1)} lb over target. Last logged ${date}.`;
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

  const [workoutsRaw, weightsRaw, templates] = await Promise.all([
    getItems("workouts"),
    getItems("weights"),
    getTemplates()
  ]);
  const workouts = sortByDateDesc(workoutsRaw);
  const weights = sortByDateDesc(weightsRaw);
  const goals = getGoals();
  const latestWeight = weights[0];
  const storedWeeklyGoal = Number(goals.weeklyGoal || 4);
  const weeklyGoal = Number.isFinite(storedWeeklyGoal) && storedWeeklyGoal > 0 ? storedWeeklyGoal : 4;
  const workoutsThisWeek = countRecentWorkouts(workouts);
  const weeklyPercent = Math.min(100, Math.round((workoutsThisWeek / weeklyGoal) * 100));
  const workoutLabel = workouts.length === 1 ? "session" : "sessions";
  const routineLabel = templates.length === 1 ? "saved routine" : "saved routines";

  setProfileText("profileCurrentWeight", latestWeight ? formatProfileWeight(latestWeight.weight) : "-");
  setProfileText("profileWeightSummary", getProfileWeightSummary(latestWeight, goals));
  setProfileText("profileWeeklyGoal", `${weeklyGoal} workouts/week`);
  setProfileText("profileGoalSummary", `${workoutsThisWeek} logged in the last 7 days.`);
  setProfileText("profileGoalMeterLabel", workoutsThisWeek >= weeklyGoal ? "Weekly goal complete" : "Weekly goal progress");
  setProfileText("profileGoalMeterCount", `${workoutsThisWeek}/${weeklyGoal}`);
  setProfileText("profileRoutineSummary", `${templates.length} ${routineLabel}`);
  setProfileText("profileBackupSummary", getProfileBackupSummary());
  setProfileText("profileDataSummary", `${workouts.length} ${workoutLabel} on this device`);

  const fill = $("profileGoalMeterFill");
  if (fill) fill.style.width = `${weeklyPercent}%`;
}

Object.assign(globalThis, { renderProfile });
