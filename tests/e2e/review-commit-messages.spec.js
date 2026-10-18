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
  await seedStores(page, { workouts: [workout] });
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

test("goal refresh failures acknowledge the saved goal", async ({ page }) => {
  await loadApp(page);
  const outcome = await page.evaluate(async () => {
    const progress = await import("/src/js/screens/progress.js");
    const local = await import("/src/js/storage/local.js");
    globalThis.document.querySelector("#weeklyGoal").value = "5";
    const original = globalThis.renderAll;
    globalThis.renderAll = () => {
      throw new Error("injected view failure");
    };
    try {
      return {
        saved: await progress.saveGoalsToStorage(),
        goal: local.getGoals().weeklyGoal,
        message: globalThis.document.querySelector("#toast").textContent,
      };
    } finally {
      globalThis.renderAll = original;
    }
  });
  expect(outcome.saved).toBe(true);
  expect(outcome.goal).toBe(5);
  expect(outcome.message).toContain(
    "Goals saved, but the screen could not refresh",
  );
});

test("settings application failures acknowledge saved and reset preferences", async ({
  page,
}) => {
  await loadApp(page);
  page.on("dialog", (dialog) => dialog.accept());
  const outcomes = await page.evaluate(async () => {
    const settings = await import("/src/js/core/settings.js");
    const local = await import("/src/js/storage/local.js");
    await settings.renderSettings();
    globalThis.document.querySelector("#settingsWeightJump").value = "3";
    const classes = globalThis.document.body.classList;
    const original = classes.toggle;
    classes.toggle = function (name, ...args) {
      if (name === "no-animations")
        throw new Error("injected settings view failure");
      return original.call(this, name, ...args);
    };
    try {
      const saved = await settings.saveSettingsFromForm();
      const savedValue = local.getAppSettings().defaultWeightJump;
      const savedMessage =
        globalThis.document.querySelector("#toast").textContent;
      const reset = await settings.resetAppSettings();
      return {
        saved,
        savedValue,
        savedMessage,
        reset,
        resetValue: local.getAppSettings().defaultWeightJump,
        resetMessage: globalThis.document.querySelector("#toast").textContent,
      };
    } finally {
      classes.toggle = original;
    }
  });
  expect(outcomes.saved).toBe(true);
  expect(outcomes.savedValue).toBe(3);
  expect(outcomes.savedMessage).toContain(
    "Settings saved, but the screen could not refresh",
  );
  expect(outcomes.reset).toBe(true);
  expect(outcomes.resetValue).not.toBe(3);
  expect(outcomes.resetMessage).toContain(
    "Settings reset, but the screen could not refresh",
  );
});

test("a completed import is acknowledged when the view fails", async ({
  page,
}) => {
  await loadApp(page);
  page.on("dialog", (dialog) => dialog.accept());
  const record = workoutFixture(0, { id: "committed-import" });
  const message = await page.evaluate(async (workout) => {
    const application = await import("/src/js/application/backup.js");
    const screen = await import("/src/js/screens/backup.js");
    const backup = await application.buildBackup(new Date().toISOString());
    backup.workouts = [workout];
    const original = globalThis.renderAll;
    globalThis.renderAll = () => {
      throw new Error("injected view failure");
    };
    try {
      await screen.importData(
        new File([JSON.stringify(backup)], "backup.json"),
      );
      return globalThis.document.querySelector("#toast").textContent;
    } finally {
      globalThis.renderAll = original;
    }
  }, record);
  expect(message).toContain(
    "Backup imported, but the screen could not refresh",
  );
  expect(
    (await readStore(page, "workouts")).some((item) => item.id === record.id),
  ).toBe(true);
});

for (const failure of ["metadata", "view"]) {
  test(`backup download survives ${failure} failure`, async ({ page }) => {
    await loadApp(page);
    const download = page.waitForEvent("download");
    const message = await page.evaluate(async (stage) => {
      const backup = await import("/src/js/screens/backup.js");
      const originalSet = Storage.prototype.setItem;
      const originalLookup = globalThis.$;
      if (stage === "metadata") {
        Storage.prototype.setItem = () => {
          throw new Error("injected metadata failure");
        };
      } else {
        globalThis.$ = (id) => {
          if (id === "backupStatusMore")
            throw new Error("injected backup view failure");
          return originalLookup(id);
        };
      }
      try {
        await backup.exportData();
        return globalThis.document.querySelector("#toast").textContent;
      } finally {
        Storage.prototype.setItem = originalSet;
        globalThis.$ = originalLookup;
      }
    }, failure);
    expect((await download).suggestedFilename()).toMatch(
      /workout-tracker-backup/,
    );
    expect(message).toContain("Backup download started");
    expect(message).toContain(
      failure === "metadata"
        ? "reminder could not be updated"
        : "screen could not refresh",
    );
  });
}
