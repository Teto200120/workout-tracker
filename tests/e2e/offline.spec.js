import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import {
  completeOnboarding,
  monitorRuntime,
  openPrimary,
  readStore,
  startRoutine,
} from "../helpers/app.js";

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
  await expect(page).toHaveTitle("Workout Tracker");
  await completeOnboarding(page);
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload();
  await expect
    .poll(() =>
      page.evaluate(() => Boolean(navigator.serviceWorker.controller)),
    )
    .toBe(true);

  await startRoutine(page);
  await page.locator("#addExercise").click();
  await page.locator("#exercisePickerSearch").fill("Air Bike");
  await expect(
    page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]'),
  ).toBeVisible();
  await page.locator(".exercise-picker-cancel").click();

  await context.setOffline(true);
  try {
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page).toHaveTitle("Workout Tracker");
    await expect(page.locator("#log")).toHaveClass(/active/);
    await expect(page.locator("#todayGreeting")).not.toContainText("Loading");
    await expect(page.locator("#todayStartWorkout .cta-label")).toContainText(
      "Resume",
    );
    await page.locator("#todayStartWorkout").click({ force: true });
    await page.locator("#addExercise").click();
    await page.locator("#exercisePickerSearch").fill("Air Bike");
    await expect(
      page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]'),
    ).toBeVisible();
    await page.locator(".exercise-picker-cancel").click();
    await page.locator(".exercise").first().locator(".guide-row").click();
    await page.locator("#exerciseDetailGuideTab").click();
    await expect(page.locator('[data-guide-source="catalog"]')).toBeVisible();
    await expect(page.locator(".exercise-guide-attribution")).toContainText(
      "Free Exercise DB",
    );
    await expect(page.locator("#exerciseDetailTitle")).toHaveText(
      "V-Bar Lat Pulldown",
    );
    await page.locator("#exerciseDetailBack").click();
    await page.locator(".set-done").evaluateAll((inputs) => {
      inputs.forEach((input) => {
        input.checked = true;
        input.dispatchEvent(new Event("change", { bubbles: true }));
      });
    });
    await page.locator("#sessionSaveTop").click();
    await expect(page.locator("#completionModal")).toBeVisible();
    await page.locator("#completionDone").click();
    await expect(page.locator("#completionModal")).toBeHidden();
    expect(await readStore(page, "workouts")).toHaveLength(1);

    await openPrimary(page, "profile");
    await page.locator('[data-profile-target="templates"]').click();
    await page.locator("#browseTemplateExercise").click();
    await page.locator("#exercisePickerSearch").fill("Air Bike");
    await page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]').click();
    await page.locator("#exercisePickerPreviewAdd").click();
    await expect(
      page.locator("#templateDraftList .routine-draft-name"),
    ).toHaveText("Air Bike");

    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page).toHaveTitle("Workout Tracker");
    await expect(page.locator("#todayGreeting")).not.toContainText("Loading");
    expect(await readStore(page, "workouts")).toHaveLength(1);
    assertNoRuntimeErrors();
  } finally {
    await context.setOffline(false);
  }
});

test("onboarding and education journeys remain usable offline", async ({
  context,
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.goto("/");
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload();
  await expect
    .poll(() =>
      page.evaluate(() => Boolean(navigator.serviceWorker.controller)),
    )
    .toBe(true);
  await page.evaluate(async () => {
    const { clearApplicationData } =
      await import("/src/js/application/backup.js");
    await clearApplicationData();
  });

  await context.setOffline(true);
  try {
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page.locator("#onboarding")).toBeVisible();
    await expect(page.locator("#appShell")).toBeHidden();
    await completeOnboarding(page, "Offline Athlete", {
      preserveEducation: true,
    });
    await expect(page.locator("#homeEducationOffer")).toBeVisible();

    await page.locator("#homeEducationStart").click();
    for (const progress of ["1 of 4", "2 of 4", "3 of 4", "4 of 4"]) {
      await expect(page.locator("#coachMarkProgress")).toHaveText(progress);
      await page.locator(".coach-mark-next").click();
    }
    await expect(page.locator(".coach-mark-root")).toBeHidden();

    await openPrimary(page, "profile");
    await page.locator("[data-open-settings]").first().click();
    await expect(page.locator("#settings")).toHaveClass(/active/u);
    await page.locator('[data-education-action="replay-home"]').click();
    await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 4");
    await page.keyboard.press("Escape");
    await expect(page.locator(".coach-mark-root")).toBeHidden();

    await startRoutine(page);
    for (const progress of ["1 of 3", "2 of 3", "3 of 3"]) {
      await expect(page.locator("#coachMarkProgress")).toHaveText(progress);
      await page.locator(".coach-mark-next").click();
    }
    await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 1");
    await expect(page.locator("#coachMarkBody")).toContainText("RPE 8");
    await page.locator(".coach-mark-next").click();
    await expect(page.locator(".coach-mark-root")).toBeHidden();
    assertNoRuntimeErrors();
  } finally {
    await context.setOffline(false);
  }
});
