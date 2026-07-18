import {
  nameComparisonKey,
  validateExerciseName,
} from "./input-guardrails.js";

export function hasRoutineExerciseDuplicate(
  exercises = [],
  name,
  { excludeIndex = -1 } = {},
) {
  const key = nameComparisonKey(name);
  return exercises.some(
    (exercise, index) =>
      index !== excludeIndex && nameComparisonKey(exercise) === key,
  );
}

export function applyRoutineExerciseSelection(
  exercises = [],
  { mode = "add", index = -1, name } = {},
) {
  const validation = validateExerciseName(name);
  if (!validation.valid) {
    throw new TypeError(validation.errors[0]?.message || "Invalid exercise name.");
  }
  const next = [...exercises];
  if (mode === "add") {
    next.push(validation.normalized);
    return next;
  }
  if (mode !== "replace") {
    throw new TypeError(`Unsupported routine selection mode: ${mode}`);
  }
  if (!Number.isInteger(index) || index < 0 || index >= next.length) {
    throw new RangeError("Routine exercise index is out of range.");
  }
  next[index] = validation.normalized;
  return next;
}
