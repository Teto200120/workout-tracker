import { writeFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { currentBackup } from "../fixtures/schema-data.js";
import {
  loadApp,
  monitorRuntime,
  openPrimary,
  readStore,
  seedStores,
  startRoutine,
  workoutFixture,
} from "../helpers/app.js";

async function openSettings(page) {
  await openPrimary(page, "profile");
  await page.locator("[data-open-settings]").first().click();
  await expect(page.locator("#settings")).toHaveClass(/active/u);
}

async function markEverySetDone(page) {
  await page.locator(".set-done").evaluateAll((inputs) => {
    inputs.forEach((input) => {
      input.checked = true;
      input.dispatchEvent(new Event("change", { bubbles: true }));
    });
  });
  await expect(page.locator("#saveWorkout")).toHaveText("Finish Workout");
}

test("careless numeric input is preserved, rejected, corrected, and saved safely", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  const exercise = page.locator(".exercise").first();
  const weight = exercise.locator('.control-value-input[data-field="weight"]');
  const reps = exercise.locator('.control-value-input[data-field="reps"]');
  const rpe = exercise.locator('.control-value-input[data-field="rpe"]');

  await weight.fill("-1");
  await expect(weight).toHaveValue("-1");
  await expect(weight).toHaveAttribute("aria-invalid", "true");
  await weight.fill("1e6");
  await expect(exercise.locator("[data-set-validation]")).toContainText(
    "without scientific notation",
  );
  await weight.fill("300000");
  await expect(exercise.locator("[data-set-validation]")).toContainText(
    "10,000 or less",
  );

  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#toast")).toContainText("10,000 or less");
  expect(await readStore(page, "workouts")).toHaveLength(0);
  expect(
    await page.evaluate(() => {
      const draft = JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
      return draft.exercises[0].sets[0].weight;
    }),
  ).not.toBe("300000");

  await weight.fill("225.5");
  await reps.fill("8.5");
  await expect(reps).toHaveAttribute("aria-invalid", "true");
  await reps.fill("8");
  await rpe.fill("11");
  await expect(rpe).toHaveAttribute("aria-invalid", "true");
  await rpe.fill("10");
  await exercise.locator(".complete-set-button").click();

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#completionModal")).toBeVisible();
  await page.locator("#completionDone").click();
  await expect(page.locator("#completionModal")).toBeHidden();

  const workouts = await readStore(page, "workouts");
  expect(workouts).toHaveLength(1);
  expect(workouts[0].exercises[0].sets[0]).toMatchObject({
    weight: "225.5",
    reps: "8",
    rpe: "10",
  });
  assertNoRuntimeErrors();
});

test("top and bottom save taps plus double completion submission create one record", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);
  await markEverySetDone(page);

  await page.evaluate(() => {
    globalThis.document.querySelector("#sessionSaveTop").click();
    globalThis.document.querySelector("#saveWorkout").click();
  });
  await expect(page.locator("#completionModal")).toBeVisible();
  await expect(page.locator("#completionDone")).toBeFocused();
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

  await page.keyboard.press("Tab");
  await expect(page.locator("[data-completion-tag]").first()).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(page.locator("#completionDone")).toBeFocused();
  await page.evaluate(() => {
    globalThis.document.querySelector("#completionDone").click();
    globalThis.document.querySelector("#completionDone").click();
  });
  await expect(page.locator("#completionModal")).toBeHidden();
  expect(await readStore(page, "workouts")).toHaveLength(1);

  await page.reload();
  await expect(page.locator("#completionModal")).toBeHidden();
  expect(await readStore(page, "workouts")).toHaveLength(1);
  assertNoRuntimeErrors();
});

test("custom Unicode input survives while long names, notes, and executable-looking text stay safe", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);
  const initialCount = await page.locator(".exercise").count();

  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerCreateAction").click();
  await page.locator("#exercisePickerCustomName").fill("x".repeat(121));
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  await expect(page.locator("#exercisePickerCustomError")).toContainText(
    "120 characters or fewer",
  );
  await expect(page.locator(".exercise")).toHaveCount(initialCount);

  const unicodeName = "Élévation 肩 💪";
  await page.locator("#exercisePickerCustomName").fill(`  ${unicodeName}  `);
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  await expect(page.locator(".exercise")).toHaveCount(initialCount + 1);
  const exercise = page.locator(".exercise").last();
  await expect(exercise.locator(".exercise-name")).toHaveValue(unicodeName);

  await exercise.locator(".guide-row").click();
  const notes = page.locator("#exerciseDetailNotes");
  await notes.fill("x".repeat(2_001));
  await expect(page.locator("#exerciseDetailNotesError")).toContainText(
    "2,000 characters or fewer",
  );
  const hostileText = '<img src=x onerror="window.__injected=true"> 💪 café';
  await notes.fill(hostileText);
  await page.locator("#exerciseDetailBack").click();

  await page.locator("#workoutNotes").fill("x".repeat(4_001));
  await expect(page.locator("#workoutNotesError")).toContainText(
    "4,000 characters or fewer",
  );
  await page
    .locator("#workoutNotes")
    .fill("Quotes ' \" & <script>window.__injected=true</script> ✅");

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#completionModal")).toBeVisible();
  await page.locator("#completionDone").click();
  await openPrimary(page, "stats");
  await page.locator('[data-stats-detail="history"]').click();
  await expect(page.locator("#historyList")).toContainText(unicodeName);
  await expect(page.locator("#historyList")).toContainText(hostileText);
  await expect(page.locator("#historyList img")).toHaveCount(0);
  await expect(page.locator("#historyList script")).toHaveCount(0);
  expect(await page.evaluate(() => globalThis.__injected)).toBeUndefined();
  assertNoRuntimeErrors();
});

test("rapid Add Set, Add Exercise, and catalog selection stop at deterministic boundaries", async ({
  page,
}) => {
  test.setTimeout(60_000);
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page, "Custom");
  const exercises = page.locator(".exercise");
  const initialExerciseCount = await exercises.count();

  await page.evaluate(() => {
    globalThis.document.querySelector("#addExercise").click();
    globalThis.document.querySelector("#addExercise").click();
  });
  await expect(page.locator("#exercisePicker")).toBeVisible();
  await page.locator("#exercisePickerCreateAction").click();
  await page.locator("#exercisePickerCustomName").fill("Tap Test");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  await expect(exercises).toHaveCount(initialExerciseCount + 1);

  const addedExercise = exercises.last();
  await addedExercise.locator(".add-set").evaluate((button) => {
    for (let index = 0; index < 205; index += 1) button.click();
  });
  await expect(addedExercise.locator(".set-row")).toHaveCount(200);
  await expect(page.locator("#toast")).toContainText("limited to 200 sets");

  await page.evaluate(() => {
    globalThis.document.querySelector("#addExercise").click();
    globalThis.document.querySelector("#addExercise").click();
  });
  await page.locator("#exercisePickerSearch").fill("Air Bike");
  await page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]').click();
  await page.locator("#exercisePickerPreviewAdd").evaluate((button) => {
    button.click();
    button.click();
  });
  await expect(exercises).toHaveCount(initialExerciseCount + 2);
  await expect(exercises.last().locator(".exercise-name")).toHaveValue(
    "Air Bike",
  );
  assertNoRuntimeErrors();
});

test("routine, settings, and goal actions reject bad values and remain single-flight", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await openPrimary(page, "profile");
  await page.locator('[data-profile-target="templates"]').click();

  await page.locator("#templateName").fill("x".repeat(81));
  await page.locator("#saveTemplate").click();
  await expect(page.locator("#templateNameError")).toContainText(
    "80 characters or fewer",
  );
  await page.locator("#templateName").fill("Unicode Routine 💪");
  await page.locator("#templateExerciseInput").fill("Goblet Squat");
  await page.locator("#addTemplateExercise").click();
  await page.evaluate(() => {
    globalThis.document.querySelector("#saveTemplate").click();
    globalThis.document.querySelector("#saveTemplate").click();
  });
  await expect(page.locator("#toast")).toContainText("Routine saved");
  expect(
    (await readStore(page, "templates")).filter(
      (routine) => routine.name === "Unicode Routine 💪",
    ),
  ).toHaveLength(1);

  await openSettings(page);
  await page.locator("#settingsWeightJump").fill("-1");
  await page.locator("#saveSettings").click();
  await expect(page.locator("#settingsWeightJump")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await page.locator("#settingsWeightJump").fill("2.5");
  await page.locator("#settingsCompoundMin").fill("20");
  await page.locator("#settingsCompoundMax").fill("10");
  await page.locator("#saveSettings").click();
  await expect(page.locator("#settingsCompoundMax")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await page.locator("#settingsCompoundMax").fill("25");
  await page.locator("#saveSettings").click();
  await expect
    .poll(() =>
      page.evaluate(() =>
        JSON.parse(localStorage.getItem("hector_workout_settings_v1")),
      ),
    )
    .toMatchObject({
      defaultWeightJump: 2.5,
      compoundMin: 20,
      compoundMax: 25,
    });

  await openPrimary(page, "stats");
  await page.locator('[data-stats-detail="statsGoals"]').click();
  await page.locator("#weeklyGoal").fill("300000");
  await page.locator("#saveGoals").click();
  await expect(page.locator("#weeklyGoalError")).toContainText("100 or less");
  await page.locator("#weeklyGoal").fill("5");
  await page.locator("#saveGoals").click();
  expect(
    await page.evaluate(() =>
      JSON.parse(localStorage.getItem("hector_workout_goals_v1")),
    ),
  ).toMatchObject({ weeklyGoal: 5 });
  assertNoRuntimeErrors();
});

test("oversized, extreme, and duplicate-ID backups fail before writes while one repeated valid import succeeds", async ({
  page,
}, testInfo) => {
  test.setTimeout(60_000);
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(90)] });
  await startRoutine(page);
  const draftBefore = await page.evaluate(() =>
    localStorage.getItem("hector_workout_draft_v1"),
  );
  await page.evaluate(async () => {
    const { switchScreen } = await import("/src/js/router.js");
    switchScreen("backup");
  });
  await expect(page.locator("#backup")).toHaveClass(/active/u);

  const emptyPath = testInfo.outputPath("empty-backup.json");
  await writeFile(emptyPath, "");
  await page.locator("#importFile").setInputFiles(emptyPath);
  await expect(page.locator("#toast")).toContainText("backup file is empty");

  const oversizedPath = testInfo.outputPath("oversized-backup.json");
  await writeFile(oversizedPath, Buffer.alloc(25 * 1024 * 1024 + 1, 32));
  await page.locator("#importFile").setInputFiles(oversizedPath);
  await expect(page.locator("#toast")).toContainText("limited to 25 MB");
  expect(await readStore(page, "workouts")).toHaveLength(1);

  const extreme = currentBackup();
  extreme.workouts[0].exercises[0].sets[0].weight = "300000";
  const extremePath = testInfo.outputPath("extreme-backup.json");
  await writeFile(extremePath, JSON.stringify(extreme));
  await page.locator("#importFile").setInputFiles(extremePath);
  await expect(page.locator("#toast")).toContainText("10,000 or less");
  expect(await readStore(page, "workouts")).toHaveLength(1);

  const duplicate = currentBackup();
  duplicate.workouts.push(structuredClone(duplicate.workouts[0]));
  const duplicatePath = testInfo.outputPath("duplicate-backup.json");
  await writeFile(duplicatePath, JSON.stringify(duplicate));
  await page.locator("#importFile").setInputFiles(duplicatePath);
  await expect(page.locator("#toast")).toContainText("duplicate ID");
  expect(await readStore(page, "workouts")).toHaveLength(1);

  const valid = currentBackup();
  valid.workouts[0].id = "single-repeated-import";
  let confirmationCount = 0;
  page.on("dialog", async (dialog) => {
    confirmationCount += 1;
    expect(dialog.message()).toContain("unsaved workout draft");
    await dialog.accept();
  });
  await page.evaluate(async (backup) => {
    const { importData } = await import("/src/js/screens/backup.js");
    const file = new File([JSON.stringify(backup)], "valid-backup.json", {
      type: "application/json",
    });
    await Promise.all([importData(file), importData(file)]);
  }, valid);
  expect(confirmationCount).toBe(1);
  expect(
    (await readStore(page, "workouts")).filter(
      (workout) => workout.id === "single-repeated-import",
    ),
  ).toHaveLength(1);
  expect(
    await page.evaluate(() => localStorage.getItem("hector_workout_draft_v1")),
  ).toBe(draftBefore);
  await page.reload();
  expect(
    (await readStore(page, "workouts")).some(
      (workout) => workout.id === "single-repeated-import",
    ),
  ).toBe(true);
  assertNoRuntimeErrors();
});

test("IndexedDB save failure keeps the draft recoverable and a retry succeeds", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);
  await page
    .locator(".exercise")
    .first()
    .locator('.control-value-input[data-field="weight"]')
    .fill("135");

  await page.evaluate(() => {
    globalThis.__originalWorkoutPut = globalThis.IDBObjectStore.prototype.put;
    globalThis.IDBObjectStore.prototype.put = function put(value, key) {
      if (this.name === "workouts") {
        throw new DOMException("Simulated write failure", "QuotaExceededError");
      }
      return globalThis.__originalWorkoutPut.call(this, value, key);
    };
  });
  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#toast")).toContainText("Could not save workout");
  expect(await readStore(page, "workouts")).toHaveLength(0);
  expect(
    await page.evaluate(() =>
      Boolean(localStorage.getItem("hector_workout_draft_v1")),
    ),
  ).toBe(true);
  await expect(page.locator("#sessionSaveTop")).toBeEnabled();

  await page.evaluate(() => {
    globalThis.IDBObjectStore.prototype.put = globalThis.__originalWorkoutPut;
    delete globalThis.__originalWorkoutPut;
  });
  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#completionModal")).toBeVisible();
  await page.locator("#completionDone").click();
  expect(await readStore(page, "workouts")).toHaveLength(1);
  assertNoRuntimeErrors();
});

test("empty date is blocked and an overnight historical workout remains valid", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);
  await page.locator("#workoutDate").fill("");
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#workoutDateError")).toContainText("required");
  expect(await readStore(page, "workouts")).toHaveLength(0);

  await page.locator("#workoutDate").fill("2024-02-29");
  await page.locator("#startTime").evaluate((input) => {
    input.type = "text";
  });
  await page.locator("#startTime").fill("24:99");
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#startTimeError")).toContainText(
    "real 24-hour time",
  );
  expect(await readStore(page, "workouts")).toHaveLength(0);
  await page.locator("#startTime").evaluate((input) => {
    input.type = "time";
  });
  await page.locator("#startTime").fill("23:30");
  await page.locator("#endTime").fill("00:15");
  await markEverySetDone(page);
  page.once("dialog", async (dialog) => {
    expect(dialog.message()).toContain("treated as overnight");
    await dialog.accept();
  });
  await page.locator("#sessionSaveTop").click();
  await expect(page.locator("#completionModal")).toBeVisible();
  await page.locator("#completionDone").click();
  const workouts = await readStore(page, "workouts");
  expect(workouts).toHaveLength(1);
  expect(workouts[0]).toMatchObject({
    date: "2024-02-29",
    startTime: "23:30",
    endTime: "00:15",
    durationMinutes: 45,
  });
  assertNoRuntimeErrors();
});
