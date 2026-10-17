import { expect, test } from "@playwright/test";
import {
  loadApp,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

test("routine reset rolls back the complete library after a middle write fails", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  const before = await readStore(page, "templates");
  const result = await page.evaluate(async () => {
    const { resetDefaultTemplates } =
      await import("/src/js/application/routine-seeding.js");
    const original = globalThis.IDBObjectStore.prototype.put;
    let writes = 0;
    globalThis.IDBObjectStore.prototype.put = function (...args) {
      if (this.name === "templates" && ++writes === 2)
        throw new Error("injected second write failure");
      return original.apply(this, args);
    };
    try {
      try {
        await resetDefaultTemplates();
        return false;
      } catch {
        return true;
      }
    } finally {
      globalThis.IDBObjectStore.prototype.put = original;
    }
  });
  expect(result).toBe(true);
  expect(await readStore(page, "templates")).toEqual(before);
  expect(await readStore(page, "workouts")).toHaveLength(1);
});

test("unavailable catalog cannot replace an existing routine library", async ({
  page,
}) => {
  await loadApp(page);
  const before = await readStore(page, "templates");
  await page.route("**/src/data/exercise-catalog.json", (route) =>
    route.abort(),
  );
  const failed = await page.evaluate(async () => {
    const { resetCatalogStateForTests } =
      await import("/src/js/catalog/catalog-loader.js");
    const { resetDefaultTemplates } =
      await import("/src/js/application/routine-seeding.js");
    resetCatalogStateForTests();
    try {
      await resetDefaultTemplates();
      return false;
    } catch {
      return true;
    }
  });
  expect(failed).toBe(true);
  expect(await readStore(page, "templates")).toEqual(before);
});

test("catalog fallback retries and concurrent seeding preserve custom routines", async ({
  page,
}) => {
  await page.route("**/src/data/exercise-catalog.json", (route) =>
    route.abort(),
  );
  await loadApp(page);
  const fallback = await readStore(page, "templates");
  expect(fallback).toHaveLength(1);
  expect(fallback[0]).toMatchObject({ name: "Custom", starterSeedPending: 1 });
  const custom = {
    id: 0,
    name: "My own plan",
    exercises: ["Custom exercise"],
    createdAt: "2026-06-01T00:00:00.000Z",
    updatedAt: "2026-06-01T00:00:00.000Z",
  };
  await page.evaluate(async (routine) => {
    const { saveRoutine } = await import("/src/js/storage/indexed-db.js");
    await saveRoutine(routine);
  }, custom);
  await page.unroute("**/src/data/exercise-catalog.json");
  await page.evaluate(async () => {
    const { seedDefaultTemplates } =
      await import("/src/js/application/routine-seeding.js");
    await Promise.all([seedDefaultTemplates(), seedDefaultTemplates()]);
  });
  const recovered = await readStore(page, "templates");
  expect(recovered.find((routine) => routine.id === 0)).toEqual(custom);
  expect(recovered.filter((routine) => routine.name === "Custom")).toHaveLength(
    1,
  );
  expect(recovered.find((routine) => routine.name === "Custom").id).toBe(
    fallback[0].id,
  );
  expect(recovered.some((routine) => routine.name === "Legs")).toBe(true);
  expect(recovered.some((routine) => routine.starterSeedPending)).toBe(false);
  expect(new Set(recovered.map((routine) => routine.id)).size).toBe(
    recovered.length,
  );
});

test("clear compensates database and local data when a later removal fails", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  const before = await page.evaluate(async () => {
    const { getAllApplicationRecords } =
      await import("/src/js/storage/indexed-db.js");
    const { captureApplicationLocalStorage, getRawEducationRecord } =
      await import("/src/js/storage/local.js");
    return {
      records: await getAllApplicationRecords(),
      local: captureApplicationLocalStorage(),
      education: getRawEducationRecord(),
    };
  });
  const result = await page.evaluate(async () => {
    const { clearApplicationData } =
      await import("/src/js/application/backup.js");
    const original = Storage.prototype.removeItem;
    let injected = false;
    Storage.prototype.removeItem = function (key) {
      if (key === "hector_workout_data_schema_version" && !injected) {
        injected = true;
        throw new Error("injected removal failure");
      }
      return original.call(this, key);
    };
    try {
      try {
        await clearApplicationData();
        return null;
      } catch (error) {
        return {
          code: error.code,
          rollbackErrors: error.rollbackErrors.length,
        };
      }
    } finally {
      Storage.prototype.removeItem = original;
    }
  });
  expect(result).toEqual({ code: "data_clear_failed", rollbackErrors: 0 });
  const after = await page.evaluate(async () => {
    const { getAllApplicationRecords } =
      await import("/src/js/storage/indexed-db.js");
    const { captureApplicationLocalStorage, getRawEducationRecord } =
      await import("/src/js/storage/local.js");
    return {
      records: await getAllApplicationRecords(),
      local: captureApplicationLocalStorage(),
      education: getRawEducationRecord(),
    };
  });
  expect(after).toEqual(before);
});

test("backup export reads every record store in one readonly transaction", async ({
  page,
}) => {
  await loadApp(page);
  const scopes = await page.evaluate(async () => {
    const { buildBackup } = await import("/src/js/application/backup.js");
    const original = globalThis.IDBDatabase.prototype.transaction;
    const scopes = [];
    globalThis.IDBDatabase.prototype.transaction = function (
      stores,
      mode,
      ...rest
    ) {
      scopes.push({ stores: Array.from(stores), mode });
      return original.call(this, stores, mode, ...rest);
    };
    try {
      await buildBackup(new Date().toISOString());
    } finally {
      globalThis.IDBDatabase.prototype.transaction = original;
    }
    return scopes;
  });
  expect(scopes).toEqual([
    { stores: ["workouts", "weights", "templates"], mode: "readonly" },
  ]);
});

test("maintenance waits for a cooperating tab and continues after it releases the lock", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const other = await context.newPage();
  try {
    await loadApp(other);
    await page.evaluate(
      () =>
        new Promise((acquired) => {
          navigator.locks.request(
            "hector-workout-maintenance-v1",
            () =>
              new Promise((release) => {
                globalThis.__releaseMaintenanceLock = release;
                acquired();
              }),
          );
        }),
    );
    await other.evaluate(async () => {
      const { buildBackup } = await import("/src/js/application/backup.js");
      globalThis.__maintenanceResult = "waiting";
      buildBackup(new Date().toISOString()).then(() => {
        globalThis.__maintenanceResult = "completed";
      });
    });
    expect(await other.evaluate(() => globalThis.__maintenanceResult)).toBe(
      "waiting",
    );
    await page.evaluate(() => globalThis.__releaseMaintenanceLock());
    await expect
      .poll(() => other.evaluate(() => globalThis.__maintenanceResult))
      .toBe("completed");
  } finally {
    await page.evaluate(() => globalThis.__releaseMaintenanceLock?.());
    await other.close();
  }
});

test("committed resets and clears report view failures truthfully", async ({
  page,
}) => {
  await loadApp(page);
  page.on("dialog", (dialog) => dialog.accept());
  const messages = await page.evaluate(async () => {
    const { resetTemplates } = await import("/src/js/screens/routines.js");
    const { clearAllData } = await import("/src/js/screens/backup.js");
    const original = globalThis.renderAll;
    globalThis.renderAll = () => {
      throw new Error("injected render failure");
    };
    try {
      const reset = await resetTemplates();
      const resetMessage =
        globalThis.document.querySelector("#toast").textContent;
      await clearAllData();
      return {
        reset,
        resetMessage,
        clearMessage: globalThis.document.querySelector("#toast").textContent,
      };
    } finally {
      globalThis.renderAll = original;
    }
  });
  expect(messages.reset).toBe(true);
  expect(messages.resetMessage).toContain("Default routines restored, but");
  expect(messages.clearMessage).toContain("Local data was cleared, but");
});
