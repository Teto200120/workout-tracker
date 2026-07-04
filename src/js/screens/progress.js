import "../core/globals.js";

function workoutVolume(workout) {
  return workout.exercises.reduce((total, exercise) => {
    return total + exercise.sets.reduce((sum, set) => {
      if (set.warmup) return sum;
      return sum + Number(set.weight || 0) * Number(set.reps || 0);
    }, 0);
  }, 0);
}

function totalSets(workout) { return workout.exercises.reduce((sum, exercise) => sum + exercise.sets.filter((set) => !set.warmup).length, 0); }

function completedSets(workout) { return workout.exercises.reduce((sum, exercise) => sum + exercise.sets.filter((set) => set.done).length, 0); }

function workoutDurationMinutes(workout) {
  if (typeof workout.durationMinutes === "number") return workout.durationMinutes;
  if (!workout.startTime || !workout.endTime) return 0;
  const [startHour, startMinute] = workout.startTime.split(":").map(Number);
  const [endHour, endMinute] = workout.endTime.split(":").map(Number);
  let start = startHour * 60 + startMinute;
  let end = endHour * 60 + endMinute;
  if (end < start) end += 24 * 60;
  return Math.max(0, end - start);
}

function durationLabel(minutes) {
  if (!minutes) return "-";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (!h) return `${m}m`;
  if (!m) return `${h}h`;
  return `${h}h ${m}m`;
}

function getBestSet(exercise) {
  let best = null;
  for (const set of exercise.sets || []) {
    if (set.warmup) continue;
    const weight = Number(set.weight || 0);
    const reps = Number(set.reps || 0);
    if (!weight || !reps) continue;
    const estimated1rm = weight * (1 + reps / 30);
    const volume = weight * reps;
    const candidate = { weight, reps, estimated1rm, volume, rpe: set.rpe || "" };
    if (!best || candidate.estimated1rm > best.estimated1rm) best = candidate;
  }
  return best;
}

function buildExerciseStats(workouts) {
  const stats = new Map();
  workouts.forEach((workout) => {
    workout.exercises.forEach((exercise) => {
      const name = exercise.name?.trim();
      if (!name) return;
      if (!stats.has(name)) {
        stats.set(name, { name, sessions: 0, sets: 0, bestWeight: 0, bestVolume: 0, bestEstimated1rm: 0, bestDate: "", history: [] });
      }
      const item = stats.get(name);
      item.sessions += 1;
      item.sets += exercise.sets.length;
      const bestSet = getBestSet(exercise);
      if (bestSet) {
        item.bestWeight = Math.max(item.bestWeight, bestSet.weight);
        item.bestVolume = Math.max(item.bestVolume, bestSet.volume);
        if (bestSet.estimated1rm > item.bestEstimated1rm) { item.bestEstimated1rm = bestSet.estimated1rm; item.bestDate = workout.date; }
        item.history.push({ date: workout.date, type: workout.type, bestWeight: bestSet.weight, bestVolume: bestSet.volume, estimated1rm: bestSet.estimated1rm, reps: bestSet.reps });
      }
    });
  });
  return Array.from(stats.values()).sort((a, b) => a.name.localeCompare(b.name));
}

function metricLabel(metric) {
  return { estimated1rm: "Estimated 1RM", bestWeight: "Best Weight", bestVolume: "Best Set Volume" }[metric] || "Metric";
}

function metricUnit(metric) {
  return metric === "bestVolume" ? "" : " lb";
}

function formatSignedNumber(value, decimals = 1) {
  const number = Number(value || 0);
  const fixed = Number.isInteger(number) ? number.toFixed(0) : number.toFixed(decimals);
  return `${number >= 0 ? "+" : ""}${fixed}`;
}

function getSortedExerciseHistory(exercise) {
  return [...(exercise.history || [])].sort((a, b) => a.date.localeCompare(b.date));
}

function getExerciseTrendInsight(exercise, metric = "bestWeight") {
  const history = getSortedExerciseHistory(exercise).slice(-7);
  if (history.length < 2) return null;

  const first = history[0];
  const last = history[history.length - 1];
  const delta = Number(last[metric] || 0) - Number(first[metric] || 0);
  const e1rmDelta = Number(last.estimated1rm || 0) - Number(first.estimated1rm || 0);
  if (delta <= 0 && e1rmDelta < 2) return null;

  const mainDelta = metric === "bestWeight" ? delta : e1rmDelta;
  return {
    type: "trend",
    score: Math.max(delta, e1rmDelta),
    title: `${exercise.name} is trending up`,
    meta: `${history.length} recent sessions · ${metric === "bestWeight" ? `${formatSignedNumber(delta, 1)} lb` : `${formatSignedNumber(e1rmDelta, 1)} est. 1RM`} since ${dateLabel(first.date)}`
  };
}

function getRecentPrInsights(exerciseStats, limit = 3) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - 60);
  const cutoffKey = cutoff.toISOString().slice(0, 10);
  const insights = [];

  exerciseStats.forEach((exercise) => {
    let runningBest = 0;
    getSortedExerciseHistory(exercise).forEach((entry, index) => {
      const value = Number(entry.estimated1rm || 0);
      const isPr = value > runningBest;
      runningBest = Math.max(runningBest, value);
      if (isPr && index > 0 && entry.date >= cutoffKey) {
        insights.push({
          type: "pr",
          date: entry.date,
          score: value,
          title: `New PR: ${exercise.name}`,
          meta: `${entry.bestWeight} × ${entry.reps} · ${value.toFixed(1)} est. 1RM · ${dateLabel(entry.date)}`
        });
      }
    });
  });

  return insights
    .sort((a, b) => b.date.localeCompare(a.date) || b.score - a.score)
    .slice(0, limit);
}

function getVolumeInsight(workouts) {
  if (workouts.length < 2) return null;
  const latest = workouts[0];
  const previousSame = workouts
    .slice(1)
    .find((workout) => workout.type === latest.type);

  if (!previousSame) return null;

  const latestVolume = workoutVolume(latest);
  const previousVolume = workoutVolume(previousSame);
  const delta = latestVolume - previousVolume;
  if (Math.abs(delta) < 250) return null;

  return {
    type: "volume",
    score: Math.abs(delta),
    title: delta > 0 ? `${latest.type} volume increased` : `${latest.type} volume dipped`,
    meta: `${formatSignedNumber(Math.round(delta), 0)} lb vs ${dateLabel(previousSame.date)}`
  };
}

function buildProgressInsights(workouts, exerciseStats, limit = 3) {
  const insights = [];

  insights.push(...getRecentPrInsights(exerciseStats, 2));

  const trends = exerciseStats
    .map((exercise) => getExerciseTrendInsight(exercise, "bestWeight"))
    .filter(Boolean)
    .sort((a, b) => b.score - a.score)
    .slice(0, 2);
  insights.push(...trends);

  const volumeInsight = getVolumeInsight(workouts);
  if (volumeInsight) insights.push(volumeInsight);

  const unique = [];
  const seen = new Set();
  insights.forEach((insight) => {
    const key = `${insight.type}-${insight.title}`;
    if (!seen.has(key)) {
      seen.add(key);
      unique.push(insight);
    }
  });

  return unique.slice(0, limit);
}

function renderInsightList(targetId, insights, emptyText) {
  const target = $(targetId);
  if (!target) return;
  if (!insights.length) {
    target.innerHTML = `<div class="insight-item stats-empty-state"><strong>Keep logging sessions</strong><p class="muted small" style="margin:0;">${cleanText(emptyText)}</p></div>`;
    return;
  }

  target.innerHTML = insights.map((insight) => `
    <div class="insight-item ${cleanText(insight.type || "")}">
      <strong>${cleanText(insight.title)}</strong>
      <p class="muted small" style="margin:0;">${cleanText(insight.meta)}</p>
    </div>
  `).join("");
}

function renderStrengthSnapshot(workouts, exerciseStats) {
  const insights = buildProgressInsights(workouts, exerciseStats, 4);
  renderInsightList("strengthSnapshot", insights, "Strength trends will appear here once you have a few sessions logged.");
}

function renderTodayProgressGlance(workouts, exerciseStats) {
  const insights = buildProgressInsights(workouts, exerciseStats, 2);
  renderInsightList("todayProgressGlance", insights, "Your strength highlights will show here after more logged workouts.");
}

function fallbackWeekDates(baseDate = new Date()) {
  const date = new Date(baseDate);
  date.setHours(0, 0, 0, 0);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  const monday = new Date(date);
  monday.setDate(date.getDate() + diff);
  return Array.from({ length: 7 }, (_, index) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + index);
    return d;
  });
}

function renderWeeklyActivity(workouts) {
  const target = $("weeklyActivity");
  const count = $("weeklyActivityCount");
  if (!target || !count) return;

  const dates = typeof mondayFirstWeekDates === "function" ? mondayFirstWeekDates(new Date()) : fallbackWeekDates(new Date());
  const keyFromDate = typeof dateKeyFromDate === "function" ? dateKeyFromDate : (date) => date.toISOString().slice(0, 10);
  const workoutDates = new Set(workouts.map((workout) => workout.date));
  const todayKey = today();
  const dayLabels = ["M", "T", "W", "T", "F", "S", "S"];
  let gymDays = 0;
  let completedGymDays = 0;

  target.innerHTML = dates.map((date, index) => {
    const key = keyFromDate(date);
    const plan = typeof getTodayPlan === "function" ? getTodayPlan(key) : { kind: "gym", title: "Workout" };
    const complete = workoutDates.has(key);
    const isGymDay = plan.kind === "gym";
    if (isGymDay) {
      gymDays += 1;
      if (complete) completedGymDays += 1;
    }
    const classes = ["stats-week-day", complete ? "complete" : "", key === todayKey ? "today" : "", plan.kind === "rest" ? "rest" : "", plan.kind === "soccer" ? "soccer" : ""].filter(Boolean).join(" ");
    const status = complete ? "Logged" : plan.kind === "rest" ? "Rest" : plan.kind === "soccer" ? "Soccer" : "Open";
    return `
      <div class="${classes}" title="${cleanText(plan.title)}">
        <span class="stats-week-label">${dayLabels[index]}</span>
        <span class="stats-week-marker" aria-hidden="true"></span>
        <span class="stats-week-status">${cleanText(status)}</span>
      </div>
    `;
  }).join("");

  count.textContent = `${completedGymDays}/${gymDays || 0}`;
}

async function renderDashboard() {
  const workouts = (await getItems("workouts")).sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  const weights = (await getItems("weights")).sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  const exerciseStats = buildExerciseStats(workouts);
  const lastWorkout = workouts[0];
  const lastWeight = weights[0];
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const sevenDayKey = sevenDaysAgo.toISOString().slice(0, 10);
  const workoutsThisWeek = workouts.filter((workout) => workout.date >= sevenDayKey).length;
  const streak = getWorkoutStreak(workouts);

  $("dashboardStats").innerHTML = `
    <div class="stat stats-stat-card stats-stat-accent"><strong>${workoutsThisWeek}</strong><span class="muted small">Last 7 Days</span></div>
    <div class="stat stats-stat-card"><strong>${lastWorkout ? dateLabel(lastWorkout.date) : "-"}</strong><span class="muted small">Last Workout</span></div>
    <div class="stat stats-stat-card"><strong>${lastWeight ? `${lastWeight.weight.toFixed(1)} lb` : "-"}</strong><span class="muted small">Latest Weight</span></div>
    <div class="stat stats-stat-card"><strong>${streak}</strong><span class="muted small">Streak Days</span></div>
  `;
  renderWeeklyActivity(workouts);
  renderStrengthSnapshot(workouts, exerciseStats);
  renderGoals(workouts, weights);
  renderPersonalRecords(exerciseStats);
  renderExerciseSelectors(exerciseStats);
  renderExerciseProgress(exerciseStats);
}

function getWorkoutStreak(workouts) {
  if (!workouts.length) return 0;
  const workoutDates = new Set(workouts.map((workout) => workout.date));
  let streak = 0;
  const cursor = new Date();

  while (true) {
    const key = cursor.toISOString().slice(0, 10);
    if (!workoutDates.has(key)) break;
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }

  return streak;
}

function renderGoals(workouts, weights) {
  const goals = getGoals();
  $("weeklyGoal").value = goals.weeklyGoal || 4;
  $("targetWeight").value = goals.targetWeight || "";

  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const sevenDayKey = sevenDaysAgo.toISOString().slice(0, 10);
  const workoutsThisWeek = workouts.filter((workout) => workout.date >= sevenDayKey).length;
  const weeklyGoal = Number(goals.weeklyGoal || 4);
  const weeklyPercent = Math.min(100, Math.round((workoutsThisWeek / weeklyGoal) * 100));

  const latestWeight = weights[0]?.weight;
  const targetWeight = Number(goals.targetWeight || 0);
  let weightText = "Set a target weight to track bulk progress.";
  let weightPercent = 0;

  if (latestWeight && targetWeight) {
    const oldestWeight = weights[weights.length - 1]?.weight || latestWeight;
    const totalNeeded = targetWeight - oldestWeight;
    const currentChange = latestWeight - oldestWeight;
    weightPercent = totalNeeded === 0 ? 100 : Math.max(0, Math.min(100, Math.round((currentChange / totalNeeded) * 100)));
    const remaining = targetWeight - latestWeight;
    weightText = `${remaining >= 0 ? remaining.toFixed(1) + " lb left" : Math.abs(remaining).toFixed(1) + " lb over target"} · Target: ${targetWeight.toFixed(1)} lb`;
  }

  $("goalsProgress").innerHTML = `
    <div class="record-card stats-goal-row">
      <strong>Weekly workouts</strong>
      <p class="muted small" style="margin:4px 0 0;">${workoutsThisWeek} / ${weeklyGoal} workouts in the last 7 days</p>
      <div class="progress-bar"><div class="progress-fill" style="width:${weeklyPercent}%"></div></div>
    </div>
    <div class="record-card stats-goal-row">
      <strong>Body weight goal</strong>
      <p class="muted small" style="margin:4px 0 0;">${weightText}</p>
      <div class="progress-bar"><div class="progress-fill" style="width:${weightPercent}%"></div></div>
    </div>
  `;
}

function renderPersonalRecords(exerciseStats) {
  const records = exerciseStats.filter((exercise) => exercise.bestEstimated1rm > 0).sort((a, b) => b.bestEstimated1rm - a.bestEstimated1rm).slice(0, 8);
  if (!records.length) { $("personalRecords").innerHTML = `<div class="stats-empty-state"><strong>No personal records yet</strong><p class="muted small" style="margin:4px 0 0;">Log sets with weight and reps first.</p></div>`; return; }
  $("personalRecords").innerHTML = records.map((record) => `
    <div class="record-card"><div class="row" style="align-items:flex-start;"><div><h3>${cleanText(record.name)}</h3><p class="muted small" style="margin-bottom:0;">Best estimated 1RM: ${record.bestEstimated1rm.toFixed(1)} lb${record.bestDate ? ` · ${dateLabel(record.bestDate)}` : ""}</p></div><strong>${record.sessions}x</strong></div></div>
  `).join("");
}

function renderExerciseSelectors(exerciseStats) {
  const select = $("progressExercise");
  const current = select.value;
  const options = exerciseStats.filter((exercise) => exercise.history.length).map((exercise) => `<option value="${cleanText(exercise.name)}">${cleanText(exercise.name)}</option>`).join("");
  select.innerHTML = options || `<option value="">No exercises yet</option>`;
  if (current && Array.from(select.options).some((option) => option.value === current)) select.value = current;
}

function renderExerciseProgress(exerciseStats) {
  const selected = $("progressExercise").value;
  const metric = $("progressMetric").value;
  const exercise = exerciseStats.find((item) => item.name === selected);
  if (!exercise || !exercise.history.length) { $("exerciseProgress").innerHTML = `<div class="stats-empty-state"><strong>No exercise trend yet</strong><p class="muted small" style="margin:4px 0 0;">Log an exercise with weight and reps to see progress.</p></div>`; return; }

  const sorted = getSortedExerciseHistory(exercise);
  const recent = sorted.slice(-7);
  const label = metricLabel(metric);
  const unit = metricUnit(metric);
  const max = Math.max(...recent.map((entry) => Number(entry[metric] || 0)), 1);
  const first = recent[0];
  const last = recent[recent.length - 1];
  const delta = recent.length > 1 ? Number(last[metric] || 0) - Number(first[metric] || 0) : 0;
  const trendText = recent.length > 1
    ? `${formatSignedNumber(delta, metric === "bestVolume" ? 0 : 1)}${unit} over last ${recent.length} sessions`
    : "Need one more session to show a trend.";

  let runningBest = 0;
  const rows = recent.map((entry) => {
    const value = Number(entry[metric] || 0);
    const isPr = value > runningBest;
    runningBest = Math.max(runningBest, value);
    const width = Math.max(4, Math.round((value / max) * 100));
    return `<div class="record-card"><div class="row" style="align-items:flex-start;"><div><strong>${dateLabel(entry.date)} ${isPr ? `<span class="pill">PR</span>` : ""}</strong><p class="muted small" style="margin:4px 0 0;">${cleanText(entry.type)} · ${label}: ${value.toFixed(metric === "bestVolume" ? 0 : 1)}${unit} · ${entry.bestWeight} × ${entry.reps}</p><div class="progress-bar"><div class="progress-fill" style="width:${width}%"></div></div></div></div></div>`;
  }).join("");

  $("exerciseProgress").innerHTML = `
    <div class="detail">
      <div class="trend-row">
        <div>
          <strong>Last ${recent.length} sessions</strong>
          <p class="muted small" style="margin:4px 0 0;">${cleanText(label)} trend for ${cleanText(exercise.name)}.</p>
        </div>
        <span class="trend-value">${cleanText(trendText)}</span>
      </div>
    </div>
    ${rows}
  `;
}

Object.assign(globalThis, { workoutVolume, totalSets, completedSets, workoutDurationMinutes, durationLabel, getBestSet, buildExerciseStats, metricLabel, metricUnit, formatSignedNumber, getSortedExerciseHistory, getExerciseTrendInsight, getRecentPrInsights, getVolumeInsight, buildProgressInsights, renderInsightList, renderStrengthSnapshot, renderTodayProgressGlance, fallbackWeekDates, renderWeeklyActivity, renderDashboard, getWorkoutStreak, renderGoals, renderPersonalRecords, renderExerciseSelectors, renderExerciseProgress });
