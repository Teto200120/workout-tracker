import { readFile, writeFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import {
  CURRENT_APPLICATION_SCHEMA_VERSION,
  CURRENT_BACKUP_FILE_VERSION,
} from "../../src/js/schema/versions.js";
import {
  completeOnboarding,
  loadApp,
  monitorRuntime,
  openPrimary,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

async function openBackup(page) {
  await openPrimary(page, "profile");
  await page.locator('[data-profile-target="backup"]').first().click();
  await expect(page.locator("#backup")).toHaveClass(/active/);
}

async function importFile(page, path) {
  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#importFile").setInputFiles(path);
}

test("backup export, clear, restore, and invalid-import rollback", async ({
  page,
}, testInfo) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  const workout = workoutFixture(1, {
    id: "round-trip-workout",
    type: "Round Trip Routine",
  });
  const routine = {
    id: "round-trip-routine",
    name: "Round Trip Routine",
    exercises: ["Flat Bench Press"],
    createdAt: "2026-06-01T10:00:00.000Z",
    updatedAt: "2026-06-01T10:00:00.000Z",
  };
  await seedStores(page, {
    workouts: [workout],
    weights: [],
    templates: [routine],
  });
  await page.evaluate(async () => {
    localStorage.setItem(
      "hector_workout_goals_v1",
      JSON.stringify({ weeklyGoal: 3 }),
    );
    const { cloneDefaultSettings, getAppSettings, setAppSettings } =
      await import("/src/js/core/settings.js");
    setAppSettings({
      ...cloneDefaultSettings(),
      displayName: getAppSettings().displayName,
      animations: false,
    });
  });
  await openBackup(page);

  const downloadPromise = page.waitForEvent("download");
  await page.locator("#exportData").click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(
    /^workout-tracker-backup-.*\.json$/,
  );
  const exported = JSON.parse(await readFile(await download.path(), "utf8"));
  expect(exported.backupFileVersion).toBe(CURRENT_BACKUP_FILE_VERSION);
  expect(exported.applicationSchemaVersion).toBe(
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );
  expect(exported.version).toBeUndefined();
  expect(exported.workouts).toHaveLength(1);
  expect(exported.templates).toHaveLength(1);
  expect(exported.settings.displayName).toBe("Test User");

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#clearData").click();
  expect(await readStore(page, "workouts")).toEqual([]);
  expect(await readStore(page, "weights")).toEqual([]);
  const storageAfterClear = await page.evaluate(() => ({
    goals: localStorage.getItem("hector_workout_goals_v1"),
    draft: localStorage.getItem("hector_workout_draft_v1"),
    settings: localStorage.getItem("hector_workout_settings_v1"),
    backup: localStorage.getItem("hector_workout_backup_meta_v1"),
    schema: localStorage.getItem("hector_workout_data_schema_version"),
  }));
  expect(storageAfterClear).toEqual({
    goals: null,
    draft: null,
    settings: null,
    backup: null,
    schema: null,
  });
  await expect(page.locator("#onboarding")).toBeVisible();
  await completeOnboarding(page, "Temporary User");
  await openBackup(page);

  delete exported.weights;
  const validPath = testInfo.outputPath("backup-without-weights.json");
  await writeFile(validPath, JSON.stringify(exported));
  await importFile(page, validPath);
  await expect
    .poll(async () => (await readStore(page, "workouts")).length)
    .toBe(1);
  await expect
    .poll(async () =>
      (await readStore(page, "templates")).some(
        (item) => item.id === routine.id,
      ),
    )
    .toBe(true);
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_data_schema_version"),
    ),
  ).toBe(String(CURRENT_APPLICATION_SCHEMA_VERSION));
  await openPrimary(page, "home");
  await expect(page.locator("#todayWorkoutSelect")).toContainText(
    "Round Trip Routine",
  );
  await expect(page.locator("#todayGreeting")).toContainText("Test User");
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem("hector_workout_settings_v1"))
          .displayName,
    ),
  ).toBe("Test User");

  const invalidPath = testInfo.outputPath("invalid-backup.json");
  await writeFile(invalidPath, JSON.stringify({ workouts: "not-an-array" }));
  await page.locator("#importFile").setInputFiles(invalidPath);
  await expect(page.locator("#toast")).toContainText("Could not import backup");
  expect(await readStore(page, "workouts")).toHaveLength(1);

  const malformedNamePath = testInfo.outputPath("malformed-name-backup.json");
  await writeFile(
    malformedNamePath,
    JSON.stringify({
      ...exported,
      settings: { ...exported.settings, displayName: "x".repeat(81) },
    }),
  );
  await page.locator("#importFile").setInputFiles(malformedNamePath);
  await expect(page.locator("#toast")).toContainText("Could not import backup");
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem("hector_workout_settings_v1"))
          .displayName,
    ),
  ).toBe("Test User");
  expect(await readStore(page, "workouts")).toHaveLength(1);

  const partialPath = testInfo.outputPath("transaction-failure-backup.json");
  await writeFile(
    partialPath,
    JSON.stringify({
      workouts: [
        workoutFixture(40, { id: "must-roll-back" }),
        workoutFixture(41, { id: null }),
      ],
      templates: [],
    }),
  );
  await importFile(page, partialPath);
  await expect(page.locator("#toast")).toContainText("Could not import backup");
  const workoutsAfterFailure = await readStore(page, "workouts");
  expect(
    workoutsAfterFailure.some((item) => item.id === "must-roll-back"),
  ).toBe(false);
  expect(workoutsAfterFailure).toHaveLength(1);
  assertNoRuntimeErrors();
});

test("legacy backups migrate and future backup versions are rejected", async ({
  page,
}, testInfo) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await openBackup(page);

  const legacyWorkout = workoutFixture(70, {
    id: "legacy-backup-workout",
    exercises: [
      {
        name: "  Legacy Backup Press  ",
        sets: [{ weight: 0, reps: "0", rpe: null, done: 1 }],
        unknownExerciseField: "retained",
      },
    ],
  });
  delete legacyWorkout.notes;
  delete legacyWorkout.tags;
  delete legacyWorkout.createdAt;
  delete legacyWorkout.durationMinutes;
  const legacyPath = testInfo.outputPath("legacy-v2-backup.json");
  await writeFile(
    legacyPath,
    JSON.stringify({
      app: "Hector's Workout Tracker",
      version: 2,
      exportedAt: "2026-06-20T12:00:00.000Z",
      workouts: [legacyWorkout],
      templates: [
        {
          id: "legacy-backup-routine",
          name: "  Legacy Backup Routine  ",
          exercises: ["  Legacy Backup Press  "],
        },
      ],
      goals: { weeklyGoal: "3", targetWeight: "170" },
      settings: { animations: "false" },
    }),
  );
  await importFile(page, legacyPath);
  await expect
    .poll(async () =>
      (await readStore(page, "workouts")).some(
        (item) => item.id === "legacy-backup-workout",
      ),
    )
    .toBe(true);
  const imported = (await readStore(page, "workouts")).find(
    (item) => item.id === "legacy-backup-workout",
  );
  expect(imported).toMatchObject({ notes: "", tags: [], createdAt: "" });
  expect(imported.exercises[0]).toMatchObject({
    name: "Legacy Backup Press",
    notes: "",
    unknownExerciseField: "retained",
  });
  expect(imported.exercises[0].sets[0]).toMatchObject({
    weight: "0",
    reps: "0",
    rpe: "",
    done: true,
    warmup: false,
  });
  await expect(page.locator("#onboarding")).toBeVisible();
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem("hector_workout_settings_v1"))
          .displayName,
    ),
  ).toBe(null);
  await completeOnboarding(page, "Legacy Import User");
  await openBackup(page);

  const beforeFutureImport = await readStore(page, "workouts");
  const futurePath = testInfo.outputPath("future-backup.json");
  await writeFile(
    futurePath,
    JSON.stringify({
      backupFileVersion: CURRENT_BACKUP_FILE_VERSION + 1,
      applicationSchemaVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
      workouts: [workoutFixture(71)],
    }),
  );
  await page.locator("#importFile").setInputFiles(futurePath);
  await expect(page.locator("#toast")).toContainText("newer app version");
  expect(await readStore(page, "workouts")).toEqual(beforeFutureImport);
  assertNoRuntimeErrors();
});
