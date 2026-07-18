import { DEFAULT_APP_SETTINGS } from "../core/constants.js";
import { SCHEDULE_DAYS, SETTINGS_BOOLEAN_FIELDS, SETTINGS_NUMBER_FIELDS } from "./contracts.js";

export function clonePersistedValue(value, seen = new WeakMap()) {
  if (value === null || typeof value !== "object") return value;
  if (value instanceof Date) return new Date(value.getTime());
  if (seen.has(value)) return seen.get(value);
  if (Array.isArray(value)) {
    const clone = [];
    seen.set(value, clone);
    value.forEach((item) => clone.push(clonePersistedValue(item, seen)));
    return clone;
  }
  const clone = {};
  seen.set(value, clone);
  Object.keys(value).forEach((key) => {
    clone[key] = clonePersistedValue(value[key], seen);
  });
  return clone;
}

function normalizeNumericText(value) {
  if (value === null || value === undefined || value === "") return "";
  return String(value).trim();
}

function normalizeBoolean(value, fallback = false) {
  if (value === null || value === undefined) return fallback;
  if (value === false || value === 0 || value === "0" || value === "false") return false;
  if (value === true || value === 1 || value === "1" || value === "true") return true;
  return fallback;
}

function normalizeText(value, options = {}) {
  const text = value === null || value === undefined ? "" : String(value);
  return options.trim ? text.trim() : text;
}

export function normalizeSet(value = {}) {
  return {
    ...clonePersistedValue(value),
    weight: normalizeNumericText(value.weight),
    reps: normalizeNumericText(value.reps),
    rpe: normalizeNumericText(value.rpe),
    done: normalizeBoolean(value.done),
    warmup: normalizeBoolean(value.warmup)
  };
}

export function normalizeExercise(value = {}) {
  return {
    ...clonePersistedValue(value),
    name: normalizeText(value.name, { trim: true }),
    notes: normalizeText(value.notes),
    sets: Array.isArray(value.sets) ? value.sets.map(normalizeSet) : []
  };
}

export function normalizeWorkout(value = {}) {
  const normalized = {
    ...clonePersistedValue(value),
    id: value.id,
    date: value.date,
    type: normalizeText(value.type),
    startTime: normalizeText(value.startTime),
    endTime: normalizeText(value.endTime),
    notes: normalizeText(value.notes),
    tags: Array.isArray(value.tags) ? value.tags.map((tag) => String(tag)) : [],
    exercises: Array.isArray(value.exercises) ? value.exercises.map(normalizeExercise) : [],
    createdAt: normalizeText(value.createdAt)
  };
  if (value.durationMinutes === null || value.durationMinutes === undefined || value.durationMinutes === "") {
    delete normalized.durationMinutes;
  } else {
    normalized.durationMinutes = Number(value.durationMinutes);
  }
  return normalized;
}

export function normalizeRoutine(value = {}) {
  return {
    ...clonePersistedValue(value),
    id: value.id,
    name: normalizeText(value.name, { trim: true }),
    exercises: Array.isArray(value.exercises) ? value.exercises.map((exercise) => normalizeText(exercise, { trim: true })) : [],
    createdAt: normalizeText(value.createdAt),
    updatedAt: normalizeText(value.updatedAt)
  };
}

export function normalizeSettings(value = {}) {
  const defaults = clonePersistedValue(DEFAULT_APP_SETTINGS);
  const source = clonePersistedValue(value);
  const schedule = {};
  SCHEDULE_DAYS.forEach((day) => {
    const defaultEntry = defaults.schedule[day];
    const sourceEntry = source.schedule?.[day] || {};
    schedule[day] = {
      ...clonePersistedValue(defaultEntry),
      ...clonePersistedValue(sourceEntry),
      kind: sourceEntry.kind || defaultEntry.kind,
      routine: normalizeText(sourceEntry.routine ?? defaultEntry.routine, { trim: true })
    };
  });
  const normalized = { ...defaults, ...source, schedule };
  normalized.displayName = source.displayName === null || source.displayName === undefined
    ? null
    : typeof source.displayName === "string"
      ? normalizeText(source.displayName, { trim: true })
      : source.displayName;
  SETTINGS_NUMBER_FIELDS.forEach((field) => {
    const sourceValue = source[field];
    normalized[field] = sourceValue === null || sourceValue === undefined || sourceValue === "" ? defaults[field] : Number(sourceValue);
  });
  SETTINGS_BOOLEAN_FIELDS.forEach((field) => {
    normalized[field] = normalizeBoolean(source[field], defaults[field]);
  });
  return normalized;
}

export function normalizeGoals(value = {}) {
  const normalized = { ...clonePersistedValue(value) };
  normalized.weeklyGoal = value.weeklyGoal === null || value.weeklyGoal === undefined || value.weeklyGoal === "" ? 4 : Number(value.weeklyGoal);
  return normalized;
}

export function normalizeDraft(value = {}) {
  return {
    ...normalizeWorkout(value),
    editingWorkoutId: value.editingWorkoutId ?? null,
    activeExerciseIndex: value.activeExerciseIndex === null || value.activeExerciseIndex === undefined || value.activeExerciseIndex === "" ? 0 : Number(value.activeExerciseIndex),
    savedAt: normalizeText(value.savedAt)
  };
}

export function normalizeBackupMeta(value = {}) {
  return clonePersistedValue(value);
}

export function normalizeLegacyWeight(value = {}) {
  return {
    ...clonePersistedValue(value),
    id: value.id,
    date: value.date,
    weight: Number(value.weight),
    notes: normalizeText(value.notes),
    createdAt: normalizeText(value.createdAt)
  };
}

export function normalizeApplicationData(value) {
  return {
    workouts: value.workouts.map(normalizeWorkout),
    legacyWeights: value.legacyWeights.map(normalizeLegacyWeight),
    routines: value.routines.map(normalizeRoutine),
    settings: value.settings === null ? null : normalizeSettings(value.settings),
    goals: value.goals === null ? null : normalizeGoals(value.goals),
    draft: value.draft === null ? null : normalizeDraft(value.draft),
    backupMeta: value.backupMeta === null ? null : normalizeBackupMeta(value.backupMeta)
  };
}
