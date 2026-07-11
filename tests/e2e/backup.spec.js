import { readFile, writeFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import {
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
  await page.evaluate(() => {
    localStorage.setItem(
      "hector_workout_goals_v1",
      JSON.stringify({ weeklyGoal: 3 }),
    );
    globalThis.setAppSettings({
      ...globalThis.cloneDefaultSettings(),
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
  expect(exported.workouts).toHaveLength(1);
  expect(exported.templates).toHaveLength(1);

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#clearData").click();
  expect(await readStore(page, "workouts")).toEqual([]);
  expect(await readStore(page, "weights")).toEqual([]);
  const storageAfterClear = await page.evaluate(() => ({
    goals: localStorage.getItem("hector_workout_goals_v1"),
    draft: localStorage.getItem("hector_workout_draft_v1"),
    settings: localStorage.getItem("hector_workout_settings_v1"),
    backup: localStorage.getItem("hector_workout_backup_meta_v1"),
  }));
  expect(storageAfterClear).toEqual({
    goals: null,
    draft: null,
    settings: null,
    backup: null,
  });

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
  await openPrimary(page, "home");
  await expect(page.locator("#todayWorkoutSelect")).toContainText(
    "Round Trip Routine",
  );

  const invalidPath = testInfo.outputPath("invalid-backup.json");
  await writeFile(invalidPath, JSON.stringify({ workouts: "not-an-array" }));
  await page.locator("#importFile").setInputFiles(invalidPath);
  await expect(page.locator("#toast")).toContainText("Could not import backup");
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
