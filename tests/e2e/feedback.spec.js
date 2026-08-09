import { expect, test } from "@playwright/test";
import { loadApp, monitorRuntime, openPrimary } from "../helpers/app.js";

async function openFeedbackFromProfile(page) {
  await openPrimary(page, "profile");
  const entry = page.getByRole("button", { name: /Beta Feedback/i });
  await expect(entry).toBeVisible();
  await entry.click();
  await expect(page.locator("#feedback")).toHaveClass(/active/);
}

test.beforeEach(async ({ page }) => {
  await loadApp(page);
});

test("Profile exposes the user-facing feedback form and explicit diagnostics allowlist", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await openFeedbackFromProfile(page);

  await expect(
    page.getByRole("heading", { name: "Send Feedback" }),
  ).toBeVisible();
  await expect(page.getByLabel("Feedback type")).toBeVisible();
  await expect(page.getByLabel("Details")).toBeVisible();
  await expect(page.getByLabel("Screenshot (optional)")).toBeVisible();
  await expect(page.locator("#feedbackDiagnosticsList li")).toHaveText([
    "App version",
    "Screen size",
    "Browser or installed-app mode",
    "Online or offline state at save time",
    "The Feedback screen name",
  ]);
  await expect(page.locator("#feedback")).not.toContainText(
    /all users|aggregate submissions|admin dashboard/i,
  );
  expect(
    await page.evaluate(
      () =>
        globalThis.document.documentElement.scrollWidth <=
        globalThis.innerWidth,
    ),
  ).toBe(true);
  await assertNoRuntimeErrors();
});

test("a failed current-slice send stays pending across reload and a mocked retry can succeed", async ({
  page,
}) => {
  const assertNoRuntimeErrors = monitorRuntime(page);
  await openFeedbackFromProfile(page);
  await page
    .getByLabel("Details")
    .fill("The weekly card stopped responding after I returned from Stats.");

  const requestsDuringSubmit = [];
  page.on("request", (request) => requestsDuringSubmit.push(request.url()));
  await page.getByRole("button", { name: "Save report" }).click();

  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
  await expect(page.locator(".feedback-pending-report")).toContainText(
    "Sending is not configured in this build.",
  );
  await expect(page.locator(".feedback-pending-report")).toContainText(
    "1 send attempt",
  );
  expect(requestsDuringSubmit).toEqual([]);

  const persisted = await page.evaluate(() =>
    JSON.parse(localStorage.getItem("hector_workout_feedback_outbox_v1")),
  );
  expect(persisted).toHaveLength(1);
  expect(Object.keys(persisted[0].diagnostics).sort()).toEqual([
    "appVersion",
    "connection",
    "currentScreen",
    "displayMode",
    "viewport",
  ]);

  await page.reload();
  await openFeedbackFromProfile(page);
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");

  await page.evaluate(async () => {
    const { createMockFeedbackTransport } =
      await import("/src/js/application/feedback.js");
    const { setFeedbackTransport } =
      await import("/src/js/screens/feedback.js");
    setFeedbackTransport(createMockFeedbackTransport(["success"]));
  });
  await page.getByRole("button", { name: "Try sending" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("0 pending");
  await expect(page.locator("#feedbackFormStatus")).toContainText(
    "Report sent",
  );
  expect(
    await page.evaluate(() =>
      JSON.parse(localStorage.getItem("hector_workout_feedback_outbox_v1")),
    ),
  ).toEqual([]);
  await assertNoRuntimeErrors();
});

test("screenshot validation rejects unsafe types and locally compresses an allowed image", async ({
  page,
}) => {
  await openFeedbackFromProfile(page);
  const input = page.locator("#feedbackScreenshot");
  await input.setInputFiles({
    name: "not-an-image.svg",
    mimeType: "image/svg+xml",
    buffer: Buffer.from("<svg xmlns='http://www.w3.org/2000/svg'></svg>"),
  });
  await expect(page.locator("#feedbackScreenshotStatus")).toHaveText(
    "Choose a PNG, JPEG, or WebP screenshot.",
  );
  await expect(page.locator("#feedbackScreenshotPreview")).toBeHidden();

  await input.setInputFiles("icon-192.png");
  await expect(page.locator("#feedbackScreenshotStatus")).toContainText(
    "Ready: compressed",
  );
  await expect(page.locator("#feedbackScreenshotPreview")).toBeVisible();
});

test("deleting a pending report requires confirmation", async ({ page }) => {
  await openFeedbackFromProfile(page);
  await page
    .getByLabel("Details")
    .fill("This report should remain until deletion is confirmed.");
  await page.getByRole("button", { name: "Save report" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");

  page.once("dialog", (dialog) => dialog.dismiss());
  await page.getByRole("button", { name: "Delete" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");

  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "Delete" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("0 pending");
});

test("backups and workout-data clearing leave pending feedback separate", async ({
  page,
}) => {
  await openFeedbackFromProfile(page);
  await page
    .getByLabel("Details")
    .fill(
      "Keep this pending report separate from workout backup and clearing.",
    );
  await page.getByRole("button", { name: "Save report" }).click();
  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");

  const result = await page.evaluate(async () => {
    const { buildBackup, clearApplicationData } =
      await import("/src/js/application/backup.js");
    const backup = await buildBackup("2026-08-08T16:00:00.000Z");
    await clearApplicationData();
    return {
      backupKeys: Object.keys(backup),
      pending: JSON.parse(
        localStorage.getItem("hector_workout_feedback_outbox_v1"),
      ),
    };
  });

  expect(result.backupKeys).not.toContain("feedback");
  expect(result.backupKeys).not.toContain("feedbackOutbox");
  expect(result.pending).toHaveLength(1);
});

test("saving feedback works when crypto.randomUUID is unavailable", async ({
  page,
}) => {
  await page.evaluate(() => {
    Object.defineProperty(globalThis.crypto, "randomUUID", {
      configurable: true,
      value: undefined,
    });
  });
  await openFeedbackFromProfile(page);
  await page
    .getByLabel("Details")
    .fill("This mobile browser does not expose crypto.randomUUID.");
  await page.getByRole("button", { name: "Save report" }).click();

  await expect(page.locator("#feedbackPendingCount")).toHaveText("1 pending");
  const id = await page.evaluate(() => {
    const reports = JSON.parse(
      localStorage.getItem("hector_workout_feedback_outbox_v1"),
    );
    return reports[0].id;
  });
  expect(id).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u,
  );
});
