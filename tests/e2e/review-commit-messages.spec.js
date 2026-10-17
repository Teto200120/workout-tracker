import { expect, test } from "@playwright/test";
import {
  loadApp,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

test("a committed workout reports a view failure and blocks duplicate retries", async ({
  page,
}) => {
  await loadApp(page);
  const workout = workoutFixture(0);
  page.on("dialog", (dialog) => dialog.accept());
  const result = await page.evaluate(async (record) => {
    const active = await import("/src/js/screens/active-workout.js");
    const doc = globalThis.document;
    active.setEditingWorkoutId(record.id);
    active.setEditingWorkoutRecord(record);
    doc.querySelector("#workoutDate").value = record.date;
    doc.querySelector("#workoutType").value = record.type;
    doc
      .querySelector("#exerciseList")
      .replaceChildren(active.makeExercise(record.exercises[0]));
    const original = globalThis.renderAll;
    globalThis.renderAll = () => {
      throw new Error("injected view failure");
    };
    try {
      const saved = await active.saveWorkout();
      const message = doc.querySelector("#toast").textContent;
      const retry = await active.saveWorkout();
      return { saved, message, retry };
    } finally {
      globalThis.renderAll = original;
    }
  }, workout);
  expect(result.saved).toBe(true);
  expect(result.message).toContain(
    "Workout saved, but the screen could not refresh",
  );
  expect(result.retry).toBe(false);
  expect(await readStore(page, "workouts")).toHaveLength(1);
});

test("routine save and routine/workout deletes acknowledge committed data after view failures", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  page.on("dialog", (dialog) => dialog.accept());
  const saved = await page.evaluate(async () => {
    const routines = await import("/src/js/screens/routines.js");
    const doc = globalThis.document;
    doc.querySelector("#templateName").value = "Committed Routine";
    doc.querySelector("#templateExerciseInput").value = "Flat Bench Press";
    routines.addTemplateExercise();
    const original = globalThis.renderAll;
    globalThis.renderAll = () => {
      throw new Error("injected view failure");
    };
    globalThis.__restoreRenderAll = () => {
      globalThis.renderAll = original;
    };
    return {
      result: await routines.saveTemplate(),
      message: doc.querySelector("#toast").textContent,
    };
  });
  expect(saved.result).toBe(true);
  expect(saved.message).toContain(
    "Routine saved, but the screen could not refresh",
  );
  const routines = await readStore(page, "templates");
  expect(routines.some((routine) => routine.name === "Committed Routine")).toBe(
    true,
  );
  // Buttons were rendered by the initial seed; exercise the actual delegated handlers.
  await page.evaluate(() =>
    globalThis.document.querySelector('[data-history-action="delete"]').click(),
  );
  await expect(page.locator("#toast")).toContainText(
    "Workout deleted, but the screen could not refresh",
  );
  expect(await readStore(page, "workouts")).toHaveLength(0);
  const before = (await readStore(page, "templates")).length;
  await page.evaluate(() =>
    globalThis.document.querySelector('[data-routine-action="delete"]').click(),
  );
  await expect(page.locator("#toast")).toContainText(
    "Routine deleted, but the screen could not refresh",
  );
  expect(await readStore(page, "templates")).toHaveLength(before - 1);
  await page.evaluate(() => globalThis.__restoreRenderAll());
});
