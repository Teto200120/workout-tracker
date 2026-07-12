import { DEFAULT_APP_SETTINGS } from "../../src/js/core/constants.js";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION,
} from "../../src/js/schema/versions.js";

export function cloneFixture(value) {
  return structuredClone(value);
}

export function canonicalSet(overrides = {}) {
  return {
    weight: "135",
    reps: "8",
    rpe: "8",
    done: true,
    warmup: false,
    ...overrides,
  };
}

export function canonicalExercise(overrides = {}) {
  return {
    name: "Flat Bench Press",
    notes: "Controlled tempo",
    sets: [canonicalSet()],
    ...overrides,
  };
}

export function canonicalWorkout(overrides = {}) {
  return {
    id: "workout-schema-fixture",
    date: "2026-06-15",
    type: "Chest / Triceps",
    startTime: "18:00",
    endTime: "18:45",
    durationMinutes: 45,
    notes: "Schema fixture",
    tags: ["Good session"],
    exercises: [canonicalExercise()],
    createdAt: "2026-06-15T18:00:00.000Z",
    ...overrides,
  };
}

export function canonicalRoutine(overrides = {}) {
  return {
    id: "routine-schema-fixture",
    name: "Chest / Triceps",
    exercises: ["Flat Bench Press", "Tricep Pushdown"],
    createdAt: "2026-06-01T10:00:00.000Z",
    updatedAt: "2026-06-02T10:00:00.000Z",
    ...overrides,
  };
}

export function canonicalSettings(overrides = {}) {
  return {
    ...cloneFixture(DEFAULT_APP_SETTINGS),
    ...overrides,
    schedule: {
      ...cloneFixture(DEFAULT_APP_SETTINGS.schedule),
      ...(overrides.schedule || {}),
    },
  };
}

export function canonicalGoals(overrides = {}) {
  return { weeklyGoal: 4, ...overrides };
}

export function canonicalDraft(overrides = {}) {
  return {
    ...canonicalWorkout({
      id: "draft-schema-fixture",
      endTime: "",
      durationMinutes: 0,
      notes: "Recover this draft",
    }),
    editingWorkoutId: null,
    activeExerciseIndex: 0,
    savedAt: "2026-06-15T18:05:00.000Z",
    ...overrides,
  };
}

export function canonicalBackupMeta(overrides = {}) {
  return { lastExportedAt: "2026-06-16T12:00:00.000Z", ...overrides };
}

export function canonicalLegacyWeight(overrides = {}) {
  return {
    id: "legacy-weight-fixture",
    date: "2026-05-01",
    weight: 175.5,
    notes: "Compatibility record",
    createdAt: "2026-05-01T12:00:00.000Z",
    ...overrides,
  };
}

export function canonicalApplicationData(overrides = {}) {
  return {
    workouts: [canonicalWorkout()],
    legacyWeights: [canonicalLegacyWeight()],
    routines: [canonicalRoutine()],
    settings: canonicalSettings(),
    goals: canonicalGoals(),
    draft: canonicalDraft(),
    backupMeta: canonicalBackupMeta(),
    ...overrides,
  };
}

export function legacyApplicationData(overrides = {}) {
  return {
    workouts: [
      {
        id: "legacy-workout",
        date: "2026-06-10",
        type: "Legacy Routine",
        exercises: [
          {
            name: "  Legacy Press  ",
            sets: [{ weight: 0, reps: "0", rpe: null, done: 1 }],
            futureExerciseField: { source: "local" },
          },
        ],
        legacyWorkoutField: "preserve",
      },
    ],
    legacyWeights: [
      {
        id: "legacy-weight",
        date: "2026-05-01",
        weight: "175.5",
      },
    ],
    routines: [
      {
        id: "legacy-routine",
        name: "  Legacy Routine  ",
        exercises: ["  Legacy Press  "],
      },
    ],
    settings: {
      schedule: { 1: { kind: "gym", routine: "Legacy Routine" } },
      animations: "false",
      haptics: 1,
    },
    goals: { weeklyGoal: "3", targetWeight: "170", legacyGoalField: true },
    draft: {
      id: "legacy-draft",
      date: "2026-06-11",
      type: "Legacy Routine",
      startTime: "18:00",
      exercises: [],
    },
    backupMeta: {},
    ...overrides,
  };
}

export function legacyBackup(overrides = {}) {
  const data = legacyApplicationData();
  return {
    app: "Hector's Workout Tracker",
    version: 2,
    database: "hector_workout_tracker_fresh_v1",
    exportedAt: "2026-06-16T12:00:00.000Z",
    workouts: data.workouts,
    weights: data.legacyWeights,
    templates: data.routines,
    settings: data.settings,
    goals: data.goals,
    backupMeta: data.backupMeta,
    ...overrides,
  };
}

export function currentBackup(overrides = {}) {
  const data = canonicalApplicationData();
  return {
    app: "Hector's Workout Tracker",
    backupFileVersion: CURRENT_BACKUP_FILE_VERSION,
    applicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
    database: "hector_workout_tracker_fresh_v1",
    exportedAt: "2026-06-16T12:00:00.000Z",
    workouts: data.workouts,
    weights: data.legacyWeights,
    templates: data.routines,
    settings: data.settings,
    goals: data.goals,
    backupMeta: data.backupMeta,
    ...overrides,
  };
}

const FIXTURES = {
  emptyNewInstallation: {
    workouts: [],
    legacyWeights: [],
    routines: [],
    settings: null,
    goals: null,
    draft: null,
    backupMeta: null,
  },
  unversionedLegacyInstallation: legacyApplicationData(),
  validCurrentPersistedData: canonicalApplicationData(),
  oldSupportedBackup: legacyBackup(),
  newCurrentBackup: currentBackup(),
  backupWithoutLegacyWeights: (() => {
    const backup = legacyBackup();
    delete backup.weights;
    return backup;
  })(),
  malformedTopLevelBackup: [],
  malformedWorkout: legacyBackup({
    workouts: [
      { id: "bad", date: "2026-06-01", type: "Bad", exercises: "bad" },
    ],
  }),
  malformedExercise: legacyBackup({
    workouts: [
      { id: "bad", date: "2026-06-01", type: "Bad", exercises: [null] },
    ],
  }),
  malformedSet: legacyBackup({
    workouts: [
      {
        id: "bad",
        date: "2026-06-01",
        type: "Bad",
        exercises: [{ name: "Bad", sets: ["bad"] }],
      },
    ],
  }),
  malformedRoutine: legacyBackup({
    templates: [{ id: "bad", name: "Bad", exercises: {} }],
  }),
  futureApplicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION + 1,
  futureBackupFileVersion: currentBackup({
    backupFileVersion: CURRENT_BACKUP_FILE_VERSION + 1,
  }),
  validZeroValues: canonicalApplicationData({
    workouts: [
      canonicalWorkout({
        durationMinutes: 0,
        exercises: [
          canonicalExercise({
            sets: [canonicalSet({ weight: "0", reps: "0", rpe: "0" })],
          }),
        ],
      }),
    ],
  }),
  missingOptionalFields: (() => {
    const data = canonicalApplicationData();
    delete data.workouts[0].durationMinutes;
    return data;
  })(),
  unknownPreservedFields: legacyApplicationData(),
  nullIdTransactionFailure: legacyBackup({
    workouts: [{ ...legacyApplicationData().workouts[0], id: null }],
  }),
  mixedIndexedDbAndLocalStorageMigrationData: legacyApplicationData(),
  failedStartupMigration: legacyApplicationData({
    workouts: [
      { id: "bad", date: "2026-06-01", type: "Bad", exercises: "bad" },
    ],
  }),
  retryAfterFailedMigration: legacyApplicationData(),
  currentVersionStartupWithoutDuplication: canonicalApplicationData(),
};

export function getSchemaFixture(name) {
  return cloneFixture(FIXTURES[name]);
}
