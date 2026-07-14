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

test("Exercise Details owns scrolling and keeps notes above the action dock", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  await page.locator(".exercise").first().locator(".guide-row").click();
  const detailView = page.locator("#exerciseDetailView");
  const detailScroll = page.locator(".exercise-detail-scroll");
  await expect(detailView).toBeVisible();
  await expect(page.locator("#exerciseDetailCompleteSet")).toBeVisible();
  await expect
    .poll(() =>
      page
        .locator("body")
        .evaluate(
          (body) =>
            body.ownerDocument.defaultView.getComputedStyle(body).overflow,
        ),
    )
    .toBe("hidden");

  const documentScrollBefore = await page
    .locator("html")
    .evaluate((element) => element.ownerDocument.scrollingElement.scrollTop);
  const headerTopBefore = await page
    .locator(".exercise-detail-header")
    .evaluate((element) => element.getBoundingClientRect().top);

  await detailScroll.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
  });
  await expect
    .poll(() => detailScroll.evaluate((element) => element.scrollTop))
    .toBeGreaterThan(0);

  const layout = await detailView.evaluate((view) => {
    const header = view.querySelector(".exercise-detail-header");
    const scroller = view.querySelector(".exercise-detail-scroll");
    const dock = view.querySelector(".exercise-detail-dock");
    const notes = view.querySelector("#exerciseDetailNotes");
    return {
      viewportHeight: view.ownerDocument.defaultView.innerHeight,
      view: view.getBoundingClientRect().toJSON(),
      header: header.getBoundingClientRect().toJSON(),
      scroller: scroller.getBoundingClientRect().toJSON(),
      dock: dock.getBoundingClientRect().toJSON(),
      notes: notes.getBoundingClientRect().toJSON(),
      scrollHeight: scroller.scrollHeight,
      clientHeight: scroller.clientHeight,
    };
  });

  expect(Math.abs(layout.view.top)).toBeLessThanOrEqual(2);
  expect(
    Math.abs(layout.view.bottom - layout.viewportHeight),
  ).toBeLessThanOrEqual(2);
  expect(Math.abs(layout.header.top - headerTopBefore)).toBeLessThanOrEqual(4);
  expect(layout.scrollHeight).toBeGreaterThan(layout.clientHeight);
  expect(layout.scroller.top).toBeGreaterThanOrEqual(layout.header.bottom - 2);
  expect(layout.scroller.bottom).toBeLessThanOrEqual(layout.dock.top + 2);
  expect(layout.notes.bottom).toBeLessThanOrEqual(layout.dock.top - 8);
  expect(layout.dock.bottom).toBeLessThanOrEqual(layout.viewportHeight + 2);

  await page
    .locator("html")
    .evaluate((element) => element.ownerDocument.defaultView.scrollBy(0, 300));
  expect(
    await page
      .locator("html")
      .evaluate((element) => element.ownerDocument.scrollingElement.scrollTop),
  ).toBe(documentScrollBefore);

  await page.locator("#exerciseDetailNotes").fill("Controlled mobile notes");
  await expect(page.locator("#exerciseDetailNotes")).toHaveValue(
    "Controlled mobile notes",
  );
  await page.locator("#exerciseDetailBack").click();
  await expect(detailView).toBeHidden();
  await expect(
    page.locator(".exercise").first().locator(".exercise-notes"),
  ).toHaveValue("Controlled mobile notes");
  assertNoRuntimeErrors();
});

test("Add Exercise opens a non-destructive searchable picker", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page, "Chest / Triceps");

  const exercises = page.locator(".exercise");
  const initialCount = await exercises.count();
  const draftBefore = await page
    .locator("html")
    .evaluate((element) =>
      element.ownerDocument.defaultView.localStorage.getItem(
        "hector_workout_draft_v1",
      ),
    );
  await page.locator("#addExercise").click();
  await expect(page.locator("#exercisePicker")).toBeVisible();
  await expect(page.locator("#exercisePickerSearch")).toBeFocused();
  await expect(exercises).toHaveCount(initialCount);
  await expect(page.locator(".exercise-picker-option.is-local")).toHaveCount(3);
  await page.locator("[data-show-all-local]").click();
  await expect(
    page.getByRole("button", { name: "Flat Bench Press", exact: true }),
  ).toHaveCount(1);

  await page.locator("#exercisePickerSearch").fill("press");
  const filteredNames = await page
    .locator("#exercisePickerOptions .exercise-picker-option")
    .allTextContents();
  expect(filteredNames.length).toBeGreaterThan(0);
  expect(filteredNames.every((name) => /press/i.test(name))).toBe(true);

  await page.locator(".exercise-picker-cancel").click();
  await expect(page.locator("#exercisePicker")).toBeHidden();
  await expect(page.locator("#addExercise")).toBeFocused();
  await expect(exercises).toHaveCount(initialCount);
  expect(
    await page
      .locator("html")
      .evaluate((element) =>
        element.ownerDocument.defaultView.localStorage.getItem(
          "hector_workout_draft_v1",
        ),
      ),
  ).toBe(draftBefore);
  assertNoRuntimeErrors();
});

test("selecting an existing exercise adds, opens, and restores it from the draft", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  const exercises = page.locator(".exercise");
  const initialCount = await exercises.count();
  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerSearch").fill("Romanian deadlift");
  await page
    .getByRole("button", { name: "Romanian Deadlift", exact: true })
    .click();

  await expect(page.locator("#exercisePicker")).toBeHidden();
  await expect(exercises).toHaveCount(initialCount + 1);
  const addedExercise = exercises.last();
  await expect(addedExercise.locator(".exercise-name")).toHaveValue(
    "Romanian Deadlift",
  );
  await expect(addedExercise).not.toHaveClass(/collapsed/);

  await page.reload();
  await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
    "Resume",
  );
  await page.locator("#todayStartWorkout").click({ force: true });
  expect(
    await page
      .locator(".exercise-name")
      .evaluateAll((inputs) => inputs.map((input) => input.value)),
  ).toEqual([
    "V-Bar Lat Pulldown",
    "V-Bar Cable Row",
    "Incline Hammer Curl",
    "Cable Bicep Curl",
    "Romanian Deadlift",
  ]);
  await expect(page.locator(".exercise").last()).not.toHaveClass(/collapsed/);
  assertNoRuntimeErrors();
});

test("creating an exercise validates, trims, and adds the custom name", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  const exercises = page.locator(".exercise");
  const initialCount = await exercises.count();
  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerCreateAction").click();
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  await expect(page.locator("#exercisePickerCustomError")).toHaveText(
    "Enter an exercise name.",
  );
  await expect(exercises).toHaveCount(initialCount);

  await page.locator("#exercisePickerCustomName").fill("  Landmine Press  ");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  await expect(page.locator("#exercisePicker")).toBeHidden();
  await expect(exercises).toHaveCount(initialCount + 1);
  await expect(exercises.last().locator(".exercise-name")).toHaveValue(
    "Landmine Press",
  );
  await expect(exercises.last()).not.toHaveClass(/collapsed/);

  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerSearch").fill("LANDMINE PRESS");
  await expect(
    page.getByRole("button", { name: "Landmine Press", exact: true }),
  ).toHaveCount(1);
  await page.keyboard.press("Escape");
  await expect(page.locator("#exercisePicker")).toBeHidden();
  await expect(exercises).toHaveCount(initialCount + 1);
  assertNoRuntimeErrors();
});

test("manual rest timer is absent while workout elapsed time and duration remain", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  await expect(
    page.locator("[data-timer], #timerDisplay, #timerText, #stopTimer"),
  ).toHaveCount(0);
  const elapsed = page.locator("#sessionElapsedTimer");
  await expect(elapsed).toBeVisible();
  await expect(elapsed).toHaveText(/^\d+:\d{2}(?::\d{2})?$/);
  const initialElapsed = await elapsed.textContent();
  await expect.poll(() => elapsed.textContent()).not.toBe(initialElapsed);

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#completionModal")).toBeVisible();
  const saved = await readStore(page, "workouts");
  expect(saved).toHaveLength(1);
  expect(saved[0].durationMinutes).toEqual(expect.any(Number));
  expect(saved[0].durationMinutes).toBeGreaterThanOrEqual(0);
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
