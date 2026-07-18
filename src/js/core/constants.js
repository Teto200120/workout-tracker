export const DB_NAME = "hector_workout_tracker_fresh_v1";
export const DB_VERSION = 2;
export const STORES = ["workouts", "weights", "templates"];

export const DEFAULT_TEMPLATES = {
  "Chest / Triceps": ["Flat Bench Press", "Incline Press", "Low-to-High Cable Fly", "Tricep Pushdown"],
  "Back / Biceps": ["V-Bar Lat Pulldown", "V-Bar Cable Row", "Incline Hammer Curl", "Cable Bicep Curl"],
  Legs: ["Squat", "Romanian Deadlift", "Leg Press", "Calf Raise"],
  "Shoulders / Traps": ["Shoulder Press", "Lateral Raise", "Rear Delt Fly", "Shrugs"],
  "Soccer / Conditioning": ["Warm-up", "Sprint Work", "Agility Work", "Cooldown"],
  Custom: []
};

export const DEFAULT_SCHEDULE = {
  0: { kind: "rest", title: "Rest Day", routine: "Custom", note: "No gym workout scheduled. Optional recovery, mobility, or custom workout." },
  1: { kind: "gym", title: "Push Day", routine: "Chest / Triceps", note: "Suggested from your Monday schedule." },
  2: { kind: "gym", title: "Pull Day", routine: "Back / Biceps", note: "Suggested from your Tuesday schedule." },
  3: { kind: "soccer", title: "Soccer Day", routine: "Custom", note: "Soccer is treated separately, since you do not track it as a gym workout here." },
  4: { kind: "rest", title: "Rest Day", routine: "Custom", note: "No gym workout scheduled. Optional recovery, mobility, or custom workout." },
  5: { kind: "gym", title: "Leg Day", routine: "Legs", note: "Suggested from your Friday schedule." },
  6: { kind: "gym", title: "Upper / Full Body", routine: "Shoulders / Traps", note: "Suggested from your Saturday schedule." }
};

export const SETTINGS_KEY = "hector_workout_settings_v1";
export const GOALS_KEY = "hector_workout_goals_v1";
export const DRAFT_KEY = "hector_workout_draft_v1";
export const BACKUP_META_KEY = "hector_workout_backup_meta_v1";
export const APPLICATION_SCHEMA_VERSION_KEY = "hector_workout_data_schema_version";
export const DAY_LABELS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export const DEFAULT_APP_SETTINGS = {
  displayName: null,
  schedule: {
    0: { kind: "rest", routine: "Custom" },
    1: { kind: "gym", routine: "Chest / Triceps" },
    2: { kind: "gym", routine: "Back / Biceps" },
    3: { kind: "soccer", routine: "Custom" },
    4: { kind: "rest", routine: "Custom" },
    5: { kind: "gym", routine: "Legs" },
    6: { kind: "gym", routine: "Shoulders / Traps" }
  },
  defaultWeightJump: 5,
  compoundMin: 6,
  compoundMax: 10,
  pullMin: 8,
  pullMax: 12,
  isolationMin: 12,
  isolationMax: 20,
  generalMin: 8,
  generalMax: 12,
  rpeAware: true,
  haptics: true,
  animations: true
};
