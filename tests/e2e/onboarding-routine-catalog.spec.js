import { expect, test } from "@playwright/test";
import {
  loadApp,
  monitorRuntime,
  openPrimary,
  readStore,
} from "../helpers/app.js";

async function openSettings(page) {
  await openPrimary(page, "profile");
  await page.locator("[data-open-settings]").first().click();
  await expect(page.locator("#settings")).toHaveClass(/active/);
}

async function openRoutines(page) {
  await openPrimary(page, "profile");
  await page.locator('[data-profile-target="templates"]').click();
  await expect(page.locator("#templates")).toHaveClass(/active/);
}

async function draftExerciseNames(page) {
  return page
    .locator("#templateDraftList .routine-draft-name")
    .allTextContents();
}

test("first launch gates the app, validates safely, retries storage, and persists one Unicode name", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.addInitScript(() => {
    globalThis.__appShellVisibleBeforeName = false;
    new globalThis.MutationObserver(() => {
      const shell = globalThis.document.querySelector("#appShell");
      if (
        shell &&
        !shell.hidden &&
        !localStorage.getItem("hector_workout_settings_v1")
      ) {
        globalThis.__appShellVisibleBeforeName = true;
      }
    }).observe(globalThis.document, {
      subtree: true,
      childList: true,
      attributes: true,
    });
  });
  await loadApp(page, { completeOnboarding: false });

  await expect(page.locator("#onboarding h1")).toHaveText(
    "Welcome to your workout tracker",
  );
  await expect(page.locator("#onboardingForm")).toBeVisible();
  await expect(page.locator("#appShell")).toBeHidden();
  expect(
    await page.evaluate(() => globalThis.__appShellVisibleBeforeName),
  ).toBe(false);
  expect(
    await page.evaluate(() => [globalThis.innerWidth, globalThis.innerHeight]),
  ).toEqual([412, 915]);
  expect(
    await page.evaluate(
      () =>
        globalThis.document.documentElement.scrollWidth <=
        globalThis.innerWidth,
    ),
  ).toBe(true);

  const input = page.locator("#onboardingDisplayName");
  await input.fill(" \u200B\u200C ");
  await page.locator("#onboardingSubmit").click();
  await expect(page.locator("#onboardingError")).toContainText("required");
  await expect(input).toBeFocused();

  await input.fill("x".repeat(81));
  await page.locator("#onboardingSubmit").click();
  await expect(page.locator("#onboardingError")).toContainText(
    "80 characters or fewer",
  );

  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    globalThis.__restoreStorageSetItem = () => {
      Storage.prototype.setItem = original;
    };
    Storage.prototype.setItem = function setItem(key, value) {
      if (key === "hector_workout_settings_v1") {
        throw new DOMException("Storage is unavailable", "QuotaExceededError");
      }
      return original.call(this, key, value);
    };
  });
  await input.fill("Retry Me");
  await page.locator("#onboardingSubmit").click();
  await expect(page.locator("#onboardingError")).toContainText(
    "Could not save your name",
  );
  await expect(input).toHaveValue("Retry Me");
  await expect(page.locator("#onboarding")).toBeVisible();

  await page.evaluate(() => {
    globalThis.__restoreStorageSetItem();
    delete globalThis.__restoreStorageSetItem;
    globalThis.__settingsWriteCount = 0;
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function setItem(key, value) {
      if (key === "hector_workout_settings_v1") {
        globalThis.__settingsWriteCount += 1;
      }
      return original.call(this, key, value);
    };
  });
  const safeName = "José <b>Strong</b> 🏋️";
  await input.fill(`  ${safeName}  `);
  await page.locator("#onboardingForm").evaluate((form) => {
    form.dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
    form.dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
  });

  await expect(page.locator("#appShell")).toBeVisible();
  await expect(page.locator("#todayGreeting")).toContainText(safeName);
  await expect(page.locator("#todayGreeting b")).toHaveCount(0);
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem("hector_workout_settings_v1"))
          .displayName,
    ),
  ).toBe(safeName);
  expect(await page.evaluate(() => globalThis.__settingsWriteCount)).toBe(1);

  await page.reload();
  await expect(page.locator("#onboarding")).toBeHidden();
  await expect(page.locator("#todayGreeting")).toContainText(safeName);
  await expect(page.locator("body")).not.toContainText("Hector");
  assertNoRuntimeErrors();
});

test("display-name edits support cancel, visible failure, immediate refresh, and reload", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page, { displayName: "Initial User" });
  await openSettings(page);
  await expect(page.locator("#settingsDisplayNameCurrent")).toHaveText(
    "Initial User",
  );

  await page.locator("#editDisplayName").click();
  await page.locator("#settingsDisplayName").fill("Cancelled Name");
  await page.locator("#cancelDisplayName").click();
  await expect(page.locator("#settingsDisplayNameCurrent")).toHaveText(
    "Initial User",
  );

  await page.locator("#editDisplayName").click();
  await page.locator("#settingsDisplayName").fill("Renée 🚀");
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    globalThis.__restoreProfileStorage = () => {
      Storage.prototype.setItem = original;
    };
    Storage.prototype.setItem = function setItem(key, value) {
      if (key === "hector_workout_settings_v1") {
        throw new DOMException("Storage is unavailable", "QuotaExceededError");
      }
      return original.call(this, key, value);
    };
  });
  await page.locator("#saveDisplayName").click();
  await expect(page.locator("#settingsDisplayNameError")).toContainText(
    "current name is unchanged",
  );
  await expect(page.locator("#settingsDisplayName")).toHaveValue("Renée 🚀");
  await expect(page.locator("#settingsDisplayNameCurrent")).toHaveText(
    "Initial User",
  );

  await page.evaluate(() => {
    globalThis.__restoreProfileStorage();
    delete globalThis.__restoreProfileStorage;
  });
  await page.locator("#saveDisplayName").click();
  await expect(page.locator("#settingsDisplayNameCurrent")).toHaveText(
    "Renée 🚀",
  );
  await openPrimary(page, "home");
  await expect(page.locator("#todayGreeting")).toContainText("Renée 🚀");
  await openPrimary(page, "profile");
  await expect(page.locator("#profileDisplayName")).toHaveText("Renée 🚀");
  await expect(page.locator("#profileAvatar")).toHaveText("R");

  await page.reload();
  await expect(page.locator("#todayGreeting")).toContainText("Renée 🚀");
  assertNoRuntimeErrors();
});

test("routine builder uses one picker for local, catalog, custom, cancel, duplicate, and replace flows", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await openRoutines(page);
  await page.locator("#templateName").fill("Catalog Builder QA");

  const browse = page.locator("#browseTemplateExercise");
  await browse.click();
  await expect(page.locator("#exercisePickerTitle")).toHaveText(
    "Add Exercise to Routine",
  );
  await page.locator("#exercisePickerSearch").fill("Flat Bench Press");
  await page
    .locator('#exercisePickerOptions [data-exercise-name="Flat Bench Press"]')
    .click();
  await expect(page.locator("#exercisePicker")).toBeHidden();
  expect(await draftExerciseNames(page)).toEqual(["Flat Bench Press"]);

  await browse.click();
  await page.keyboard.press("Escape");
  await expect(page.locator("#exercisePicker")).toBeHidden();
  await expect(browse).toBeFocused();
  expect(await draftExerciseNames(page)).toEqual(["Flat Bench Press"]);

  await browse.click();
  await page.locator("#exercisePickerSearch").fill("Air Bike");
  await page.locator('[data-catalog-id="free-exercise-db:Air_Bike"]').click();
  await expect(page.locator("#exercisePickerPreviewAdd")).toHaveText(
    "Add to Routine",
  );
  await page.locator("#exercisePickerPreviewAdd").evaluate((button) => {
    button.click();
    button.click();
  });
  await expect(page.locator("#exercisePicker")).toBeHidden();
  expect(await draftExerciseNames(page)).toEqual([
    "Flat Bench Press",
    "Air Bike",
  ]);

  await browse.click();
  await page.locator("#exercisePickerCreateAction").click();
  await page.locator("#exercisePickerCustomName").fill("Custom Arc 🧭");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  expect(await draftExerciseNames(page)).toEqual([
    "Flat Bench Press",
    "Air Bike",
    "Custom Arc 🧭",
  ]);

  await browse.click();
  await page.locator("#exercisePickerSearch").fill("Air Bike");
  let duplicateMessage = "";
  page.once("dialog", async (dialog) => {
    duplicateMessage = dialog.message();
    await dialog.dismiss();
  });
  await page
    .locator('#exercisePickerOptions [data-exercise-name="Air Bike"]')
    .click();
  expect(duplicateMessage).toContain("already in this routine");
  await expect(page.locator("#exercisePicker")).toBeHidden();
  expect(await draftExerciseNames(page)).toEqual([
    "Flat Bench Press",
    "Air Bike",
    "Custom Arc 🧭",
  ]);

  await page
    .locator('[data-routine-action="change-draft"][data-exercise-index="0"]')
    .click();
  await expect(page.locator("#exercisePickerTitle")).toHaveText(
    "Change Routine Exercise",
  );
  await page.locator("#exercisePickerSearch").fill("Ab Crunch Machine");
  await page
    .locator('[data-catalog-id="free-exercise-db:Ab_Crunch_Machine"]')
    .click();
  await expect(page.locator("#exercisePickerPreviewAdd")).toHaveText(
    "Use in Routine",
  );
  await page.locator("#exercisePickerPreviewAdd").click();
  expect(await draftExerciseNames(page)).toEqual([
    "Ab Crunch Machine",
    "Air Bike",
    "Custom Arc 🧭",
  ]);
  expect(
    (await readStore(page, "templates")).some(
      (routine) => routine.name === "Catalog Builder QA",
    ),
  ).toBe(false);

  expect(
    await page.evaluate(
      () =>
        globalThis.document.documentElement.scrollWidth <=
        globalThis.innerWidth,
    ),
  ).toBe(true);
  await page.locator("#saveTemplate").click();
  await expect(page.locator("#toast")).toContainText("Routine saved");
  const saved = (await readStore(page, "templates")).find(
    (routine) => routine.name === "Catalog Builder QA",
  );
  expect(saved.exercises).toEqual([
    "Ab Crunch Machine",
    "Air Bike",
    "Custom Arc 🧭",
  ]);
  expect(
    saved.exercises.every((exercise) => typeof exercise === "string"),
  ).toBe(true);

  await page.reload();
  await openRoutines(page);
  const card = page.locator(".routine-card", {
    has: page.getByRole("heading", { name: "Catalog Builder QA" }),
  });
  await expect(card).toContainText("Ab Crunch Machine");
  await expect(card).toContainText("Air Bike");
  await expect(card).toContainText("Custom Arc 🧭");
  await card.locator('[data-routine-action="edit"]').click();
  expect(await draftExerciseNames(page)).toEqual(saved.exercises);
  assertNoRuntimeErrors();
});

test("routine picker keeps local and custom options usable when the catalog fails", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.route("**/src/data/exercise-catalog.json", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ invalidCatalog: true }),
    }),
  );
  await loadApp(page);
  await openRoutines(page);

  await page.locator("#browseTemplateExercise").click();
  await expect(page.locator("#exercisePickerCatalogStatus")).toContainText(
    "Catalog unavailable",
  );
  await page.locator("#exercisePickerSearch").fill("Romanian Deadlift");
  await page
    .locator('#exercisePickerOptions [data-exercise-name="Romanian Deadlift"]')
    .click();

  await page.locator("#browseTemplateExercise").click();
  await page.locator("#exercisePickerSearch").fill("No Such Exercise 12345");
  await expect(page.locator("#exercisePickerEmpty")).toBeVisible();
  await expect(page.locator("#exercisePickerCreateAction")).toBeVisible();
  await page.locator("#exercisePickerCreateAction").click();
  await page
    .locator("#exercisePickerCustomName")
    .fill("Catalog-Free Routine Move");
  await page
    .locator("#exercisePickerCreateForm")
    .evaluate((form) => form.requestSubmit());
  expect(await draftExerciseNames(page)).toEqual([
    "Romanian Deadlift",
    "Catalog-Free Routine Move",
  ]);
  assertNoRuntimeErrors();
});
