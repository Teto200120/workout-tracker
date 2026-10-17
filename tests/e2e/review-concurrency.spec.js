import { canonicalDraft } from "../fixtures/schema-data.js";
import { expect, test } from "@playwright/test";
import { loadApp, readStore, workoutFixture } from "../helpers/app.js";

test("ordinary writes in another tab wait through maintenance compensation", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const other = await context.newPage();
  await loadApp(other);
  const workout = workoutFixture(0, { id: "queued-workout" });
  try {
    await page.evaluate(async () => {
      const { runMaintenanceOperation } =
        await import("/src/js/application/maintenance.js");
      const { getAllApplicationRecords, replaceApplicationRecords } =
        await import("/src/js/storage/indexed-db.js");
      globalThis.__maintenance = runMaintenanceOperation(async (operation) => {
        const original = await getAllApplicationRecords();
        await replaceApplicationRecords(
          { workouts: [], legacyWeights: [], routines: [] },
          { operation },
        );
        await new Promise((release) => {
          globalThis.__release = release;
        });
        await replaceApplicationRecords(original, { operation });
      });
    });
    await expect
      .poll(() => page.evaluate(() => Boolean(globalThis.__release)))
      .toBe(true);
    await other.evaluate(async (record) => {
      const { saveWorkoutRecord } =
        await import("/src/js/storage/indexed-db.js");
      globalThis.__writeStatus = "waiting";
      globalThis.__write = saveWorkoutRecord(record).then(() => {
        globalThis.__writeStatus = "saved";
      });
    }, workout);
    expect(await other.evaluate(() => globalThis.__writeStatus)).toBe(
      "waiting",
    );
    await page.evaluate(async () => {
      globalThis.__release();
      await globalThis.__maintenance;
    });
    await other.evaluate(() => globalThis.__write);
    expect(await readStore(other, "workouts")).toContainEqual(workout);
  } finally {
    await page.evaluate(() => globalThis.__release?.());
    await other.close();
  }
});

test("preference and draft writes wait through another tab's local rollback", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const other = await context.newPage();
  await loadApp(other);
  try {
    await page.evaluate(async () => {
      const { runMaintenanceOperation } =
        await import("/src/js/application/maintenance.js");
      const local = await import("/src/js/storage/local.js");
      globalThis.__maintenance = runMaintenanceOperation(async (operation) => {
        const before = local.captureApplicationLocalStorage();
        await local.setGoals({ weeklyGoal: 1 }, { operation });
        await new Promise((release) => {
          globalThis.__release = release;
        });
        await local.restoreApplicationLocalStorage(before, { operation });
      });
    });
    await expect
      .poll(() => page.evaluate(() => Boolean(globalThis.__release)))
      .toBe(true);
    await other.evaluate(
      async (draft) => {
        const local = await import("/src/js/storage/local.js");
        globalThis.__localStatus = "waiting";
        globalThis.__localWrites = Promise.all([
          local.setGoals({ weeklyGoal: 6 }),
          local.setDraft(draft),
        ]).then(() => {
          globalThis.__localStatus = "saved";
        });
      },
      canonicalDraft({ id: "queued-draft" }),
    );
    expect(await other.evaluate(() => globalThis.__localStatus)).toBe(
      "waiting",
    );
    await page.evaluate(async () => {
      globalThis.__release();
      await globalThis.__maintenance;
    });
    await other.evaluate(() => globalThis.__localWrites);
    expect(
      await other.evaluate(async () => {
        const local = await import("/src/js/storage/local.js");
        return {
          goal: local.getGoals().weeklyGoal,
          draft: local.getDraft().id,
        };
      }),
    ).toEqual({ goal: 6, draft: "queued-draft" });
  } finally {
    await page.evaluate(() => globalThis.__release?.());
    await other.close();
  }
});
