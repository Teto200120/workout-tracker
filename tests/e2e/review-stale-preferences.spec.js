import { expect, test } from "@playwright/test";
import { loadApp } from "../helpers/app.js";

for (const action of ["save", "reset"]) {
  test(`stale settings ${action} preserves newer preferences and entered values`, async ({
    page,
    context,
  }) => {
    await loadApp(page);
    const other = await context.newPage();
    await loadApp(other);
    await page.evaluate(async () =>
      (await import("/src/js/core/settings.js")).renderSettings(),
    );
    await page.evaluate(() => {
      globalThis.document.querySelector("#settingsWeightJump").value = "3";
    });
    await other.evaluate(async () => {
      const local = await import("/src/js/storage/local.js");
      await local.setAppSettings({
        ...local.getAppSettings(),
        defaultWeightJump: 5,
        displayName: "Newer name",
      });
    });
    page.on("dialog", (dialog) => dialog.accept());
    expect(
      await page.evaluate(async (action) => {
        const settings = await import("/src/js/core/settings.js");
        return action === "save"
          ? settings.saveSettingsFromForm()
          : settings.resetAppSettings();
      }, action),
    ).toBe(false);
    await expect(page.locator("#toast")).toContainText(
      "Settings changed in another action",
    );
    await expect(page.locator("#settingsWeightJump")).toHaveValue("3");
    const saved = await page.evaluate(async () =>
      (await import("/src/js/storage/local.js")).getAppSettings(),
    );
    expect(saved.defaultWeightJump).toBe(5);
    expect(saved.displayName).toBe("Newer name");
    await other.close();
  });
}

test("background goal rendering preserves the stale draft and its original conflict check", async ({
  page,
  context,
}) => {
  await loadApp(page);
  const other = await context.newPage();
  await loadApp(other);
  await page.evaluate(async () => {
    const local = await import("/src/js/storage/local.js");
    await local.setGoals({ weeklyGoal: 4 });
    await globalThis.renderAll();
    globalThis.document.querySelector("#weeklyGoal").value = "5";
  });
  await other.evaluate(async () =>
    (await import("/src/js/storage/local.js")).setGoals({ weeklyGoal: 6 }),
  );
  await page.evaluate(() => globalThis.renderAll());
  await expect(page.locator("#weeklyGoal")).toHaveValue("5");
  expect(
    await page.evaluate(async () =>
      (await import("/src/js/screens/progress.js")).saveGoalsToStorage(),
    ),
  ).toBe(false);
  await expect(page.locator("#toast")).toContainText(
    "Your goal changed in another action",
  );
  await expect(page.locator("#weeklyGoal")).toHaveValue("5");
  expect(
    await page.evaluate(
      async () =>
        (await import("/src/js/storage/local.js")).getGoals().weeklyGoal,
    ),
  ).toBe(6);
  await other.close();
});

test("successful preference saves advance their own snapshots and retain extensions", async ({
  page,
}) => {
  await loadApp(page);
  const outcomes = await page.evaluate(async () => {
    const local = await import("/src/js/storage/local.js");
    const settings = await import("/src/js/core/settings.js");
    const progress = await import("/src/js/screens/progress.js");
    const current = local.getAppSettings();
    await local.setAppSettings({
      ...current,
      schedule: {
        ...current.schedule,
        0: { ...current.schedule[0], extension: "keep day" },
        extension: { source: "keep schedule" },
      },
      extension: { keep: true },
    });
    await local.setGoals({ weeklyGoal: 4, extension: "keep" });
    await globalThis.renderAll();
    await settings.renderSettings();
    const results = [];
    for (const value of [3, 4]) {
      globalThis.document.querySelector("#settingsWeightJump").value =
        String(value);
      results.push(await settings.saveSettingsFromForm());
    }
    for (const value of [5, 6]) {
      globalThis.document.querySelector("#weeklyGoal").value = String(value);
      results.push(await progress.saveGoalsToStorage());
    }
    return {
      results,
      settings: local.getAppSettings(),
      goals: local.getGoals(),
    };
  });
  expect(outcomes.results).toEqual([true, true, true, true]);
  expect(outcomes.settings.defaultWeightJump).toBe(4);
  expect(outcomes.settings.extension).toEqual({ keep: true });
  expect(outcomes.settings.schedule[0].extension).toBe("keep day");
  expect(outcomes.settings.schedule.extension).toEqual({
    source: "keep schedule",
  });
  expect(outcomes.goals).toEqual({ weeklyGoal: 6, extension: "keep" });
});
