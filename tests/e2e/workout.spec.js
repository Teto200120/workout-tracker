import { expect, test } from "@playwright/test";
import {
  loadApp,
  monitorRuntime,
  openPrimary,
  readStore,
  startRoutine,
} from "../helpers/app.js";

test("starting immediately uses the newly selected Home routine", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);

  const initialRoutine = await page.locator("#todayWorkoutSelect").inputValue();
  const selectedRoutine =
    initialRoutine === "Back / Biceps" ? "Legs" : "Back / Biceps";
  const expectedExercises =
    selectedRoutine === "Legs"
      ? ["Squat", "Romanian Deadlift", "Leg Press", "Calf Raise"]
      : [
          "V-Bar Lat Pulldown",
          "V-Bar Cable Row",
          "Incline Hammer Curl",
          "Cable Bicep Curl",
        ];

  await startRoutine(page, selectedRoutine);
  await expect(page.locator("#workoutType")).toHaveValue(selectedRoutine);
  const visibleExercises = await page
    .locator(".exercise-name")
    .evaluateAll((inputs) => inputs.map((input) => input.value));
  expect(visibleExercises).toEqual(expectedExercises);
  expect(visibleExercises).not.toContain("Flat Bench Press");
  assertNoRuntimeErrors();
});

test("active workout draft restores values, notes, and active exercise", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  const firstExercise = page.locator(".exercise").nth(0);
  await firstExercise
    .locator('.control-value-input[data-field="weight"]')
    .fill("135");
  await firstExercise
    .locator('.control-value-input[data-field="reps"]')
    .fill("9");
  await firstExercise.locator(".guide-row").click();
  await page.locator("#exerciseDetailNotes").fill("Keep elbows tucked");
  await page.locator("#exerciseDetailBack").click();
  await page.locator("#workoutNotes").fill("Draft recovery test note");
  await page.locator(".exercise").nth(1).locator(".exercise-top").click();
  await expect(page.locator(".exercise").nth(1)).not.toHaveClass(/collapsed/);

  await page.reload();
  await expect(page.locator("#todayStartWorkout")).toBeVisible();
  await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
    "Resume",
  );
  await page.locator("#todayStartWorkout").click({ force: true });
  await expect(page.locator("#workoutNotes")).toHaveValue(
    "Draft recovery test note",
  );
  await expect(
    page.locator(".exercise").nth(0).locator(".set-weight").nth(0),
  ).toHaveValue("135");
  await expect(
    page.locator(".exercise").nth(0).locator(".set-reps").nth(0),
  ).toHaveValue("9");
  await expect(
    page.locator(".exercise").nth(0).locator(".exercise-notes"),
  ).toHaveValue("Keep elbows tucked");
  await expect(page.locator(".exercise").nth(1)).not.toHaveClass(/collapsed/);
  assertNoRuntimeErrors();
});

test("a completed save appears once in History with details", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page, "Legs");

  const firstExercise = page.locator(".exercise").first();
  await firstExercise
    .locator('.control-value-input[data-field="weight"]')
    .fill("225");
  await firstExercise
    .locator('.control-value-input[data-field="reps"]')
    .fill("5");
  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#completionModal")).toBeVisible();
  await page.locator("#completionDone").click();

  const saved = await readStore(page, "workouts");
  expect(saved).toHaveLength(1);
  await openPrimary(page, "stats");
  await page.locator('[data-stats-detail="history"]').click();
  await expect(page.locator("#historyList .stats-session-card")).toHaveCount(1);
  await expect(page.locator("#historyList")).toContainText("Legs");
  await page.locator("#historyList details").first().click();
  await expect(page.locator("#historyList details").first()).toHaveAttribute(
    "open",
    "",
  );
  assertNoRuntimeErrors();
});
