import { expect, test } from "@playwright/test";
import {
  completeOnboarding,
  loadApp,
  monitorRuntime,
  openPrimary,
  startRoutine,
} from "../helpers/app.js";

async function openSettings(page) {
  await openPrimary(page, "profile");
  await page.locator("[data-open-settings]").first().click();
  await expect(page.locator("#settings")).toHaveClass(/active/u);
}

async function experience(page, experienceId) {
  return page.evaluate(async (id) => {
    const { getEducationExperience } =
      await import("/src/js/application/education.js");
    return getEducationExperience(id);
  }, experienceId);
}

test("two-step onboarding keeps values, saves name and RPE together, and survives reload", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page, { completeOnboarding: false });

  await expect(page.locator("#onboardingStepOne")).toBeVisible();
  await expect(page.locator("#onboardingStepTwo")).toBeHidden();
  await expect(page.locator("#appShell")).toBeHidden();
  await page.locator("#onboardingDisplayName").fill("Beta Athlete");
  await page.locator("#onboardingContinue").click();
  await expect(page.locator("#onboardingStepTwo")).toBeVisible();
  await expect(page.locator("#onboardingRpeAware")).toBeChecked();
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_settings_v1"),
    ),
  ).toBe(null);

  await page.locator("#onboardingBack").click();
  await expect(page.locator("#onboardingDisplayName")).toHaveValue(
    "Beta Athlete",
  );
  await page.locator("#onboardingContinue").click();
  await page.locator('label[for="onboardingRpeAware"]').click();
  await page.locator("#onboardingSubmit").click();

  const stored = await page.evaluate(() => ({
    settings: JSON.parse(localStorage.getItem("hector_workout_settings_v1")),
    education: JSON.parse(localStorage.getItem("hector_workout_education_v1")),
  }));
  expect(stored.settings.displayName).toBe("Beta Athlete");
  expect(stored.settings.rpeAware).toBe(false);
  expect(stored.education.schemaVersion).toBe(1);

  await page.reload();
  await expect(page.locator("#onboarding")).toBeHidden();
  await expect(page.locator("#todayGreeting")).toContainText("Beta Athlete");
  assertNoRuntimeErrors();
});

test("Home offer skips once and the four-step tour supports focus, Escape, Back, completion, and missing targets", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.goto("/");
  await completeOnboarding(page, "Tour User", { preserveEducation: true });
  await expect(page.locator("#homeEducationOffer")).toBeVisible();
  await page.locator("#homeEducationSkip").click();
  expect((await experience(page, "homeTour")).status).toBe("skipped");
  await page.reload();
  await expect(page.locator("#homeEducationOffer")).toBeHidden();

  await openSettings(page);
  await page.locator('[data-education-action="replay-home"]').click();
  await expect(page.locator(".coach-mark-bubble")).toBeVisible();
  await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 4");
  await expect(page.locator(".coach-mark-next")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.locator(".coach-mark-skip")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  await expect(page.locator('.tab[aria-current="page"]')).toBeFocused();
  expect((await experience(page, "homeTour")).status).toBe("dismissed");

  await openSettings(page);
  await page.locator('[data-education-action="replay-home"]').click();
  await expect(page.locator(".coach-mark-bubble")).toBeVisible();
  await page.evaluate(() => globalThis.history.back());
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  await expect(page.locator("#log")).toHaveClass(/active/u);
  expect((await experience(page, "homeTour")).status).toBe("dismissed");

  await openSettings(page);
  await page.locator('[data-education-action="replay-home"]').click();
  for (const progress of ["1 of 4", "2 of 4", "3 of 4", "4 of 4"]) {
    await expect(page.locator("#coachMarkProgress")).toHaveText(progress);
    await page.locator(".coach-mark-next").click();
  }
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  expect((await experience(page, "homeTour")).status).toBe("completed");

  await page.evaluate(async () => {
    globalThis.document
      .querySelectorAll("[data-education-target^='home-']")
      .forEach((target) => target.removeAttribute("data-education-target"));
    const { requestEducationReplay } =
      await import("/src/js/application/education.js");
    const { startHomeTour } = await import("/src/js/screens/today.js");
    requestEducationReplay("homeTour");
    startHomeTour();
  });
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  await expect(page.locator("#toast")).toContainText("still available");
  expect((await experience(page, "homeTour")).status).toBe("deferred");
  assertNoRuntimeErrors();
});

test("active-workout and RPE guidance use safe contexts and RPE controls follow the preference", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.goto("/");
  await completeOnboarding(page, "Workout Guide", {
    preserveEducation: true,
  });
  await page.locator("#homeEducationSkip").click();
  await startRoutine(page);

  await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 3");
  await expect(page.locator("#coachMarkTitle")).toHaveText(
    "Your exercise area",
  );
  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("2 of 3");
  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("3 of 3");
  await page.locator(".coach-mark-next").click();

  await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 1");
  await expect(page.locator("#coachMarkBody")).toContainText("RPE 8");
  await page.locator(".coach-mark-next").click();
  expect((await experience(page, "activeWorkoutBasics")).status).toBe(
    "completed",
  );
  expect((await experience(page, "rpeBasics")).status).toBe("completed");

  await page.locator(".weight-value").first().fill("100");
  await page.locator("#sessionRoutineTitle").click();
  await page.evaluate(async () => {
    const { requestEducationReplay } =
      await import("/src/js/application/education.js");
    const { showSessionView } =
      await import("/src/js/screens/active-workout.js");
    requestEducationReplay("activeWorkoutBasics");
    showSessionView();
  });
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  await expect
    .poll(async () => (await experience(page, "activeWorkoutBasics")).status)
    .toBe("deferred");

  await page.locator(".rpe-value").first().fill("9");
  await page.locator("#sessionBack").click();
  await openSettings(page);
  await page.locator('label[for="settingsRpeAware"]').click();
  await page.locator("#saveSettings").click();
  await openPrimary(page, "home");
  await page.locator("#todayStartWorkout").click({ force: true });
  await expect(page.locator(".live-rpe-row").first()).toBeHidden();
  await expect(page.locator(".set-rpe").first()).toHaveValue("9");

  await page.locator("#sessionBack").click();
  await openSettings(page);
  await page.locator('label[for="settingsRpeAware"]').click();
  await page.locator("#saveSettings").click();
  await openPrimary(page, "home");
  await page.locator("#todayStartWorkout").click({ force: true });
  await expect(page.locator(".live-rpe-row").first()).toBeVisible();
  await expect(page.locator(".set-rpe").first()).toHaveValue("9");
  assertNoRuntimeErrors();
});

test("Routine guidance preserves the draft and inline tips complete and replay", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);

  await openSettings(page);
  await page.locator('[data-education-action="replay-routine"]').click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 3");
  const draftBefore = await page.locator("#templateDraftList").textContent();
  await page.locator(".coach-mark-next").click();
  await page.locator(".coach-mark-next").click();
  await page.locator(".coach-mark-next").click();
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  expect(await page.locator("#templateDraftList").textContent()).toBe(
    draftBefore,
  );

  await openSettings(page);
  await page.locator('[data-education-action="replay-stats"]').click();
  await expect(page.locator("#statsEducationTip")).toBeVisible();
  await page.locator('[data-inline-education-complete="statsBasics"]').click();
  expect((await experience(page, "statsBasics")).status).toBe("completed");

  await openSettings(page);
  await page.locator('[data-education-action="replay-history"]').click();
  await expect(page.locator("#historyEducationTip")).toBeVisible();
  await page
    .locator('[data-inline-education-complete="historyBasics"]')
    .click();
  expect((await experience(page, "historyBasics")).status).toBe("completed");

  await openSettings(page);
  await page.locator('[data-education-action="explain-rpe"]').click();
  await expect(page.locator("#rpeHelpDialog")).toBeVisible();
  await expect(page.locator("#rpeHelpDialog")).toContainText("two reps left");
  await page.locator("#rpeHelpClose").click();

  page.once("dialog", (dialog) => dialog.accept());
  await page.locator('[data-education-action="reset-all"]').click();
  expect((await experience(page, "homeTour")).status).toBe("unseen");
  expect((await experience(page, "statsBasics")).status).toBe("unseen");
  await expect(page.locator("#settingsDisplayNameCurrent")).toHaveText(
    "Test User",
  );
  await expect(page.locator("#onboarding")).toBeHidden();
  assertNoRuntimeErrors();
});

test("coach marks respect reduced motion, reposition on resize, and block background actions", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await loadApp(page);
  await page.evaluate(() => {
    globalThis.__educationScrollBehaviors = [];
    const original = globalThis.Element.prototype.scrollIntoView;
    globalThis.Element.prototype.scrollIntoView = function scrollIntoView(
      options,
    ) {
      globalThis.__educationScrollBehaviors.push(options?.behavior);
      return original.call(this, options);
    };
  });
  await openSettings(page);
  await page.locator('[data-education-action="replay-home"]').click();
  await expect(page.locator(".coach-mark-bubble")).toBeVisible();
  expect(
    await page.evaluate(() => globalThis.__educationScrollBehaviors),
  ).toContain("auto");

  await page.locator(".coach-mark-next").click();
  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("3 of 4");
  const ctaBox = await page.locator("#todayStartWorkout").boundingBox();
  await page.mouse.click(
    ctaBox.x + ctaBox.width / 2,
    ctaBox.y + ctaBox.height / 2,
  );
  await expect(page.locator("#sessionView")).toHaveClass(/hidden/u);

  await page.setViewportSize({ width: 360, height: 640 });
  const bubbleBox = await page.locator(".coach-mark-bubble").boundingBox();
  expect(bubbleBox.x).toBeGreaterThanOrEqual(0);
  expect(bubbleBox.y).toBeGreaterThanOrEqual(0);
  expect(bubbleBox.x + bubbleBox.width).toBeLessThanOrEqual(360);
  expect(bubbleBox.y + bubbleBox.height).toBeLessThanOrEqual(640);
  await page.keyboard.press("Escape");
  assertNoRuntimeErrors();
});

test("Exercise Guide education is inline and Clear All Data removes only guidance with app data", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await loadApp(page);
  await openSettings(page);
  await page.locator('[data-education-action="replay-guide"]').click();
  await openPrimary(page, "home");
  await startRoutine(page);
  await page.locator(".exercise").first().locator(".guide-row").click();
  await page.locator("#exerciseDetailGuideTab").click();
  await expect(
    page.locator("#exerciseDetailContent .education-inline-tip"),
  ).toBeVisible();
  await expect(
    page.locator("#exerciseDetailContent .education-inline-tip"),
  ).toContainText("never changed");
  await page
    .locator("#exerciseDetailContent .education-inline-tip button")
    .click();
  expect((await experience(page, "exerciseGuideBasics")).status).toBe(
    "completed",
  );

  await page.locator("#exerciseDetailBack").click();
  await page.locator("#sessionBack").click();
  await openPrimary(page, "profile");
  await page.locator('[data-profile-target="backup"]').first().click();
  page.once("dialog", (dialog) => dialog.accept());
  await page.locator("#clearData").click();
  await expect(page.locator("#onboarding")).toBeVisible();
  const cleared = await page.evaluate(() => ({
    education: localStorage.getItem("hector_workout_education_v1"),
    settings: localStorage.getItem("hector_workout_settings_v1"),
    marker: localStorage.getItem("hector_workout_data_schema_version"),
  }));
  expect(cleared.education).toBe(null);
  expect(cleared.settings).toBe(null);
  expect(cleared.marker).toBe("2");
  assertNoRuntimeErrors();
});
