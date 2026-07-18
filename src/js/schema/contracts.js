export const SCHEDULE_DAYS = Object.freeze(["0", "1", "2", "3", "4", "5", "6"]);
export const SCHEDULE_KINDS = Object.freeze(["gym", "rest", "soccer"]);
export const SET_SCALAR_FIELDS = Object.freeze(["weight", "reps", "rpe"]);
export const SET_BOOLEAN_FIELDS = Object.freeze(["done", "warmup"]);
export const SETTINGS_NUMBER_FIELDS = Object.freeze([
  "defaultWeightJump",
  "compoundMin",
  "compoundMax",
  "pullMin",
  "pullMax",
  "isolationMin",
  "isolationMax",
  "generalMin",
  "generalMax"
]);
export const SETTINGS_BOOLEAN_FIELDS = Object.freeze(["rpeAware", "haptics", "animations"]);
export const APPLICATION_DATA_COLLECTIONS = Object.freeze(["workouts", "legacyWeights", "routines"]);
export const APPLICATION_LOCAL_DATA_FIELDS = Object.freeze(["settings", "goals", "draft", "backupMeta"]);

export const CANONICAL_CONTRACTS = Object.freeze({
  set: {
    required: ["weight", "reps", "rpe", "done", "warmup"],
    emptyAllowed: ["weight", "reps", "rpe"],
    notes: "Numeric input values are persisted as strings; zero is valid. Unknown safe fields are retained."
  },
  exercise: {
    required: ["name", "notes", "sets"],
    emptyAllowed: ["name", "notes"],
    notes: "Exercise order and set order are significant. No catalog identifier is defined in schema 1."
  },
  workout: {
    required: ["id", "date", "type", "startTime", "endTime", "notes", "tags", "exercises", "createdAt"],
    optional: ["durationMinutes"],
    notes: "IDs and dates are preserved. Duration is stored only when supplied and is never recomputed by migration."
  },
  routine: {
    required: ["id", "name", "exercises", "createdAt", "updatedAt"],
    notes: "The exercises array contains ordered exercise-name strings."
  },
  settings: {
    required: ["displayName", "schedule", ...SETTINGS_NUMBER_FIELDS, ...SETTINGS_BOOLEAN_FIELDS],
    notes: "displayName is a validated string after onboarding or null while onboarding is required. Schedule entries are required for Sunday through Saturday and contain kind and routine."
  },
  goals: {
    required: ["weeklyGoal"],
    optional: ["targetWeight"],
    notes: "targetWeight is retained only as a legacy compatibility field."
  },
  draft: {
    required: ["id", "date", "type", "startTime", "endTime", "notes", "tags", "exercises", "createdAt", "editingWorkoutId", "activeExerciseIndex", "savedAt"],
    optional: ["durationMinutes"],
    notes: "Elapsed state is represented by startTime, endTime, and optional durationMinutes."
  },
  backupMeta: {
    optional: ["lastExportedAt"],
    notes: "Unknown safe metadata fields are retained."
  },
  legacyWeight: {
    required: ["id", "date", "weight", "notes", "createdAt"],
    notes: "Retained only for backward-compatible storage and backups."
  }
});
