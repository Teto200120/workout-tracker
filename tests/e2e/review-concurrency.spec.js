import { expect, test } from "@playwright/test";
import { loadApp, readStore, workoutFixture } from "../helpers/app.js";

test("ordinary writes in another tab wait through maintenance compensation", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const other = await context.newPage();
  await loadApp(other);
  const workout = workoutFixture("queued-workout");
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
