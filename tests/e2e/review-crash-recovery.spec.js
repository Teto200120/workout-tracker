import { expect, test } from "@playwright/test";
import {
  loadApp,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

async function interruptClear(page, phase) {
  await page.evaluate(async (phase) => {
    const { clearApplicationData } =
      await import("/src/js/application/backup.js");
    const descriptor = Object.getOwnPropertyDescriptor(
      globalThis.IDBTransaction.prototype,
      "oncomplete",
    );
    let applicationWrites = 0;
    Object.defineProperty(globalThis.IDBTransaction.prototype, "oncomplete", {
      ...descriptor,
      set(handler) {
        const applicationWrite =
          this.mode === "readwrite" &&
          Array.from(this.objectStoreNames).includes("workouts");
        const number = applicationWrite ? ++applicationWrites : 0;
        descriptor.set.call(this, function (event) {
          if (applicationWrite && number === (phase === "local" ? 2 : 1)) {
            // The transaction really committed. Suspend the application before
            // it can continue or compensate, then terminate this tab from Node.
            globalThis.__crashPoint = true;
            return;
          }
          handler?.call(this, event);
        });
      },
    });
    if (phase === "local") {
      const original = globalThis.Storage.prototype.removeItem;
      globalThis.Storage.prototype.removeItem = function (key) {
        if (key === "hector_workout_settings_v1")
          throw new Error("interrupted partial local clear");
        return original.call(this, key);
      };
    }
    globalThis.__clear = clearApplicationData().catch((error) => {
      globalThis.__unexpectedClearError = error.message;
    });
  }, phase);
  await expect
    .poll(() => page.evaluate(() => Boolean(globalThis.__crashPoint)))
    .toBe(true);
}

for (const phase of ["database", "local"]) {
  test(`a terminated tab recovers the exact snapshot after the ${phase} stage`, async ({
    page,
    context,
  }) => {
    await loadApp(page);
    await seedStores(page, { workouts: [workoutFixture(0)] });
    const before = await page.evaluate(async () => {
      const local = await import("/src/js/storage/local.js");
      await local.setGoals({ weeklyGoal: 5 });
      return local.captureApplicationLocalStorage();
    });
    const workouts = await readStore(page, "workouts");
    await interruptClear(page, phase);
    await page.close();
    const resumed = await context.newPage();
    await loadApp(resumed);
    expect(await readStore(resumed, "workouts")).toEqual(workouts);
    const after = await resumed.evaluate(async () => {
      const local = await import("/src/js/storage/local.js");
      const { readRecoverySnapshot } =
        await import("/src/js/storage/indexed-db.js");
      return {
        local: local.captureApplicationLocalStorage(),
        journal: await readRecoverySnapshot(),
      };
    });
    expect(after.local).toEqual(before);
    expect(after.journal).toBeNull();
  });
}

test("a failed recovery blocks later writes, keeps the journal, and succeeds on retry", async ({
  page,
  context,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  await interruptClear(page, "database");
  await page.close();
  const resumed = await context.newPage();
  await resumed.addInitScript(() => {
    const original = globalThis.Storage.prototype.setItem;
    globalThis.Storage.prototype.setItem = function (key, value) {
      if (key === "hector_workout_settings_v1" && !globalThis.__allowRecovery)
        throw new Error("recovery temporarily unavailable");
      return original.call(this, key, value);
    };
  });
  await resumed.goto("/");
  await expect(
    resumed.getByRole("button", { name: "Download recovery data" }),
  ).toBeVisible();
  const blocked = await resumed.evaluate(async (workout) => {
    const storage = await import("/src/js/storage/indexed-db.js");
    let code;
    try {
      await storage.saveWorkoutRecord(workout);
    } catch (error) {
      code = error.code;
    }
    return {
      code,
      journal: Boolean(await storage.readRecoverySnapshot()),
      records: await storage.getWorkouts(),
    };
  }, workoutFixture(1));
  expect(blocked.code).toBe("recovery_required");
  expect(blocked.journal).toBe(true);
  expect(blocked.records).toHaveLength(1);
  await resumed.evaluate(async () => {
    globalThis.__allowRecovery = true;
    const { ensureCurrentApplicationSchema } =
      await import("/src/js/application/data-schema.js");
    await ensureCurrentApplicationSchema();
  });
  expect(
    await resumed.evaluate(async () => {
      const { readRecoverySnapshot } =
        await import("/src/js/storage/indexed-db.js");
      return readRecoverySnapshot();
    }),
  ).toBeNull();
});

test("a recovery-snapshot write failure prevents all clear mutations", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  const before = await readStore(page, "workouts");
  const outcome = await page.evaluate(async () => {
    const { clearApplicationData } =
      await import("/src/js/application/backup.js");
    const local = await import("/src/js/storage/local.js");
    const original = globalThis.IDBObjectStore.prototype.put;
    const before = local.captureApplicationLocalStorage();
    globalThis.IDBObjectStore.prototype.put = function (...args) {
      if (this.name === "recovery")
        throw new Error("snapshot storage unavailable");
      return original.apply(this, args);
    };
    let failed = false;
    try {
      await clearApplicationData();
    } catch {
      failed = true;
    } finally {
      globalThis.IDBObjectStore.prototype.put = original;
    }
    return { failed, before, after: local.captureApplicationLocalStorage() };
  });
  expect(outcome.failed).toBe(true);
  expect(outcome.after).toEqual(outcome.before);
  expect(await readStore(page, "workouts")).toEqual(before);
});
