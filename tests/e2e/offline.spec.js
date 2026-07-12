import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { monitorRuntime } from "../helpers/app.js";

test.use({ serviceWorkers: "allow" });

test("every service-worker app-shell path is available", async ({
  request,
}) => {
  const source = await readFile("service-worker.js", "utf8");
  const appShellBlock = source.match(/const APP_SHELL = \[([\s\S]*?)\];/)?.[1];
  expect(appShellBlock).toBeTruthy();
  const paths = [...appShellBlock.matchAll(/"([^"]+)"/g)].map(
    (match) => match[1],
  );
  expect(paths.length).toBeGreaterThan(0);
  for (const path of paths) {
    const response = await request.get(path.replace(/^\.\//, "/"));
    expect(response.status(), path).toBe(200);
  }
});

test("the current app shell starts from the service-worker cache offline", async ({
  context,
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.goto("/");
  await expect(page).toHaveTitle("Hector's Workout Tracker");
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload();
  await expect
    .poll(() =>
      page.evaluate(() => Boolean(navigator.serviceWorker.controller)),
    )
    .toBe(true);

  await context.setOffline(true);
  try {
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page).toHaveTitle("Hector's Workout Tracker");
    await expect(page.locator("#log")).toHaveClass(/active/);
    await expect(page.locator("#todayGreeting")).not.toContainText("Loading");
    assertNoRuntimeErrors();
  } finally {
    await context.setOffline(false);
  }
});
