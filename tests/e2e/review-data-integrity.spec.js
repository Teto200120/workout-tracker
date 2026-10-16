import { expect, test } from "@playwright/test";
import {
  loadApp,
  monitorRuntime,
  openPrimary,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

async function openHistory(page) {
  await openPrimary(page, "stats");
  await page.locator('[data-stats-detail="history"]').click();
}

for (const id of [0, 123, "123"]) {
  test(`history edit preserves metadata and typed key ${typeof id}:${id}`, async ({
    page,
  }) => {
    const assertHealthy = monitorRuntime(page);
    await loadApp(page);
    const original = workoutFixture(0, {
      id,
      type: "Retired routine",
      tags: ["Felt strong", "Legacy tag"],
      durationMinutes: 73,
      extension: { source: "old backup" },
    });
    original.exercises[0].extension = "exercise metadata";
    original.exercises[0].sets[0].extension = "set metadata";
    const sibling = workoutFixture(1, {
      id: typeof id === "number" ? String(id) : Number(id),
    });
    await seedStores(page, { workouts: [original, sibling] });
    page.on("dialog", (dialog) => dialog.accept());
    await openHistory(page);
    const key = `[data-workout-id="${id}"][data-workout-id-type="${typeof id}"]`;
    await page.locator(`[data-history-action="edit"]${key}`).click();
    await expect(page.locator("#workoutType")).toHaveValue("Retired routine");
    await page.locator("#workoutNotes").fill("Changed note only");
    // Exercise the actual persisted draft and module-state reset too.
    await page.reload();
    await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
      "Resume",
    );
    await page.locator("#todayStartWorkout").click({ force: true });
    await page.locator("#sessionSaveTop").click();
    await expect(page.locator("#completionModal")).toBeVisible();
    await page.locator("#completionDone").click();
    await expect(page.locator("#completionModal")).toBeHidden();
    const saved = await readStore(page, "workouts");
    expect(saved).toHaveLength(2);
    expect(saved.find((record) => record.id === id)).toEqual({
      ...original,
      notes: "Changed note only",
    });
    expect(saved.find((record) => record.id === sibling.id)).toEqual(sibling);
    await openHistory(page);
    await page.locator(`[data-history-action="delete"]${key}`).click();
    await expect
      .poll(async () => (await readStore(page, "workouts")).length)
      .toBe(1);
    expect(await readStore(page, "workouts")).toEqual([sibling]);
    assertHealthy();
  });
}

test("numeric routine zero can start, edit and delete without touching its string sibling", async ({
  page,
}) => {
  await loadApp(page);
  const routine = {
    id: 0,
    name: "Numeric routine",
    exercises: ["Flat Bench Press"],
    createdAt: "2026-06-01T00:00:00.000Z",
    updatedAt: "2026-06-01T00:00:00.000Z",
  };
  const sibling = { ...routine, id: "0", name: "String routine" };
  await seedStores(page, { templates: [routine, sibling] });
  const openRoutines = async () => {
    await openPrimary(page, "profile");
    await page.locator('[data-profile-target="templates"]').click();
  };
  const button = (action) =>
    page.locator(
      `[data-routine-action="${action}"][data-template-id="0"][data-template-id-type="number"]`,
    );
  await openRoutines();
  await button("start").click();
  await expect(page.locator("#workoutType")).toHaveValue(routine.name);
  await openRoutines();
  await button("edit").click();
  await expect(page.locator("#templateName")).toHaveValue(routine.name);
  await page.locator("#templateName").fill("Renamed numeric routine");
  await page.locator("#saveTemplate").click();
  await expect
    .poll(
      async () =>
        (await readStore(page, "templates")).find((row) => row.id === 0)?.name,
    )
    .toBe("Renamed numeric routine");
  page.once("dialog", (dialog) => dialog.accept());
  await button("delete").click();
  await expect
    .poll(async () => (await readStore(page, "templates")).length)
    .toBe(1);
  expect(await readStore(page, "templates")).toEqual([sibling]);
});

test("late IndexedDB abort rejects a workout save and retains its draft", async ({
  page,
}) => {
  await loadApp(page);
  const result = await page.evaluate(async (workout) => {
    const storage = await import("/src/js/storage/indexed-db.js");
    const local = await import("/src/js/storage/local.js");
    const draft = {
      ...workout,
      editingWorkoutId: null,
      activeExerciseIndex: 0,
      savedAt: workout.createdAt,
    };
    local.setDraft(draft);
    const put = IDBObjectStore.prototype.put;
    IDBObjectStore.prototype.put = function (...args) {
      const request = put.apply(this, args);
      if (this.name === "workouts")
        request.addEventListener("success", () => this.transaction.abort());
      return request;
    };
    try {
      let rejected = false;
      try {
        await storage.saveWorkoutRecord(workout);
      } catch {
        rejected = true;
      }
      return {
        rejected,
        records: await storage.getWorkouts(),
        draft: local.getDraft(),
      };
    } finally {
      IDBObjectStore.prototype.put = put;
    }
  }, workoutFixture(0));
  expect(result.rejected).toBe(true);
  expect(result.records).toEqual([]);
  expect(result.draft.id).toBe("test-workout-0");
});
