import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import {
  loadApp,
  readStore,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

async function downloadRescue(page) {
  const downloading = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download recovery data" }).click();
  const download = await downloading;
  return JSON.parse(await readFile(await download.path(), "utf8"));
}

test("startup rescue preserves malformed local JSON and raw records without migration", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, {
    workouts: [workoutFixture(0, { id: "rescue-record" })],
  });
  const before = await readStore(page, "workouts");
  await page.evaluate(() => {
    localStorage.setItem("hector_workout_settings_v1", "{broken JSON");
    localStorage.setItem("hector_workout_data_schema_version", "999");
  });
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Download recovery data" }),
  ).toBeVisible();
  const rescue = await downloadRescue(page);
  expect(rescue.kind).toBe("workout-tracker-raw-rescue");
  expect(rescue.localStorage.settings).toBe("{broken JSON");
  expect(rescue.localStorage.applicationSchemaVersion).toBe("999");
  expect(rescue.database.stores.workouts.values).toEqual(before);
  expect(await readStore(page, "workouts")).toEqual(before);
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_settings_v1"),
    ),
  ).toBe("{broken JSON");
});

test("rescue reads a newer database version and unknown stores without downgrading it", async ({
  page,
}) => {
  await loadApp(page);
  await page.evaluate(async () => {
    const { openDatabase } = await import("/src/js/storage/indexed-db.js");
    const connection = await openDatabase();
    connection.close();
    await new Promise((resolve, reject) => {
      const request = globalThis.indexedDB.open(
        "hector_workout_tracker_fresh_v1",
        3,
      );
      request.onupgradeneeded = () => {
        request.result
          .createObjectStore("futureRecords")
          .put({ extra: "preserve" }, "future-key");
      };
      request.onsuccess = () => {
        request.result.close();
        resolve();
      };
      request.onerror = () => reject(request.error);
    });
  });
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Download recovery data" }),
  ).toBeVisible();
  const rescue = await downloadRescue(page);
  expect(rescue.database.version).toBe(3);
  expect(rescue.database.stores.futureRecords).toEqual({
    keys: ["future-key"],
    values: [{ extra: "preserve" }],
  });
});
