import { expect, test } from "@playwright/test";
import { loadApp, monitorRuntime, openPrimary } from "../helpers/app.js";

async function openSettings(page) {
  await openPrimary(page, "profile");
  await page.locator("[data-open-settings]").first().click();
  await expect(page.locator("#settings")).toHaveClass(/active/);
}

test("settings persist across reload and reset to defaults", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await openSettings(page);

  await page.locator('label[for="settingsAnimations"]').click();
  await expect(page.locator("#settingsAnimations")).not.toBeChecked();
  await page.locator("#saveSettings").click();
  await expect(page.locator("body")).toHaveClass(/no-animations/);
  await page.reload();
  await openSettings(page);
  await expect(page.locator("#settingsAnimations")).not.toBeChecked();

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#resetSettings").click();
  await expect(page.locator("#settingsAnimations")).toBeChecked();
  await expect(page.locator("body")).not.toHaveClass(/no-animations/);
  assertNoRuntimeErrors();
});
