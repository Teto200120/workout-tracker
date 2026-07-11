import { expect, test } from "@playwright/test";
import {
  loadApp,
  monitorRuntime,
  openPrimary,
  seedStores,
  workoutFixture,
} from "../helpers/app.js";

test("Stats and History handle about 200 workouts and filtering", async ({
  page,
}) => {
  test.setTimeout(60_000);
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  const workouts = Array.from({ length: 200 }, (_, index) =>
    workoutFixture(index),
  );
  await seedStores(page, { workouts, weights: [], templates: [] });

  await openPrimary(page, "stats");
  await expect(page.locator("#dashboardStats")).toContainText("200");
  await page.locator('[data-stats-detail="history"]').click();
  await expect(page.locator("#history")).toHaveClass(/active/);
  await expect(page.locator("#historyList .stats-session-card")).toHaveCount(
    200,
  );

  await page.locator("#exerciseSearch").fill("Unique Filter Exercise");
  await expect(page.locator("#historyList .stats-session-card")).toHaveCount(1);
  await expect(page.locator("#historyList")).toContainText(
    "Unique Filter Exercise",
  );
  assertNoRuntimeErrors();
});
