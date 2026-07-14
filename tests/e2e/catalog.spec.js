import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import {
  loadApp,
  monitorRuntime,
  openPrimary,
  readStore,
  seedStores,
  startRoutine,
  workoutFixture,
} from "../helpers/app.js";

const catalogPath = "src/data/exercise-catalog.json";

test("catalog loads after local options and saves only the canonical exercise name", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  const catalogPayload = JSON.parse(await readFile(catalogPath, "utf8"));
  const airBikeRecord = catalogPayload.exercises.find(
    (exercise) => exercise.catalogId === "free-exercise-db:Air_Bike",
  );
  const requests = [];
  page.on("request", (request) => requests.push(request.url()));
  await page.route("**/src/data/exercise-catalog.json", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 450));
    await route.continue();
  });
  await loadApp(page);
  await startRoutine(page);

  const exercises = page.locator(".exercise");
  const initialCount = await exercises.count();
  await page.locator("#addExercise").click();
  await expect(page.locator(".exercise-picker-option.is-local")).toHaveCount(3);
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "Loading catalog",
  );
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "873 offline",
  );

  await page.locator("#exercisePickerSearch").fill("Air Bike");
  const catalogResult = page.locator(
    '[data-catalog-id="free-exercise-db:Air_Bike"]',
  );
  await expect(catalogResult).toBeVisible();
  await expect(catalogResult).toContainText("Abdominals");
  await expect(catalogResult).toContainText("Body Only");
  await expect(
    page.getByRole("button", { name: "Romanian Deadlift", exact: true }),
  ).toHaveCount(0);

  await page.locator("#exercisePickerSearch").fill("Romanian Deadlift");
  await expect(
    page.getByRole("button", { name: "Romanian Deadlift", exact: true }),
  ).toHaveCount(1);
  await expect(
    page.locator('[data-catalog-id="free-exercise-db:Romanian_Deadlift"]'),
  ).toHaveCount(0);

  await page.locator("#exercisePickerSearch").fill("Air Bike");
  await page.locator("#exercisePickerFilterToggle").click();
  await page.locator("#exercisePickerMuscleFilter").selectOption("abdominals");
  await page
    .locator("#exercisePickerEquipmentFilter")
    .selectOption("body only");
  await expect(catalogResult).toBeVisible();
  await page.locator("#exercisePickerResetFilters").click();
  await expect(page.locator("#exercisePickerMuscleFilter")).toHaveValue("");
  await expect(page.locator("#exercisePickerEquipmentFilter")).toHaveValue("");

  await catalogResult.click();
  await expect(page.locator("#exercisePickerPreview")).toBeVisible();
  await expect(page.locator("#exercisePickerPreviewContent h3")).toHaveText(
    "Air Bike",
  );
  await expect(page.locator("#exercisePickerPreviewContent")).toContainText(
    "Instruction preview",
  );
  await expect(
    page.locator(
      "#exercisePickerPreviewContent .exercise-picker-instructions li",
    ),
  ).toHaveCount(2);
  await expect(page.locator("#exercisePickerPreviewContent")).not.toContainText(
    airBikeRecord.instructions.at(-1),
  );
  await expect(page.locator("#exercisePickerPreviewContent")).toContainText(
    "4 more steps available in the Guide",
  );
  await expect(page.locator("#exercisePickerPreviewContent a")).toHaveAttribute(
    "href",
    "https://github.com/yuhonas/free-exercise-db",
  );
  expect(
    await page.locator("#exercisePickerPreviewContent").evaluate((element) => ({
      overflowY:
        element.ownerDocument.defaultView.getComputedStyle(element).overflowY,
      bodyOverflow: element.ownerDocument.defaultView.getComputedStyle(
        element.ownerDocument.body,
      ).overflow,
    })),
  ).toEqual({ overflowY: "auto", bodyOverflow: "hidden" });

  await page.locator("#exercisePickerPreviewBack").click();
  await expect(page.locator("#exercisePickerBrowse")).toBeVisible();
  await expect(catalogResult).toBeVisible();
  await catalogResult.click();

  await page.locator("#exercisePickerPreviewAdd").click();
  await expect(page.locator("#exercisePicker")).toBeHidden();
  await expect(exercises).toHaveCount(initialCount + 1);
  await expect(exercises.last().locator(".exercise-name")).toHaveValue(
    "Air Bike",
  );
  const persistedExercise = await page.evaluate(() => {
    const draft = JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
    return draft.exercises.at(-1);
  });
  expect(Object.keys(persistedExercise).sort()).toEqual([
    "name",
    "notes",
    "sets",
  ]);
  expect(persistedExercise.name).toBe("Air Bike");

  await exercises.last().locator(".guide-row").click();
  await expect(page.locator("#exerciseDetailNotes")).toHaveValue("");
  await page.locator("#exerciseDetailGuideTab").click();
  const catalogGuide = page.locator('[data-guide-source="catalog"]');
  await expect(catalogGuide).toBeVisible();
  await expect(
    catalogGuide.getByRole("heading", { name: "How to perform it" }),
  ).toBeVisible();
  await expect(catalogGuide.locator("ol.guide-list li")).toHaveCount(
    airBikeRecord.instructions.length,
  );
  await expect(catalogGuide).toContainText(airBikeRecord.instructions[0]);
  await expect(
    catalogGuide.locator(".exercise-guide-attribution"),
  ).toContainText("Exercise information from Free Exercise DB");
  await expect(page.locator("#exerciseDetailNotes")).toHaveCount(0);
  await page.locator("#exerciseDetailLogTab").click();
  await expect(page.locator("#exerciseDetailNotes")).toHaveValue("");
  await page.locator("#exerciseDetailBack").click();

  await page.reload();
  await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
    "Resume",
  );
  await page.locator("#todayStartWorkout").click({ force: true });
  await expect(page.locator(".exercise-name").last()).toHaveValue("Air Bike");
  expect(requests.every((url) => url.startsWith("http://127.0.0.1:4175"))).toBe(
    true,
  );
  assertNoRuntimeErrors();
});

test("mobile picker keeps filters compact and catalog results immediately reachable", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);
  await page.locator("#addExercise").click();

  const picker = page.locator("#exercisePicker");
  const search = page.locator("#exercisePickerSearch");
  const filterToggle = page.locator("#exercisePickerFilterToggle");
  const filters = page.locator("#exercisePickerFilters");
  const options = page.locator("#exercisePickerOptions");
  await expect(search).toBeVisible();
  await expect(search).toBeFocused();
  await expect(filterToggle).toHaveAttribute("aria-expanded", "false");
  await expect(filters).toBeHidden();
  await expect(page.locator(".exercise-picker-option.is-local")).toHaveCount(3);
  await expect(page.locator("[data-show-all-local]")).toBeVisible();
  await expect(page.locator("#exercisePickerCreateAction")).toBeVisible();
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "873 offline",
  );

  const compactLayout = await picker.evaluate((dialog) => {
    const searchInput = dialog.querySelector("#exercisePickerSearch");
    const filterButton = dialog.querySelector("#exercisePickerFilterToggle");
    const results = dialog.querySelector("#exercisePickerOptions");
    return {
      dialogWidth: dialog.clientWidth,
      dialogScrollWidth: dialog.scrollWidth,
      searchHeight: searchInput.getBoundingClientRect().height,
      filterHeight: filterButton.getBoundingClientRect().height,
      dialogTop: dialog.getBoundingClientRect().top,
      resultsTop: results.getBoundingClientRect().top,
      viewportWidth: dialog.ownerDocument.defaultView.innerWidth,
      documentWidth: dialog.ownerDocument.documentElement.scrollWidth,
    };
  });
  expect(compactLayout.searchHeight).toBeGreaterThanOrEqual(48);
  expect(compactLayout.filterHeight).toBeGreaterThanOrEqual(44);
  expect(compactLayout.resultsTop - compactLayout.dialogTop).toBeLessThan(260);
  expect(compactLayout.dialogScrollWidth).toBeLessThanOrEqual(
    compactLayout.dialogWidth + 1,
  );
  expect(compactLayout.documentWidth).toBeLessThanOrEqual(
    compactLayout.viewportWidth + 1,
  );

  await filterToggle.focus();
  await page.keyboard.press("Enter");
  await expect(filterToggle).toHaveAttribute("aria-expanded", "true");
  await expect(filters).toBeVisible();
  await expect(filterToggle).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.locator("#exercisePickerMuscleFilter")).toBeFocused();
  await page.locator("#exercisePickerMuscleFilter").selectOption("abdominals");
  await expect(filterToggle).toHaveAttribute("aria-label", "Filters, 1 active");
  await expect(page.locator("#exercisePickerFilterCount")).toHaveText("1");
  await page.locator("#exercisePickerResetFilters").click();
  await expect(filterToggle).toHaveAttribute("aria-label", "Filters");
  await expect(page.locator("#exercisePickerMuscleFilter")).toHaveValue("");
  await filterToggle.focus();
  await page.keyboard.press("Space");
  await expect(filters).toBeHidden();

  await search.fill("Romanian Deadlift");
  await expect(
    page.getByRole("button", { name: "Romanian Deadlift", exact: true }),
  ).toBeVisible();
  await expect(
    page.locator('[data-catalog-id="free-exercise-db:Romanian_Deadlift"]'),
  ).toHaveCount(0);

  await search.fill("Air Bike");
  const catalogResult = page.locator(
    '[data-catalog-id="free-exercise-db:Air_Bike"]',
  );
  await expect(catalogResult).toBeVisible();
  const catalogAccess = await options.evaluate((container) => {
    const result = container.querySelector("[data-catalog-id]");
    const bounds = container.getBoundingClientRect();
    const resultBounds = result.getBoundingClientRect();
    return {
      scrollTop: container.scrollTop,
      resultTop: resultBounds.top,
      resultBottom: resultBounds.bottom,
      viewportTop: bounds.top,
      viewportBottom: bounds.bottom,
    };
  });
  expect(catalogAccess.scrollTop).toBe(0);
  expect(catalogAccess.resultTop).toBeGreaterThanOrEqual(
    catalogAccess.viewportTop - 1,
  );
  expect(catalogAccess.resultBottom).toBeLessThanOrEqual(
    catalogAccess.viewportBottom + 1,
  );

  await page.keyboard.press("Escape");
  await expect(picker).toBeHidden();
  await expect(page.locator("#addExercise")).toBeFocused();
  assertNoRuntimeErrors();
});

test("desktop picker and catalog Guide keep the responsive dialog layout", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.setViewportSize({ width: 1280, height: 800 });
  await loadApp(page);
  await startRoutine(page);
  await page.locator("#addExercise").click();
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "873 offline",
  );

  const picker = page.locator("#exercisePicker");
  const dialogBounds = await picker.boundingBox();
  expect(dialogBounds.width).toBeLessThanOrEqual(522);
  expect(dialogBounds.x).toBeGreaterThan(300);
  expect(dialogBounds.y).toBeGreaterThan(0);
  await page.locator("#exercisePickerFilterToggle").click();
  const filterTops = await page
    .locator("#exercisePickerFilters label")
    .evaluateAll((labels) =>
      labels.map((label) => Math.round(label.getBoundingClientRect().top)),
    );
  expect(new Set(filterTops).size).toBe(1);

  await page.locator("#exercisePickerSearch").fill("Air Bike");
  await page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]').click();
  await page.locator("#exercisePickerPreviewAdd").click();
  await page.locator(".exercise").last().locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  await expect(page.locator('[data-guide-source="catalog"]')).toBeVisible();
  expect(
    await page.locator("html").evaluate((element) => ({
      viewportWidth: element.ownerDocument.defaultView.innerWidth,
      documentWidth: element.ownerDocument.documentElement.scrollWidth,
    })),
  ).toEqual({ viewportWidth: 1280, documentWidth: 1280 });
  assertNoRuntimeErrors();
});

test("an existing routine exercise receives an exact catalog guide without renaming", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page, "Legs");

  const exercise = page.locator(".exercise").nth(1);
  await expect(exercise.locator(".exercise-name")).toHaveValue(
    "Romanian Deadlift",
  );
  await exercise.locator(".exercise-top").click();
  await exercise.locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  const guide = page.locator('[data-guide-source="catalog"]');
  await expect(guide).toBeVisible();
  await expect(guide.locator(".exercise-guide-overview h3")).toHaveText(
    "Romanian Deadlift",
  );
  await expect(guide.locator("ol.guide-list li")).not.toHaveCount(0);
  await expect(guide.locator(".exercise-guide-attribution")).toContainText(
    "Free Exercise DB",
  );
  await expect(page.locator("#exerciseDetailTitle")).toHaveText(
    "Romanian Deadlift",
  );
  expect(
    await page.evaluate(() => {
      const draft = JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
      return draft.exercises[1].name;
    }),
  ).toBe("Romanian Deadlift");
  assertNoRuntimeErrors();
});

test("a reviewed alias enriches a recovered draft while preserving its saved name", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page, "Back / Biceps");
  await page.reload();
  await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
    "Resume",
  );
  await page.locator("#todayStartWorkout").click({ force: true });

  const exercise = page.locator(".exercise").first();
  await expect(exercise.locator(".exercise-name")).toHaveValue(
    "V-Bar Lat Pulldown",
  );
  await exercise.locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  const guide = page.locator('[data-guide-source="catalog"]');
  await expect(guide).toBeVisible();
  await expect(guide.locator(".exercise-guide-match-note")).toHaveText(
    "Instructions matched to V-Bar Pulldown.",
  );
  await expect(page.locator("#exerciseDetailTitle")).toHaveText(
    "V-Bar Lat Pulldown",
  );
  expect(
    await page.evaluate(() => {
      const draft = JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
      return draft.exercises[0].name;
    }),
  ).toBe("V-Bar Lat Pulldown");
  assertNoRuntimeErrors();
});

test("a completed workout gains an exact guide only while displayed for editing", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  const completedWorkout = workoutFixture(1, {
    id: "catalog-history-workout",
    exercises: [
      {
        name: "Romanian Deadlift",
        notes: "Historical note stays local",
        sets: [
          {
            weight: "185",
            reps: "8",
            rpe: "8",
            done: true,
            warmup: false,
          },
        ],
      },
    ],
  });
  await seedStores(page, { workouts: [completedWorkout] });
  await openPrimary(page, "stats");
  await page.locator('[data-stats-detail="history"]').click();
  await page
    .locator(
      '[data-history-action="edit"][data-workout-id="catalog-history-workout"]',
    )
    .click();

  const exercise = page.locator(".exercise").first();
  await expect(exercise.locator(".exercise-name")).toHaveValue(
    "Romanian Deadlift",
  );
  await exercise.locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  await expect(page.locator('[data-guide-source="catalog"]')).toBeVisible();
  await expect(page.locator(".exercise-guide-attribution")).toContainText(
    "Free Exercise DB",
  );

  const storedWorkouts = await readStore(page, "workouts");
  expect(storedWorkouts[0].exercises[0]).toEqual(completedWorkout.exercises[0]);
  assertNoRuntimeErrors();
});

test("custom and broad related names keep the generic guide fallback", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await startRoutine(page);

  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerCreateAction").click();
  await page.locator("#exercisePickerCustomName").fill("My Saturday Row");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  let exercise = page.locator(".exercise").last();
  await exercise.locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  await expect(page.locator('[data-guide-source="generic"]')).toBeVisible();
  await expect(page.locator(".exercise-guide-attribution")).toHaveCount(0);
  await expect(page.locator("#exerciseDetailTitle")).toHaveText(
    "My Saturday Row",
  );
  await page.locator("#exerciseDetailBack").click();

  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerCreateAction").click();
  await page.locator("#exercisePickerCustomName").fill("Bench Press");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  exercise = page.locator(".exercise").last();
  await exercise.locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  const broadGuide = page.locator('[data-guide-source="generic"]');
  await expect(broadGuide).toBeVisible();
  await expect(broadGuide).not.toContainText("Incline Bench Press");
  await expect(page.locator(".exercise-guide-attribution")).toHaveCount(0);
  expect(
    await page.evaluate(() => {
      const draft = JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
      return draft.exercises.slice(-2).map((item) => item.name);
    }),
  ).toEqual(["My Saturday Row", "Bench Press"]);
  assertNoRuntimeErrors();
});

test("one malformed catalog record does not hide usable records", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  const catalog = JSON.parse(await readFile(catalogPath, "utf8"));
  const airBike = catalog.exercises.find(
    (exercise) => exercise.catalogId === "free-exercise-db:Air_Bike",
  );
  catalog.exercises = [airBike, { source: "free-exercise-db" }];
  catalog.metadata.exerciseCount = 2;
  await page.route("**/src/data/exercise-catalog.json", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(catalog),
    }),
  );
  await loadApp(page);
  await startRoutine(page);
  await page.locator("#addExercise").click();
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "1 offline",
  );
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "1 skipped",
  );
  await page.locator("#exercisePickerSearch").fill("Air Bike");
  await expect(
    page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]'),
  ).toBeVisible();
  assertNoRuntimeErrors();
});

test("catalog failure leaves local and custom exercise flows usable", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.route("**/src/data/exercise-catalog.json", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ invalidCatalog: true }),
    }),
  );
  await loadApp(page);
  await startRoutine(page);

  const exercises = page.locator(".exercise");
  const initialCount = await exercises.count();
  await page.locator("#addExercise").click();
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "Catalog unavailable",
  );
  await page.locator("#exercisePickerSearch").fill("Romanian Deadlift");
  await page
    .getByRole("button", { name: "Romanian Deadlift", exact: true })
    .click();
  await expect(exercises).toHaveCount(initialCount + 1);

  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerCreateAction").click();
  await page
    .locator("#exercisePickerCustomName")
    .fill("Catalog-Free Custom Move");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  await expect(exercises).toHaveCount(initialCount + 2);
  await expect(exercises.last().locator(".exercise-name")).toHaveValue(
    "Catalog-Free Custom Move",
  );
  expect(
    await page.evaluate(() => {
      const draft = JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
      return draft.exercises.slice(-2).map((exercise) => exercise.name);
    }),
  ).toEqual(["Romanian Deadlift", "Catalog-Free Custom Move"]);
  await exercises.last().locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  await expect(page.locator('[data-guide-source="generic"]')).toBeVisible();
  await expect(page.locator(".exercise-guide-attribution")).toHaveCount(0);
  assertNoRuntimeErrors();
});
