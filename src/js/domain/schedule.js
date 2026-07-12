export function mondayFirstWeekDates(baseDate) {
  const date = new Date(baseDate);
  date.setHours(0, 0, 0, 0);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  const monday = new Date(date);
  monday.setDate(date.getDate() + diff);
  return Array.from({ length: 7 }, (_, index) => {
    const result = new Date(monday);
    result.setDate(monday.getDate() + index);
    return result;
  });
}

export function dateKeyFromDate(date) {
  return date.toISOString().slice(0, 10);
}

export function resolveWorkoutDay(date, settings, defaultSchedule, defaultSettings, dayLabels) {
  const day = new Date(`${date}T00:00:00`).getDay();
  const defaults = defaultSchedule[day] || defaultSchedule[0];
  const saved = settings.schedule?.[day] || settings.schedule?.[String(day)] || defaultSettings.schedule[day] || {};
  const kind = saved.kind || defaults.kind;
  const routine = saved.routine || defaults.routine || "Custom";
  const title = kind === "gym" ? routine : kind === "soccer" ? "Soccer Day" : "Rest Day";
  const note = kind === "gym"
    ? `Suggested from your ${dayLabels[day]} schedule.`
    : kind === "soccer"
      ? "Soccer is treated separately, since you do not track it as a gym workout here."
      : "No gym workout scheduled. Optional recovery, mobility, or custom workout.";
  return { kind, title, routine, note };
}

export function getWeeklyActivityData(workouts, currentDate, resolvePlan) {
  const dates = mondayFirstWeekDates(currentDate);
  const workoutDates = new Set(workouts.map((workout) => workout.date));
  const todayKey = dateKeyFromDate(currentDate);
  const dayLabels = ["M", "T", "W", "T", "F", "S", "S"];
  let gymDays = 0;
  let completedGymDays = 0;

  const days = dates.map((date, index) => {
    const key = dateKeyFromDate(date);
    const plan = resolvePlan(key);
    const complete = workoutDates.has(key);
    const isGymDay = plan.kind === "gym";
    if (isGymDay) {
      gymDays += 1;
      if (complete) completedGymDays += 1;
    }
    const classes = ["stats-week-day", complete ? "complete" : "", key === todayKey ? "today" : "", plan.kind === "rest" ? "rest" : "", plan.kind === "soccer" ? "soccer" : ""].filter(Boolean).join(" ");
    const status = complete ? "Logged" : plan.kind === "rest" ? "Rest" : plan.kind === "soccer" ? "Soccer" : "Open";
    return { key, label: dayLabels[index], title: plan.title, kind: plan.kind, complete, status, classes };
  });

  return { days, gymDays, completedGymDays };
}

export function getWorkoutStreak(workouts, currentDate) {
  if (!workouts.length) return 0;
  const workoutDates = new Set(workouts.map((workout) => workout.date));
  let streak = 0;
  const cursor = new Date(currentDate);
  while (true) {
    const key = cursor.toISOString().slice(0, 10);
    if (!workoutDates.has(key)) break;
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

export function countRecentWorkouts(workouts, currentDate, days = 7) {
  const cutoff = new Date(currentDate);
  cutoff.setDate(cutoff.getDate() - days);
  const cutoffKey = cutoff.toISOString().slice(0, 10);
  return workouts.filter((workout) => workout.date >= cutoffKey).length;
}
