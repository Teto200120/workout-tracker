export function normalizeExerciseName(value) {
  return typeof value === "string" ? value.trim() : "";
}

export function exerciseNameKey(value) {
  return normalizeExerciseName(value).toLowerCase();
}

export function dedupeExerciseNames(names = []) {
  const seen = new Set();
  const options = [];

  for (const value of names) {
    const name = normalizeExerciseName(value);
    const key = exerciseNameKey(name);
    if (!name || seen.has(key)) continue;
    seen.add(key);
    options.push(name);
  }

  return options;
}

function routineExerciseNames(routines = []) {
  return routines.flatMap((routine) =>
    (routine?.exercises || []).map((exercise) =>
      typeof exercise === "string" ? exercise : exercise?.name,
    ),
  );
}

function workoutExerciseNames(workouts = []) {
  return workouts.flatMap((workout) =>
    (workout?.exercises || []).map((exercise) => exercise?.name),
  );
}

export function buildExerciseOptions({
  defaultRoutines = [],
  routines = [],
  workouts = [],
  currentExercises = [],
} = {}) {
  return dedupeExerciseNames([
    ...routineExerciseNames(defaultRoutines),
    ...routineExerciseNames(routines),
    ...workoutExerciseNames(workouts),
    ...currentExercises,
  ]).sort((left, right) => left.localeCompare(right));
}

export function searchExerciseOptions(options = [], query = "") {
  const search = exerciseNameKey(query);
  if (!search) return [...options];
  return options.filter((option) => exerciseNameKey(option).includes(search));
}

export function resolveExerciseName(value, options = []) {
  const name = normalizeExerciseName(value);
  if (!name) return "";
  const key = exerciseNameKey(name);
  return options.find((option) => exerciseNameKey(option) === key) || name;
}
