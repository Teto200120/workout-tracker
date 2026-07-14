import "../core/globals.js";
import { createActionCoordinator } from "../application/action-coordinator.js";
import { getWeeklyActivityData as buildWeeklyActivityData } from "../application/schedule.js";
import { cleanText, dateLabel, toast } from "../core/utils.js";
import { countRecentWorkouts, getWorkoutStreak as calculateWorkoutStreak } from "../domain/schedule.js";
import {
  firstValidationMessage,
  validateWeeklyGoal
} from "../domain/input-guardrails.js";
import {
  buildExerciseStats,
  durationLabel,
  getWorkoutStatsSummary,
  workoutDurationMinutes,
  workoutVolume
} from "../domain/workout-metrics.js";
import { getWorkouts } from "../storage/indexed-db.js";
import { getGoals, setGoals } from "../storage/local.js";

const goalsCoordinator = createActionCoordinator();

function metricLabel(metric) {
  return { estimated1rm: "Estimated 1RM", bestWeight: "Best Load", bestVolume: "Best Set Volume" }[metric] || "Metric";
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
  renderInsightList("strengthSnapshot", buildProgressInsights(workouts, exerciseStats, 3), "Strength trends will appear here once you have a few sessions logged.");
  renderInsightList("strengthSnapshotDetail", buildProgressInsights(workouts, exerciseStats, 6), "Strength trends will appear here once you have a few sessions logged.");
}

function renderTodayProgressGlance(workouts, exerciseStats) {
  const insights = buildProgressInsights(workouts, exerciseStats, 2);
  renderInsightList("todayProgressGlance", insights, "Your strength highlights will show here after more logged workouts.");
}

function renderWeeklyActivityStrip(targetId, countId, activity) {
  const target = $(targetId);
  const count = $(countId);
  if (!target || !count) return;

  target.innerHTML = activity.days.map((day) => `
      <div class="${cleanText(day.classes)}" title="${cleanText(day.title)}">
        <span class="stats-week-label">${cleanText(day.label)}</span>
        <span class="stats-week-marker" aria-hidden="true"></span>
        <span class="stats-week-status">${cleanText(day.status)}</span>
      </div>
    `).join("");

  count.textContent = `${activity.completedGymDays}/${activity.gymDays || 0}`;
}

function renderWeeklyActivityDetailList(activity) {
  const target = $("weeklyActivityDetailList");
  if (!target) return;

  target.innerHTML = activity.days.map((day) => `
    <div class="stats-week-list-row ${day.complete ? "complete" : ""}">
      <div>
        <strong>${cleanText(dateLabel(day.key))}</strong>
        <p class="muted small" style="margin:4px 0 0;">${cleanText(day.title)}</p>
      </div>
      <span class="stats-section-badge">${cleanText(day.status)}</span>
    </div>
  `).join("");
}

function renderWeeklyActivity(workouts) {
  const activity = buildWeeklyActivityData(workouts, new Date());
  renderWeeklyActivityStrip("weeklyActivityPreview", "weeklyActivityPreviewCount", activity);
  renderWeeklyActivityStrip("weeklyActivityDetail", "weeklyActivityDetailCount", activity);
  renderWeeklyActivityDetailList(activity);
}

export async function renderDashboard() {
  const workouts = (await getWorkouts()).sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  const exerciseStats = buildExerciseStats(workouts);
  const summary = getWorkoutStatsSummary(workouts);
  const streak = calculateWorkoutStreak(workouts, new Date());

  $("dashboardStats").innerHTML = `
    <div class="stat stats-stat-card stats-stat-accent"><strong>${summary.workouts}</strong><span class="muted small">Workouts</span></div>
    <div class="stat stats-stat-card"><strong>${summary.sets}</strong><span class="muted small">Total Sets</span></div>
    <div class="stat stats-stat-card"><strong>${Math.round(summary.volume).toLocaleString()}</strong><span class="muted small">Total Volume</span></div>
    <div class="stat stats-stat-card"><strong>${streak}</strong><span class="muted small">Streak Days</span></div>
  `;
  renderWeeklyActivity(workouts);
  renderStrengthSnapshot(workouts, exerciseStats);
  renderRecentSessionsPreview(workouts);
  renderWorkoutStats(workouts);
  renderGoals(workouts);
  renderPersonalRecords(exerciseStats);
  renderExerciseSelectors(exerciseStats);
  renderExerciseProgress(exerciseStats);
}

function renderStatsGrid(targetId, stats) {
  const target = $(targetId);
  if (!target) return;

  target.innerHTML = `
    <div class="stat stats-stat-card"><strong>${stats.workouts}</strong><span class="muted small">Workouts</span></div>
    <div class="stat stats-stat-card"><strong>${stats.sets}</strong><span class="muted small">Total Sets</span></div>
    <div class="stat stats-stat-card"><strong>${Math.round(stats.volume).toLocaleString()}</strong><span class="muted small">Total Volume</span></div>
    <div class="stat stats-stat-card"><strong>${durationLabel(stats.avgDuration)}</strong><span class="muted small">Avg Duration</span></div>
  `;
}

function renderWorkoutStats(workouts) {
  const stats = getWorkoutStatsSummary(workouts);
  renderStatsGrid("workoutStats", stats);
  renderStatsGrid("workoutStatsPreview", stats);
}

function renderRecentSessionsPreview(workouts) {
  const target = $("recentSessionsPreview");
  if (!target) return;

  const recent = workouts.slice(0, 3);
  if (!recent.length) {
    target.innerHTML = `<div class="stats-empty-state"><strong>No sessions yet</strong><p class="muted small" style="margin:4px 0 0;">Logged workouts will appear here.</p></div>`;
    return;
  }

  target.innerHTML = recent.map((workout) => `
    <div class="stats-preview-row">
      <div>
        <strong>${cleanText(workout.type)}</strong>
        <p class="muted small" style="margin:4px 0 0;">${dateLabel(workout.date)} - ${workout.exercises.length} exercises</p>
      </div>
      <span>${durationLabel(workoutDurationMinutes(workout))}</span>
    </div>
  `).join("");
}

function renderGoals(workouts) {
  const goals = getGoals();
  const weeklyGoalInput = $("weeklyGoal");
  if (weeklyGoalInput) weeklyGoalInput.value = goals.weeklyGoal || 4;

  const workoutsThisWeek = countRecentWorkouts(workouts, new Date());
  const weeklyGoal = Math.max(1, Number(goals.weeklyGoal || 4));
  const weeklyPercent = Math.min(100, Math.round((workoutsThisWeek / weeklyGoal) * 100));

  const preview = $("goalsPreview");
  if (preview) {
    preview.innerHTML = `
      <div class="stats-goal-row">
        <div class="stats-goal-preview-line">
          <strong>${workoutsThisWeek} / ${weeklyGoal}</strong>
          <span class="muted small">last 7 days</span>
        </div>
        <div class="progress-bar"><div class="progress-fill" style="width:${weeklyPercent}%"></div></div>
      </div>
    `;
  }

  const progress = $("goalsProgress");
  if (!progress) return;
  progress.innerHTML = `
    <div class="record-card stats-goal-row">
      <strong>Weekly workouts</strong>
      <p class="muted small" style="margin:4px 0 0;">${workoutsThisWeek} / ${weeklyGoal} workouts in the last 7 days</p>
      <div class="progress-bar"><div class="progress-fill" style="width:${weeklyPercent}%"></div></div>
    </div>
  `;
}

function renderPersonalRecordCard(record) {
  return `
    <div class="record-card">
      <div class="row" style="align-items:flex-start;">
        <div>
          <h3>${cleanText(record.name)}</h3>
          <p class="muted small" style="margin-bottom:0;">Best estimated 1RM: ${record.bestEstimated1rm.toFixed(1)} lb${record.bestDate ? ` - ${dateLabel(record.bestDate)}` : ""}</p>
        </div>
        <strong>${record.sessions}x</strong>
      </div>
    </div>
  `;
}

function renderPersonalRecordsPreview(records) {
  const target = $("personalRecordsPreview");
  if (!target) return;

  const preview = records.slice(0, 3);
  if (!preview.length) {
    target.innerHTML = `<div class="stats-empty-state"><strong>No records yet</strong><p class="muted small" style="margin:4px 0 0;">Loaded sets and reps will create records.</p></div>`;
    return;
  }

  target.innerHTML = preview.map((record) => `
    <div class="stats-preview-row">
      <div>
        <strong>${cleanText(record.name)}</strong>
        <p class="muted small" style="margin:4px 0 0;">${record.bestEstimated1rm.toFixed(1)} lb est. 1RM</p>
      </div>
      <span>${record.sessions}x</span>
    </div>
  `).join("");
}

function renderPersonalRecords(exerciseStats) {
  const allRecords = exerciseStats.filter((exercise) => exercise.bestEstimated1rm > 0).sort((a, b) => b.bestEstimated1rm - a.bestEstimated1rm);
  renderPersonalRecordsPreview(allRecords);

  const target = $("personalRecords");
  if (!target) return;
  if (!allRecords.length) {
    target.innerHTML = `<div class="stats-empty-state"><strong>No personal records yet</strong><p class="muted small" style="margin:4px 0 0;">Log loaded sets and reps first.</p></div>`;
    return;
  }
  target.innerHTML = allRecords.map(renderPersonalRecordCard).join("");
}

function renderExerciseSelectors(exerciseStats) {
  const select = $("progressExercise");
  if (!select) return;
  const current = select.value;
  const options = exerciseStats.filter((exercise) => exercise.history.length).map((exercise) => `<option value="${cleanText(exercise.name)}">${cleanText(exercise.name)}</option>`).join("");
  select.innerHTML = options || `<option value="">No exercises yet</option>`;
  if (current && Array.from(select.options).some((option) => option.value === current)) select.value = current;
}

export function renderExerciseProgress(exerciseStats) {
  const progressExercise = $("progressExercise");
  const progressMetric = $("progressMetric");
  const target = $("exerciseProgress");
  if (!progressExercise || !progressMetric || !target) return;

  const selected = progressExercise.value;
  const metric = progressMetric.value;
  const exercise = exerciseStats.find((item) => item.name === selected);
  if (!exercise || !exercise.history.length) { target.innerHTML = `<div class="stats-empty-state"><strong>No exercise trend yet</strong><p class="muted small" style="margin:4px 0 0;">Log loaded sets and reps to see progress.</p></div>`; return; }

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

  target.innerHTML = `
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

export function saveGoalsToStorage() {
  return goalsCoordinator.run(async () => {
    const input = $("weeklyGoal");
    const feedback = $("weeklyGoalError");
    const button = $("saveGoals");
    if (button) button.disabled = true;
    try {
      const validation = validateWeeklyGoal(input.value);
      if (validation.errors.length) input.setAttribute("aria-invalid", "true");
      else input.removeAttribute("aria-invalid");
      if (feedback) {
        feedback.textContent = firstValidationMessage(validation);
        feedback.classList.toggle(
          "is-warning",
          validation.valid && validation.warnings.length > 0,
        );
      }
      if (!validation.valid) {
        input.focus();
        toast(firstValidationMessage(validation));
        return false;
      }
      if (
        validation.warnings.length &&
        !confirm(`${validation.warnings[0].message}\n\nSave this goal anyway?`)
      ) {
        return false;
      }
      const weeklyGoal = Number(validation.normalized);
      setGoals({ ...getGoals(), weeklyGoal });
      toast("Goals saved.");
      await renderAll();
      return true;
    } catch (error) {
      console.info("Goal save failed.", error);
      toast("Could not save the goal. Your entered value is still available.");
      return false;
    } finally {
      if (button) button.disabled = false;
    }
  }).promise;
}

export { buildExerciseStats, renderTodayProgressGlance };
