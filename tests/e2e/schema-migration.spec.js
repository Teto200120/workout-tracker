import { expect, test } from "@playwright/test";
import { CURRENT_APPLICATION_SCHEMA_VERSION } from "../../src/js/schema/versions.js";
import {
  canonicalDraft,
  canonicalRoutine,
  canonicalSettings,
  canonicalWorkout,
} from "../fixtures/schema-data.js";
import {
  loadApp,
  monitorRuntime,
  readStore,
  seedRawApplicationData,
  waitForDatabaseOpen,
} from "../helpers/app.js";

function legacySeed() {
  return {
    workouts: [
      {
        id: "browser-legacy-workout",
        date: "2026-06-10",
        type: "Legacy Browser Routine",
        exercises: [
          {
            name: "  Browser Press  ",
            sets: [{ weight: 0, reps: "0", rpe: null, done: 1 }],
            futureExerciseField: { retained: true },
          },
        ],
        unknownWorkoutField: "retained",
      },
    ],
    weights: [
      {
        id: "browser-legacy-weight",
        date: "2026-05-01",
        weight: "175.5",
      },
    ],
    templates: [
      {
        id: "browser-legacy-routine",
        name: "  Legacy Browser Routine  ",
        exercises: ["  Browser Press  "],
      },
    ],
    settings: {
      schedule: { 1: { kind: "gym", routine: "Legacy Browser Routine" } },
      animations: "false",
    },
    goals: { weeklyGoal: "3", targetWeight: "170" },
    draft: {
      id: "browser-legacy-draft",
      date: "2026-06-11",
      type: "Legacy Browser Routine",
      startTime: "18:00",
      exercises: [
        {
          name: "Browser Press",
          notes: "Legacy draft note",
          sets: [{ weight: 0, reps: "5", done: false }],
        },
      ],
    },
    backupMeta: { lastExportedAt: "2026-06-01T12:00:00.000Z" },
    schemaVersion: null,
  };
}

test("legacy startup migrates IndexedDB and localStorage without duplication", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await seedRawApplicationData(page, legacySeed());
  await page.reload();
  await waitForDatabaseOpen(page);

  await expect(page.locator("#todayGreeting")).not.toContainText("Loading");
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_data_schema_version"),
    ),
  ).toBe(String(CURRENT_APPLICATION_SCHEMA_VERSION));

  const workouts = await readStore(page, "workouts");
  expect(workouts).toHaveLength(1);
  expect(workouts[0]).toMatchObject({
    id: "browser-legacy-workout",
    date: "2026-06-10",
    notes: "",
    tags: [],
    unknownWorkoutField: "retained",
  });
  expect(workouts[0].exercises[0]).toMatchObject({
    name: "Browser Press",
    notes: "",
    futureExerciseField: { retained: true },
  });
  expect(workouts[0].exercises[0].sets[0]).toMatchObject({
    weight: "0",
    reps: "0",
    rpe: "",
    done: true,
    warmup: false,
  });

  const routines = await readStore(page, "templates");
  expect(routines).toHaveLength(1);
  expect(routines[0]).toMatchObject({
    id: "browser-legacy-routine",
    name: "Legacy Browser Routine",
    exercises: ["Browser Press"],
    createdAt: "",
    updatedAt: "",
  });

  const localData = await page.evaluate(() => ({
    settings: JSON.parse(localStorage.getItem("hector_workout_settings_v1")),
    goals: JSON.parse(localStorage.getItem("hector_workout_goals_v1")),
    draft: JSON.parse(localStorage.getItem("hector_workout_draft_v1")),
  }));
  expect(localData.settings.animations).toBe(false);
  expect(localData.settings.defaultWeightJump).toBe(5);
  expect(localData.goals).toMatchObject({ weeklyGoal: 3, targetWeight: "170" });
  expect(localData.draft).toMatchObject({
    id: "browser-legacy-draft",
    activeExerciseIndex: 0,
    editingWorkoutId: null,
    savedAt: "",
  });

  await expect(page.locator("#todayWorkoutSelect")).toContainText(
    "Legacy Browser Routine",
  );
  await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
    "Resume",
  );
  await page.locator("#todayStartWorkout").click({ force: true });
  await expect(page.locator(".exercise-name").first()).toHaveValue(
    "Browser Press",
  );
  await expect(page.locator(".exercise-notes").first()).toHaveValue(
    "Legacy draft note",
  );
  assertNoRuntimeErrors();
});

test("current startup validates without rewriting or duplicating records", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  const workout = canonicalWorkout({ currentSentinel: { unchanged: true } });
  const routine = canonicalRoutine({ name: "Current Browser Routine" });
  const settings = canonicalSettings({
    animations: false,
    currentSettingSentinel: 0,
  });
  await seedRawApplicationData(page, {
    workouts: [workout],
    weights: [],
    templates: [routine],
    settings,
    goals: { weeklyGoal: 4 },
    draft: null,
    backupMeta: {},
    schemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
  });
  const before = await page.evaluate(() => ({
    settings: localStorage.getItem("hector_workout_settings_v1"),
    marker: localStorage.getItem("hector_workout_data_schema_version"),
  }));

  await page.reload();
  await waitForDatabaseOpen(page);
  expect(await readStore(page, "workouts")).toEqual([workout]);
  expect(await readStore(page, "templates")).toEqual([routine]);
  const after = await page.evaluate(() => ({
    settings: localStorage.getItem("hector_workout_settings_v1"),
    marker: localStorage.getItem("hector_workout_data_schema_version"),
  }));
  expect(after).toEqual(before);
  assertNoRuntimeErrors();
});

test("failed startup migration preserves source data and succeeds on retry", async ({
  page,
}) => {
  await loadApp(page);
  const malformed = {
    id: "malformed-startup-workout",
    date: "2026-06-12",
    type: "Malformed",
    exercises: "not-an-array",
  };
  const originalGoals = JSON.stringify({
    weeklyGoal: "3",
    compatibility: true,
  });
  await seedRawApplicationData(page, {
    workouts: [malformed],
    weights: [],
    templates: [],
    rawLocalStorage: { hector_workout_goals_v1: originalGoals },
    schemaVersion: null,
  });

  await page.reload();
  await expect(page.locator("#toast")).toContainText("could not be migrated");
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_data_schema_version"),
    ),
  ).toBe(null);
  expect(
    await page.evaluate(() => localStorage.getItem("hector_workout_goals_v1")),
  ).toBe(originalGoals);
  expect(await readStore(page, "workouts")).toEqual([malformed]);

  await seedRawApplicationData(page, legacySeed());
  await page.reload();
  await waitForDatabaseOpen(page);
  await expect(page.locator("#todayGreeting")).not.toContainText("Loading");
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_data_schema_version"),
    ),
  ).toBe(String(CURRENT_APPLICATION_SCHEMA_VERSION));
  expect(await readStore(page, "workouts")).toHaveLength(1);
});

test("future application schema versions stop startup without overwriting data", async ({
  page,
}) => {
  await loadApp(page);
  const workout = canonicalWorkout({ id: "future-marker-workout" });
  await seedRawApplicationData(page, {
    workouts: [workout],
    weights: [],
    templates: [canonicalRoutine()],
    settings: canonicalSettings(),
    goals: { weeklyGoal: 4 },
    draft: canonicalDraft(),
    backupMeta: {},
    schemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION + 1,
  });
  await page.reload();
  await expect(page.locator("#toast")).toContainText("newer app version");
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_data_schema_version"),
    ),
  ).toBe(String(CURRENT_APPLICATION_SCHEMA_VERSION + 1));
  expect(await readStore(page, "workouts")).toEqual([workout]);
});
