import { expect, test } from "@playwright/test";
import { loadApp } from "../helpers/app.js";

test.use({ serviceWorkers: "allow" });

test("the installed shell boots offline and never caches external requests", async ({
  page,
  context,
}) => {
  await loadApp(page);
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await expect(page.locator("#appShell")).toBeVisible();
  await context.setOffline(true);
  await page.reload();
  await expect(page).toHaveTitle("Workout Tracker");
  await expect(page.locator("#appShell")).toBeVisible();
  await page.locator('[data-screen="profile"]').first().click();
  await expect(page.locator("#profile")).toBeVisible();
  const origins = await page.evaluate(async () => {
    const names = await globalThis.caches.keys();
    const shell = await globalThis.caches.open(
      names.find((name) => name.startsWith("hector-workout-tracker-pwa-")),
    );
    return (await shell.keys()).map((request) => new URL(request.url).origin);
  });
  expect(origins.length).toBeGreaterThan(60);
  expect(origins.every((origin) => origin === "http://127.0.0.1:4175")).toBe(
    true,
  );
  await context.setOffline(false);
});
