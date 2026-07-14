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
  await waitForDatabaseOpen(page);
}

export async function waitForDatabaseOpen(page) {
  await expect
    .poll(() =>
      page.evaluate(async () => {
        const { isDatabaseOpen } =
          await import("/src/js/storage/indexed-db.js");
        return isDatabaseOpen();
      }),
    )
    .toBe(true);
}

export async function seedStores(page, records) {
  await page.evaluate(async (data) => {
    const {
      clearApplicationStores,
      saveLegacyWeight,
      saveRoutine,
      saveWorkoutRecord,
      seedDefaultTemplates,
    } = await import("/src/js/storage/indexed-db.js");
    const { refreshTemplateDropdowns } =
      await import("/src/js/components/routine-selectors.js");
    const { renderAll } = await import("/src/js/router.js");
    await clearApplicationStores();
    for (const workout of data.workouts || []) await saveWorkoutRecord(workout);
    for (const weight of data.weights || []) await saveLegacyWeight(weight);
    for (const routine of data.templates || []) await saveRoutine(routine);
    await seedDefaultTemplates();
    await refreshTemplateDropdowns();
    await renderAll();
  }, records);
}

export async function seedRawApplicationData(page, data) {
  await page.evaluate(async (seed) => {
    const request = globalThis.indexedDB.open(
      "hector_workout_tracker_fresh_v1",
      2,
    );
    const database = await new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
    await new Promise((resolve, reject) => {
      const transaction = database.transaction(
        ["workouts", "weights", "templates"],
        "readwrite",
      );
      transaction.oncomplete = () => resolve();
      transaction.onabort = () =>
        reject(transaction.error || new Error("Raw seed transaction aborted."));
      transaction.onerror = () => reject(transaction.error);
      const collections = [
        ["workouts", seed.workouts || []],
        ["weights", seed.weights || []],
        ["templates", seed.templates || []],
      ];
      for (const [storeName, records] of collections) {
        const store = transaction.objectStore(storeName);
        store.clear();
        records.forEach((record) => store.put(record));
      }
    });
    database.close();

    const localFields = {
      settings: "hector_workout_settings_v1",
      goals: "hector_workout_goals_v1",
      draft: "hector_workout_draft_v1",
      backupMeta: "hector_workout_backup_meta_v1",
    };
    for (const [field, key] of Object.entries(localFields)) {
      if (!Object.prototype.hasOwnProperty.call(seed, field)) continue;
      if (seed[field] === null) localStorage.removeItem(key);
      else localStorage.setItem(key, JSON.stringify(seed[field]));
    }
    if (Object.prototype.hasOwnProperty.call(seed, "rawLocalStorage")) {
      for (const [key, rawValue] of Object.entries(seed.rawLocalStorage)) {
        if (rawValue === null) localStorage.removeItem(key);
        else localStorage.setItem(key, rawValue);
      }
    }
    if (Object.prototype.hasOwnProperty.call(seed, "schemaVersion")) {
      if (seed.schemaVersion === null)
        localStorage.removeItem("hector_workout_data_schema_version");
      else
        localStorage.setItem(
          "hector_workout_data_schema_version",
          String(seed.schemaVersion),
        );
    }
  }, data);
}

export async function readStore(page, storeName) {
  return page.evaluate(async (name) => {
    const { getLegacyWeights, getRoutines, getWorkouts } =
      await import("/src/js/storage/indexed-db.js");
    if (name === "workouts") return getWorkouts();
    if (name === "weights") return getLegacyWeights();
    if (name === "templates") return getRoutines();
    throw new Error(`Unknown store: ${name}`);
  }, storeName);
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
