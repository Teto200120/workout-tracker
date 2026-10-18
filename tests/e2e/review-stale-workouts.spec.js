import { expect, test } from "@playwright/test";
import {
  loadApp,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

async function editWorkout(page, record) {
  await page.evaluate((id) => {
    const button = [
      ...globalThis.document.querySelectorAll('[data-history-action="edit"]'),
    ].find(
      (item) =>
        item.dataset.workoutId === String(id) &&
        item.dataset.workoutIdType === typeof id,
    );
    button.click();
  }, record.id);
  await expect(page.locator("#sessionView")).toBeVisible();
  await expect(page.locator("#workoutNotes")).toHaveValue(record.notes);
}

async function save(page) {
  return page.evaluate(async () =>
    (await import("/src/js/screens/active-workout.js")).saveWorkout(),
  );
}

for (const restored of [false, true]) {
  for (const change of ["updated", "deleted"]) {
    test(`${restored ? "restored" : "live"} workout edit cannot overwrite a ${change} record`, async ({
      page,
      context,
    }) => {
      await loadApp(page);
      const original = workoutFixture(0, { id: 0 });
      await seedStores(page, { workouts: [original] });
      await editWorkout(page, original);
      await page.locator("#workoutNotes").fill("My unsaved changes");
      await page.evaluate(async () =>
        (await import("/src/js/screens/active-workout.js")).saveDraftSilently(),
      );
      const other = await context.newPage();
      await loadApp(other);
      await other.evaluate(
        async ({ original, change }) => {
          const storage = await import("/src/js/storage/indexed-db.js");
          if (change === "deleted")
            await storage.deleteWorkoutRecord(original.id);
          else
            await storage.saveWorkoutRecord({
              ...original,
              notes: "Newer saved changes",
            });
        },
        { original, change },
      );
      if (restored) {
        await page.reload();
        await expect(
          page.locator("#todayStartWorkout .cta-label"),
        ).toContainText("Resume");
        expect(
          await page.evaluate(async () =>
            (
              await import("/src/js/screens/today.js")
            ).restoreDraftFromStorage(),
          ),
        ).toBe(true);
      }
      page.on("dialog", (dialog) => dialog.accept());
      expect(await save(page)).toBe(false);
      await expect(page.locator("#toast")).toContainText(
        "This workout changed or was deleted",
      );
      await expect(page.locator("#workoutNotes")).toHaveValue(
        "My unsaved changes",
      );
      const stored = (await readStore(page, "workouts")).find(
        (item) => item.id === original.id,
      );
      if (change === "deleted") expect(stored).toBeUndefined();
      else expect(stored.notes).toBe("Newer saved changes");
      const draft = await page.evaluate(async () =>
        (await import("/src/js/storage/local.js")).getDraft(),
      );
      expect(draft.editingWorkoutBase).toEqual(original);
      expect(draft.notes).toBe("My unsaved changes");
      await other.close();
    });
  }
}

test("stale History delete preserves a newer workout", async ({ page }) => {
  await loadApp(page);
  const original = workoutFixture(0);
  await seedStores(page, { workouts: [original] });
  page.on("dialog", (dialog) => dialog.accept());
  await page.evaluate(async (record) => {
    const storage = await import("/src/js/storage/indexed-db.js");
    await storage.saveWorkoutRecord({
      ...record,
      notes: "Changed after rendering",
    });
    globalThis.document.querySelector('[data-history-action="delete"]').click();
  }, original);
  await expect(page.locator("#toast")).toContainText(
    "Refresh History before deleting",
  );
  expect((await readStore(page, "workouts"))[0].notes).toBe(
    "Changed after rendering",
  );
});

test("older edit drafts remain available but cannot overwrite History without a base", async ({
  page,
}) => {
  await loadApp(page);
  const original = workoutFixture(0);
  await seedStores(page, { workouts: [original] });
  await page.evaluate(async (record) => {
    const local = await import("/src/js/storage/local.js");
    await local.setDraft({
      ...record,
      editingWorkoutId: record.id,
      activeExerciseIndex: 0,
      savedAt: new Date().toISOString(),
      notes: "Legacy unsaved changes",
    });
    await (await import("/src/js/screens/today.js")).restoreDraftFromStorage();
  }, original);
  expect(await save(page)).toBe(false);
  await expect(page.locator("#toast")).toContainText(
    "older edit draft has no original saved version",
  );
  await expect(page.locator("#workoutNotes")).toHaveValue(
    "Legacy unsaved changes",
  );
  expect(await readStore(page, "workouts")).toEqual([original]);
});

test("new draft IDs cannot replace an existing saved workout", async ({
  page,
}) => {
  await loadApp(page);
  const original = workoutFixture(0);
  await seedStores(page, { workouts: [original] });
  await page.evaluate(async (record) => {
    const local = await import("/src/js/storage/local.js");
    await local.setDraft({
      ...record,
      editingWorkoutId: null,
      activeExerciseIndex: 0,
      savedAt: new Date().toISOString(),
      notes: "Unsaved new workout",
    });
    await (await import("/src/js/screens/today.js")).restoreDraftFromStorage();
  }, original);
  page.on("dialog", (dialog) => dialog.accept());
  expect(await save(page)).toBe(false);
  await expect(page.locator("#toast")).toContainText(
    "This workout changed or was deleted",
  );
  expect(await readStore(page, "workouts")).toEqual([original]);
});

test("completion details cannot overwrite a workout changed after saving", async ({
  page,
}) => {
  await loadApp(page);
  const original = workoutFixture(0);
  await seedStores(page, { workouts: [original] });
  await editWorkout(page, original);
  page.on("dialog", (dialog) => dialog.accept());
  expect(await save(page)).toBe(true);
  await expect(page.locator("#completionModal")).toBeVisible();
  await page.evaluate(async () => {
    const storage = await import("/src/js/storage/indexed-db.js");
    const [record] = await storage.getWorkouts();
    await storage.saveWorkoutRecord({
      ...record,
      notes: "Changed while popup open",
    });
    await (
      await import("/src/js/screens/active-workout.js")
    ).finishCompletionPopup();
  });
  await expect(page.locator("#toast")).toContainText(
    "This workout changed or was deleted",
  );
  await expect(page.locator("#completionModal")).toBeVisible();
  expect((await readStore(page, "workouts"))[0].notes).toBe(
    "Changed while popup open",
  );
  await page.locator("#completionSkip").click();
  await expect(page.locator("#completionModal")).toBeHidden();
  expect((await readStore(page, "workouts"))[0].notes).toBe(
    "Changed while popup open",
  );
});

test("committed completion details are acknowledged after a refresh failure", async ({
  page,
}) => {
  await loadApp(page);
  const original = workoutFixture(0);
  await seedStores(page, { workouts: [original] });
  await editWorkout(page, original);
  page.on("dialog", (dialog) => dialog.accept());
  expect(await save(page)).toBe(true);
  const message = await page.evaluate(async () => {
    const active = await import("/src/js/screens/active-workout.js");
    const render = globalThis.renderAll;
    globalThis.renderAll = () => {
      throw new Error("injected completion refresh failure");
    };
    try {
      await active.finishCompletionPopup();
      return globalThis.document.querySelector("#toast").textContent;
    } finally {
      globalThis.renderAll = render;
    }
  });
  expect(message).toContain(
    "Workout details saved, but the screen could not refresh",
  );
  expect(await readStore(page, "workouts")).toHaveLength(1);
});
