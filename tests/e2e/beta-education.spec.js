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

async function expectCoachMarkSeparated(page) {
  const root = page.locator(".coach-mark-root");
  await expect(root).not.toHaveClass(/is-transitioning/u);
  const geometry = await page.evaluate(() => {
    const bubble = globalThis.document
      .querySelector(".coach-mark-bubble")
      .getBoundingClientRect();
    const highlight = globalThis.document
      .querySelector(".coach-mark-highlight")
      .getBoundingClientRect();
    return {
      bubble: {
        left: bubble.left,
        top: bubble.top,
        right: bubble.right,
        bottom: bubble.bottom,
      },
      highlight: {
        left: highlight.left,
        top: highlight.top,
        right: highlight.right,
        bottom: highlight.bottom,
        width: highlight.width,
        height: highlight.height,
      },
      placement:
        globalThis.document.querySelector(".coach-mark-bubble").dataset
          .placement,
      viewport: {
        width: globalThis.innerWidth,
        height: globalThis.innerHeight,
      },
    };
  });
  const overlaps =
    geometry.bubble.left < geometry.highlight.right &&
    geometry.bubble.right > geometry.highlight.left &&
    geometry.bubble.top < geometry.highlight.bottom &&
    geometry.bubble.bottom > geometry.highlight.top;

  expect(geometry.placement).toMatch(/^docked-(top|bottom)$/u);
  expect(geometry.highlight.width).toBeGreaterThan(0);
  expect(geometry.highlight.height).toBeGreaterThan(0);
  expect(overlaps).toBe(false);
  expect(geometry.bubble.left).toBeGreaterThanOrEqual(0);
  expect(geometry.bubble.top).toBeGreaterThanOrEqual(0);
  expect(geometry.bubble.right).toBeLessThanOrEqual(geometry.viewport.width);
  expect(geometry.bubble.bottom).toBeLessThanOrEqual(geometry.viewport.height);
}

async function expectCoachMarkFramesTarget(page, targetSelector) {
  const root = page.locator(".coach-mark-root");
  await expect(root).not.toHaveClass(/is-transitioning/u);
  const geometry = await page.evaluate((selector) => {
    const rect = (element) => {
      const bounds = element.getBoundingClientRect();
      return {
        left: bounds.left,
        top: bounds.top,
        right: bounds.right,
        bottom: bounds.bottom,
      };
    };
    return {
      target: rect(globalThis.document.querySelector(selector)),
      highlight: rect(
        globalThis.document.querySelector(".coach-mark-highlight"),
      ),
      bubble: rect(globalThis.document.querySelector(".coach-mark-bubble")),
      viewport: {
        width: globalThis.innerWidth,
        height: globalThis.innerHeight,
      },
    };
  }, targetSelector);
  const expected = {
    left: Math.max(4, geometry.target.left - 6),
    top: Math.max(4, geometry.target.top - 6),
    right: Math.min(geometry.viewport.width - 4, geometry.target.right + 6),
    bottom: Math.min(geometry.viewport.height - 4, geometry.target.bottom + 6),
  };
  const overlaps =
    geometry.bubble.left < geometry.highlight.right &&
    geometry.bubble.right > geometry.highlight.left &&
    geometry.bubble.top < geometry.highlight.bottom &&
    geometry.bubble.bottom > geometry.highlight.top;

  expect(geometry.target.right).toBeGreaterThan(0);
  expect(geometry.target.bottom).toBeGreaterThan(0);
  expect(geometry.target.left).toBeLessThan(geometry.viewport.width);
  expect(geometry.target.top).toBeLessThan(geometry.viewport.height);
  expect(geometry.highlight.left).toBeCloseTo(expected.left, 0);
  expect(geometry.highlight.top).toBeCloseTo(expected.top, 0);
  expect(geometry.highlight.right).toBeCloseTo(expected.right, 0);
  expect(geometry.highlight.bottom).toBeCloseTo(expected.bottom, 0);
  expect(overlaps).toBe(false);
  await expect(page.locator("body")).toHaveClass(/coach-mark-open/u);
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

test("Home offer skips once and the three-step tour supports focus, Escape, Back, completion, and missing targets", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.goto("/");
  await completeOnboarding(page, "Tour User", { preserveEducation: true });
  await expect(page.locator("#homeEducationOffer")).toBeVisible();
  const offeredWorkoutTop = await page
    .locator("#todayWorkoutCard")
    .evaluate((element) => element.offsetTop);
  const offerBox = await page.locator("#homeEducationOffer").boundingBox();
  const workoutBox = await page.locator("#todayWorkoutCard").boundingBox();
  const offerVisuals = await page
    .locator("#homeEducationOffer")
    .evaluate((element) => {
      const style = globalThis.getComputedStyle(element);
      const tail = globalThis.getComputedStyle(element, "::after");
      return {
        boxShadow: style.boxShadow,
        tailContent: tail.content,
        tailHeight: Number.parseFloat(tail.height),
        tailWidth: Number.parseFloat(tail.width),
      };
    });
  expect(offerBox.width).toBeLessThan(350);
  expect(offerBox.height).toBeLessThan(190);
  expect(offerBox.x).toBeGreaterThan(workoutBox.x);
  expect(offerBox.x + offerBox.width).toBeLessThan(
    workoutBox.x + workoutBox.width,
  );
  const offerBottom = offerBox.y + offerBox.height;
  expect(offerBottom).toBeLessThanOrEqual(workoutBox.y);
  expect(workoutBox.y - offerBottom).toBeLessThan(40);
  expect(offerVisuals.boxShadow).not.toBe("none");
  expect(offerVisuals.tailContent).not.toBe("none");
  expect(offerVisuals.tailHeight).toBeGreaterThan(0);
  expect(offerVisuals.tailWidth).toBeGreaterThan(0);
  await expect(page.locator("#homeEducationOffer")).toHaveCSS(
    "position",
    "fixed",
  );
  await page.locator("#homeEducationSkip").click();
  expect(
    await page
      .locator("#todayWorkoutCard")
      .evaluate((element) => element.offsetTop),
  ).toBe(offeredWorkoutTop);
  expect((await experience(page, "homeTour")).status).toBe("skipped");
  await page.reload();
  await expect(page.locator("#homeEducationOffer")).toBeHidden();

  await openSettings(page);
  await page.locator('[data-education-action="replay-home"]').click();
  await expect(page.locator(".coach-mark-bubble")).toBeVisible();
  await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 3");
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
  for (const progress of ["1 of 3", "2 of 3", "3 of 3"]) {
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

test("Home remains static and its cards keep compact aligned proportions on a phone", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loadApp(page);

  const geometry = await page.evaluate(() => {
    const app = globalThis.document.querySelector(".app");
    const week = globalThis.document
      .querySelector(".today-week-card")
      .getBoundingClientRect();
    const workout = globalThis.document
      .querySelector("#todayWorkoutCard")
      .getBoundingClientRect();
    const appRect = app.getBoundingClientRect();
    const appStyle = globalThis.getComputedStyle(app);
    return {
      documentHeight: globalThis.document.documentElement.scrollHeight,
      viewportHeight: globalThis.innerHeight,
      expectedLeft: appRect.left + Number.parseFloat(appStyle.paddingLeft),
      week: { left: week.left, width: week.width },
      workout: {
        left: workout.left,
        width: workout.width,
        height: workout.height,
      },
    };
  });

  expect(geometry.documentHeight).toBeLessThanOrEqual(geometry.viewportHeight);
  expect(geometry.workout.left).toBeCloseTo(geometry.expectedLeft, 0);
  expect(geometry.workout.left).toBeCloseTo(geometry.week.left, 0);
  expect(geometry.workout.width).toBeCloseTo(geometry.week.width, 0);
  expect(geometry.workout.height).toBeLessThan(350);
});

test("Home restores content-driven scrolling after its tour completes", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 640 });
  await loadApp(page, { preserveEducation: true });
  await page.locator("#homeEducationStart").click();
  for (const progress of ["1 of 3", "2 of 3", "3 of 3"]) {
    await expect(page.locator("#coachMarkProgress")).toHaveText(progress);
    await page.locator(".coach-mark-next").click();
  }
  await expect(page.locator(".coach-mark-root")).toBeHidden();
  await expect(page.locator("body")).not.toHaveClass(/coach-mark-open/u);

  await page.locator("#todayWorkoutDrawer summary").click();
  const heights = await page.evaluate(() => ({
    document: globalThis.document.documentElement.scrollHeight,
    viewport: globalThis.innerHeight,
  }));
  expect(heights.document).toBeGreaterThan(heights.viewport);
  await page.evaluate(() =>
    globalThis.scrollTo({
      top: globalThis.document.body.scrollHeight,
      behavior: "instant",
    }),
  );
  await expect
    .poll(() => page.evaluate(() => globalThis.scrollY))
    .toBeGreaterThan(0);
});

test("active-workout and RPE guidance use safe contexts and RPE controls follow the preference", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await page.setViewportSize({ width: 390, height: 844 });
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
  await expectCoachMarkFramesTarget(
    page,
    '[data-education-target="active-exercise-card"]',
  );
  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("2 of 3");
  await expectCoachMarkFramesTarget(
    page,
    '[data-education-target="active-current-set"]',
  );
  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("3 of 3");
  await expectCoachMarkFramesTarget(
    page,
    '[data-education-target="finish-workout"]',
  );
  await page.locator(".coach-mark-next").click();

  await expect(page.locator("#coachMarkProgress")).toHaveText("1 of 1");
  await expect(page.locator("#coachMarkBody")).toContainText("RPE 8");
  await expectCoachMarkFramesTarget(
    page,
    '[data-education-target="rpe-control"]',
  );
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
  await page.setViewportSize({ width: 360, height: 640 });
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
  await expect(page.locator(".coach-mark-bubble")).toHaveCSS(
    "padding-top",
    "14px",
  );
  expect(
    await page.evaluate(() => globalThis.__educationScrollBehaviors),
  ).toContain("auto");
  await expectCoachMarkSeparated(page);
  expect(
    (await page.locator(".coach-mark-bubble").boundingBox()).height,
  ).toBeLessThan(190);

  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("2 of 3");
  await expectCoachMarkSeparated(page);
  await page.locator(".coach-mark-next").click();
  await expect(page.locator("#coachMarkProgress")).toHaveText("3 of 3");
  await expectCoachMarkSeparated(page);
  const ctaBox = await page.locator("#todayStartWorkout").boundingBox();
  await page.mouse.click(
    ctaBox.x + ctaBox.width / 2,
    ctaBox.y + ctaBox.height / 2,
  );
  await expect(page.locator("#sessionView")).toHaveClass(/hidden/u);

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
