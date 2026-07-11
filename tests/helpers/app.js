import { expect } from "@playwright/test";

const EXPECTED_CONSOLE_MESSAGES = [
  "Service worker registration failed:",
  "Service Worker registration blocked by Playwright",
];

export function monitorRuntime(page) {
  const errors = [];

  page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`));
  page.on("console", (message) => {
    if (!["error", "warning"].includes(message.type())) return;
    const text = message.text();
    if (EXPECTED_CONSOLE_MESSAGES.some((expected) => text.includes(expected)))
      return;
    errors.push(`${message.type()}: ${text}`);
  });

  return () => expect(errors, "unexpected browser errors").toEqual([]);
}

export async function loadApp(page) {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "vibrate", {
      configurable: true,
      value: () => true,
    });
  });
  await page.goto("/");
  await expect(page).toHaveTitle("Hector's Workout Tracker");
  await expect(page.locator("#log")).toHaveClass(/active/);
  await expect(page.locator("#todayGreeting")).not.toContainText("Loading");
  await page.waitForFunction(() => globalThis.db && globalThis.getItems);
}

export async function seedStores(page, records) {
  await page.evaluate(async (data) => {
    for (const storeName of globalThis.STORES)
      await globalThis.clearStore(storeName);
    for (const [storeName, items] of Object.entries(data)) {
      for (const item of items) await globalThis.saveItem(storeName, item);
    }
    await globalThis.seedDefaultTemplates();
    await globalThis.refreshTemplateDropdowns();
    await globalThis.renderAll();
  }, records);
}

export async function readStore(page, storeName) {
  return page.evaluate((name) => globalThis.getItems(name), storeName);
}

export async function startRoutine(page, routineName = "Back / Biceps") {
  await page.locator("#todayWorkoutDrawer summary").click();
  await page.locator("#todayWorkoutSelect").selectOption(routineName);
  await page.locator("#todayStartWorkout").click({ force: true });
  await expect(page.locator("#sessionView")).not.toHaveClass(/hidden/);
  await expect(page.locator("#sessionRoutineTitle")).toHaveText(routineName);
}

export async function openPrimary(page, name) {
  await page.locator(`.tab[data-screen="${name}"]`).click();
  await expect(page.locator(`.tab[data-screen="${name}"]`)).toHaveAttribute(
    "aria-current",
    "page",
  );
}

export function workoutFixture(index = 0, overrides = {}) {
  const day = String((index % 28) + 1).padStart(2, "0");
  return {
    id: `test-workout-${index}`,
    date: `2026-06-${day}`,
    type: index % 2 ? "Back / Biceps" : "Chest / Triceps",
    startTime: "18:00",
    endTime: "18:40",
    durationMinutes: 40,
    notes: `Automated fixture ${index}`,
    tags: [],
    exercises: [
      {
        name: index === 199 ? "Unique Filter Exercise" : "Flat Bench Press",
        notes: "",
        sets: [
          {
            weight: String(100 + index),
            reps: "8",
            rpe: "8",
            done: true,
            warmup: false,
          },
        ],
      },
    ],
    createdAt: `2026-06-${day}T18:00:00.000Z`,
    ...overrides,
  };
}
