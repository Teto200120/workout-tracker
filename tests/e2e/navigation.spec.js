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
  await expect(page.locator("#todayProgressGlanceCard")).toHaveCount(0);
  await expect(page.locator("#todayBackupReminder")).toHaveCount(0);

  const cta = page.locator("#todayStartWorkout");
  await expect(cta).toBeVisible();
  await expect(cta.locator(".cta-label")).toHaveText("Start Workout");
  const ctaBeforeScroll = await cta.boundingBox();
  await page.evaluate(() =>
    globalThis.scrollTo(0, globalThis.document.documentElement.scrollHeight),
  );
  const ctaAfterScroll = await cta.boundingBox();
  expect(ctaAfterScroll.width).toBeCloseTo(ctaBeforeScroll.width, 0);
  expect(ctaAfterScroll.height).toBeCloseTo(ctaBeforeScroll.height, 0);
  await expect(page.locator("body")).not.toHaveClass(/today-cta-compact/u);
  await expect(cta).toHaveCSS("animation-name", "ctaBreathe");

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
