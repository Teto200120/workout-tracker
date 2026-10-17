export function estimatedOneRepMax(weight, reps) {
  return Number(weight || 0) * (1 + Number(reps || 0) / 30);
}

export function setVolume(set = {}) {
  if (set.warmup) return 0;
  return Number(set.weight || 0) * Number(set.reps || 0);
}

export function workoutVolume(workout = {}) {
  return (workout.exercises || []).reduce((total, exercise) => {
    return total + (exercise.sets || []).reduce((sum, set) => sum + setVolume(set), 0);
  }, 0);
}

export function totalSets(workout = {}) {
  return (workout.exercises || []).reduce((sum, exercise) => {
    return sum + (exercise.sets || []).filter((set) => !set.warmup).length;
  }, 0);
}

export function completedSets(workout = {}) {
  return (workout.exercises || []).reduce((sum, exercise) => {
    return sum + (exercise.sets || []).filter((set) => set.done).length;
  }, 0);
}

export function workoutDurationMinutes(workout = {}) {
  if (typeof workout.durationMinutes === "number") return workout.durationMinutes;
  if (!workout.startTime || !workout.endTime) return 0;
  const [startHour, startMinute] = workout.startTime.split(":").map(Number);
  const [endHour, endMinute] = workout.endTime.split(":").map(Number);
  const start = startHour * 60 + startMinute;
  let end = endHour * 60 + endMinute;
  if (end < start) end += 24 * 60;
  return Math.max(0, end - start);
}

export function durationLabel(minutes) {
  if (!minutes) return "-";
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (!hours) return `${remainingMinutes}m`;
  if (!remainingMinutes) return `${hours}h`;
  return `${hours}h ${remainingMinutes}m`;
}

export function getExerciseRecords(exercise = {}) {
  const records = { bestWeight: null, bestVolume: null, estimated1rm: null };
  for (const set of exercise.sets || []) {
    if (set.warmup) continue;
    const weight = Number(set.weight || 0);
    const reps = Number(set.reps || 0);
    if (!Number.isFinite(weight) || !Number.isFinite(reps) || weight <= 0 || reps <= 0) continue;
    const candidate = { weight, reps, estimated1rm: estimatedOneRepMax(weight, reps), volume: weight * reps, rpe: set.rpe || "" };
    for (const [metric, field] of [["bestWeight", "weight"], ["bestVolume", "volume"], ["estimated1rm", "estimated1rm"]]) {
      // Keep the first source set on a tie, independently for each metric.
      if (!records[metric] || candidate[field] > records[metric][field]) records[metric] = candidate;
    }
  }
  return records;
}

export function getBestSet(exercise = {}) {
  return getExerciseRecords(exercise).estimated1rm;
}

export function buildExerciseStats(workouts = []) {
  const stats = new Map();
  workouts.forEach((workout) => {
    (workout.exercises || []).forEach((exercise) => {
      const name = exercise.name?.trim();
      if (!name) return;
      if (!stats.has(name)) {
        stats.set(name, { name, sessions: 0, sets: 0, bestWeight: 0, bestVolume: 0, bestEstimated1rm: 0, bestDate: "", records: {}, history: [] });
      }
      const item = stats.get(name);
      item.sessions += 1;
      item.sets += (exercise.sets || []).length;
      const records = getExerciseRecords(exercise);
      const bestSet = records.estimated1rm;
      if (bestSet) {
        for (const [metric, field] of [["bestWeight", "weight"], ["bestVolume", "volume"], ["estimated1rm", "estimated1rm"]]) {
          if (!item.records[metric] || records[metric][field] > item.records[metric][field]) {
            item.records[metric] = { ...records[metric], date: workout.date };
          }
        }
        item.bestWeight = item.records.bestWeight.weight;
        item.bestVolume = item.records.bestVolume.volume;
        if (bestSet.estimated1rm > item.bestEstimated1rm) {
          item.bestEstimated1rm = bestSet.estimated1rm;
          item.bestDate = workout.date;
        }
        item.history.push({ date: workout.date, type: workout.type, bestWeight: records.bestWeight.weight, bestVolume: records.bestVolume.volume, estimated1rm: bestSet.estimated1rm, reps: bestSet.reps, records });
      }
    });
  });
  return Array.from(stats.values()).sort((a, b) => a.name.localeCompare(b.name));
}

export function getWorkoutStatsSummary(workouts = []) {
  const volume = workouts.reduce((sum, workout) => sum + workoutVolume(workout), 0);
  const sets = workouts.reduce((sum, workout) => sum + totalSets(workout), 0);
  const durations = workouts.map(workoutDurationMinutes).filter(Boolean);
  const avgDuration = durations.length ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length) : 0;
  return { workouts: workouts.length, sets, volume, avgDuration };
}

export function bestHistoricalSetForExercise(workouts, exerciseName) {
  const name = String(exerciseName || "").trim().toLowerCase();
  let best = null;
  for (const workout of workouts || []) {
    for (const exercise of workout.exercises || []) {
      if ((exercise.name || "").trim().toLowerCase() !== name) continue;
      const bestSet = getBestSet(exercise);
      if (bestSet && (!best || bestSet.estimated1rm > best.estimated1rm)) best = bestSet;
    }
  }
  return best;
}

export function buildCompletionSummary(workout, previousSameWorkout, previousWorkouts = []) {
  const volume = Math.round(workoutVolume(workout));
  const sets = totalSets(workout);
  const duration = workoutDurationMinutes(workout);
  const highlights = [];

  for (const exercise of workout.exercises || []) {
    const bestSet = getBestSet(exercise);
    if (!bestSet) continue;
    const previousBest = bestHistoricalSetForExercise(previousWorkouts, exercise.name || "");
    if (!previousBest || bestSet.estimated1rm > previousBest.estimated1rm) {
      highlights.push({
        type: "PR",
        title: `New PR: ${exercise.name}`,
        text: `${bestSet.weight} × ${bestSet.reps}${previousBest ? ` · beat ${previousBest.weight} × ${previousBest.reps}` : ""}`
      });
    }
  }

  if (previousSameWorkout) {
    const previousVolume = Math.round(workoutVolume(previousSameWorkout));
    const difference = volume - previousVolume;
    if (difference > 0) {
      highlights.push({ type: "Volume", title: "Volume increased", text: `+${difference.toLocaleString()} lb vs last ${workout.type}` });
    }
    const previousSets = totalSets(previousSameWorkout);
    if (sets > previousSets) {
      highlights.push({ type: "Work", title: "More work completed", text: `${sets} work sets today vs ${previousSets} last time` });
    }
  }

  if (!highlights.length) {
    highlights.push({ type: "Saved", title: "Session logged", text: "Workout completed and saved. Progress still counts even without a PR." });
  }

  return { volume, sets, duration, highlights: highlights.slice(0, 4) };
}

export function hasSaveableWorkoutContent(workout = {}) {
  return Array.isArray(workout.exercises) && workout.exercises.length > 0;
}
