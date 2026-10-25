import { expect, test } from "@playwright/test";
import { monitorRuntime } from "../helpers/app.js";

const entry = "/?feedback=android";
const receiver =
  "https://workout-tracker-beta-feedback.hector-workout-tracker.workers.dev";

test("the cached hosted entry reloads offline and retains a pending report", async ({
  browser,
}) => {
  const context = await browser.newContext({ serviceWorkers: "allow" });
  try {
    const page = await context.newPage();
    const healthy = monitorRuntime(page);
    await page.goto("http://127.0.0.1:4175/?feedback=android");
    await page.evaluate(() => globalThis.navigator.serviceWorker.ready);
    await expect
      .poll(() =>
        page.evaluate(() =>
          Boolean(globalThis.navigator.serviceWorker.controller),
        ),
      )
      .toBe(true);
    await context.setOffline(true);
    await page.reload();
    await expect(page).toHaveTitle("Android Feedback · Workout Tracker");
    await page
      .getByLabel("Details")
      .fill(
        "Android preview: this synthetic report is saved while the browser is offline.",
      );
    await page.getByRole("button", { name: "Save report" }).click();
    await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
    await expect(page.locator("#feedbackSubmit")).toBeEnabled();
    await page.reload();
    await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
    await expect(page.locator("#onboarding")).toBeHidden();
    await healthy();
  } finally {
    await context.close();
  }
});

test("Android feedback opens without browser onboarding or touching workout storage", async ({
  page,
}) => {
  const healthy = monitorRuntime(page);
  const saved = {
    hector_workout_settings_v1: "unreadable settings must stay untouched",
    hector_workout_draft_v1: JSON.stringify({ privateDraft: "retained" }),
  };
  await page.addInitScript((values) => {
    for (const [key, value] of Object.entries(values))
      localStorage.setItem(key, value);
  }, saved);
  const requests = [];
  page.on("request", (request) => {
    if (request.url().startsWith(receiver)) requests.push(request.url());
  });
  await page.goto(entry);
  await expect(page).toHaveTitle("Android Feedback · Workout Tracker");
  await expect(
    page.getByRole("heading", { name: "Send Feedback" }),
  ).toBeVisible();
  await expect(page.locator("#onboarding")).toBeHidden();
  await expect(page.getByRole("navigation")).toBeHidden();
  await expect(
    page.getByRole("button", { name: "Back to Profile" }),
  ).toBeHidden();
  await expect(page.getByLabel("Details")).toBeVisible();
  await expect(page.locator("#feedback")).toContainText(
    "Reports are saved in this browser, separate from Android workouts.",
  );
  await expect(page.locator("#feedbackDiagnosticsList li").first()).toHaveText(
    "Browser page version",
  );
  expect(await page.evaluate(() => ({ ...localStorage }))).toEqual(saved);
  expect(
    await page.evaluate(async () => {
      const { isDatabaseOpen } = await import("/src/js/storage/indexed-db.js");
      return isDatabaseOpen();
    }),
  ).toBe(false);
  expect(requests).toEqual([]);
  expect(
    await page.evaluate(
      () =>
        globalThis.document.documentElement.scrollWidth <=
        globalThis.innerWidth,
    ),
  ).toBe(true);
  await healthy();
});

test("hosted reports survive offline failure and reload; sending and retry stay explicit", async ({
  page,
  context,
}) => {
  const healthy = monitorRuntime(page);
  await page.addInitScript(() => {
    globalThis.turnstile = {
      render(_container, options) {
        options.callback("test-only-token");
      },
    };
  });
  const sent = [];
  await page.route(`${receiver}/`, async (route) => {
    sent.push(route.request().postDataJSON());
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ ok: true }),
    });
  });
  await page.goto(entry);
  await page
    .getByLabel("Details")
    .fill(
      "Android preview: the session button did not respond after returning from History.",
    );
  await context.setOffline(true);
  await page.getByRole("button", { name: "Save report" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
  await expect(page.locator(".feedback-pending-report")).toContainText(
    "1 send attempt",
  );
  expect(sent).toEqual([]);
  const pending = await page.evaluate(() =>
    localStorage.getItem("hector_workout_feedback_outbox_v1"),
  );
  await context.setOffline(false);
  await page.reload();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_feedback_outbox_v1"),
    ),
  ).toBe(pending);
  expect(sent).toEqual([]);
  await page.getByRole("button", { name: "Try sending" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("0 pending");
  expect(sent).toHaveLength(1);
  expect(sent[0].turnstileToken).toBe("test-only-token");
  expect(Object.keys(sent[0].report.diagnostics).sort()).toEqual([
    "appVersion",
    "connection",
    "currentScreen",
    "displayMode",
    "viewport",
  ]);
  expect(sent[0].report.id).toBe(JSON.parse(pending)[0].id);
  await healthy();
});

test("the durable report is visible while verification waits, with duplicate actions disabled", async ({
  page,
}) => {
  const healthy = monitorRuntime(page);
  await page.addInitScript(() => {
    globalThis.turnstile = {
      render(_container, options) {
        globalThis.completeTestVerification = () =>
          options.callback("test-only-token");
      },
    };
  });
  await page.route(`${receiver}/`, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ ok: true }),
    }),
  );
  await page.goto(entry);
  await page
    .getByLabel("Details")
    .fill("Android preview: a synthetic report awaiting browser verification.");
  await page.getByRole("button", { name: "Save report" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
  await expect(page.locator("#feedbackFormStatus")).toContainText(
    "Complete the verification",
  );
  await expect(
    page.getByRole("button", { name: "Try sending" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Delete", exact: true }),
  ).toBeDisabled();
  const saved = await page.evaluate(() =>
    JSON.parse(localStorage.getItem("hector_workout_feedback_outbox_v1")),
  );
  expect(saved).toHaveLength(1);
  expect(saved[0].attempts).toBe(1);
  await page.evaluate(() => globalThis.completeTestVerification());
  await expect(page.locator("#feedbackPendingCount")).toHaveText("0 pending");
  await healthy();
});

test("a corrupt browser outbox stays unchanged and ordinary visits still require setup", async ({
  page,
}) => {
  const healthy = monitorRuntime(page);
  await page.addInitScript(() => {
    localStorage.setItem(
      "hector_workout_feedback_outbox_v1",
      "corrupt report data",
    );
  });
  await page.goto(entry);
  await expect(page.locator("#feedbackPendingCount")).toHaveText(
    "Needs attention",
  );
  await expect(page.locator(".feedback-outbox-warning")).toContainText(
    "left unchanged",
  );
  expect(
    await page.evaluate(() =>
      localStorage.getItem("hector_workout_feedback_outbox_v1"),
    ),
  ).toBe("corrupt report data");
  await page.goto("/");
  await expect(page).toHaveTitle("Workout Tracker");
  await expect(page.locator("#onboarding")).toBeVisible();
  await expect(page.locator("#appShell")).toBeHidden();
  await healthy();
});
