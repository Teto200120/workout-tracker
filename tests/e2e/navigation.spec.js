import { expect, test } from "@playwright/test";
import { loadApp, monitorRuntime, openPrimary } from "../helpers/app.js";

test("startup, primary navigation, and nested navigation stay coherent", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);

  const homeTab = page.locator('.tab[data-screen="home"]');
  await expect(page.locator("#todayView")).toBeVisible();
  await expect(homeTab).toHaveClass(/active/);
  await expect(homeTab).toHaveAttribute("aria-current", "page");

  await openPrimary(page, "stats");
  await expect(page.locator("#dashboard")).toHaveClass(/active/);
  await page.locator('[data-stats-detail="statsWeekly"]').click();
  await expect(page.locator("#statsWeekly")).toHaveClass(/active/);
  await expect(page.locator('.tab[data-screen="stats"]')).toHaveAttribute(
    "aria-current",
    "page",
  );
  await page.locator("#statsWeekly [data-stats-back]").click();
  await expect(page.locator("#dashboard")).toHaveClass(/active/);

  await openPrimary(page, "profile");
  await expect(page.locator("#profile")).toHaveClass(/active/);
  await page.locator('[data-profile-target="templates"]').click();
  await expect(page.locator("#templates")).toHaveClass(/active/);
  await expect(page.locator('.tab[data-screen="profile"]')).toHaveAttribute(
    "aria-current",
    "page",
  );
  await page.locator("#templates [data-profile-back]").click();
  await expect(page.locator("#profile")).toHaveClass(/active/);

  await openPrimary(page, "home");
  await expect(page.locator("#todayView")).toBeVisible();
  await expect(page.locator('[aria-current="page"]')).toHaveCount(1);
  assertNoRuntimeErrors();
});
