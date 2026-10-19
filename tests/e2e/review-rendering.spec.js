import { expect, test } from "@playwright/test";
import { loadApp, seedStores, workoutFixture } from "../helpers/app.js";

test("a full refresh reads each application store once in one snapshot transaction", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(1), workoutFixture(0)] });
  const result = await page.evaluate(async () => {
    const router = await import("/src/js/router.js");
    const reads = {};
    const transactions = [];
    const read = globalThis.IDBObjectStore.prototype.getAll;
    const transaction = globalThis.IDBDatabase.prototype.transaction;
    globalThis.IDBObjectStore.prototype.getAll = function (...args) {
      if (["workouts", "weights", "templates"].includes(this.name))
        reads[this.name] = (reads[this.name] || 0) + 1;
      return read.apply(this, args);
    };
    globalThis.IDBDatabase.prototype.transaction = function (
      names,
      mode,
      ...rest
    ) {
      const stores = typeof names === "string" ? [names] : [...names];
      if (
        stores.some((name) =>
          ["workouts", "weights", "templates"].includes(name),
        )
      )
        transactions.push({ stores, mode });
      return transaction.call(this, names, mode, ...rest);
    };
    try {
      await router.renderAll();
      return {
        reads,
        transactions,
        profileCount: globalThis.document.querySelector("#profileSessionCount")
          .textContent,
        historyCount: globalThis.document.querySelectorAll(
          ".stats-session-card",
        ).length,
        dashboardCount: globalThis.document.querySelector(
          "#dashboardStats strong",
        ).textContent,
      };
    } finally {
      globalThis.IDBObjectStore.prototype.getAll = read;
      globalThis.IDBDatabase.prototype.transaction = transaction;
    }
  });
  expect(result.reads).toEqual({ workouts: 1, weights: 1, templates: 1 });
  expect(result.transactions).toEqual([
    { stores: ["workouts", "weights", "templates"], mode: "readonly" },
  ]);
  expect(result.profileCount).toBe("2");
  expect(result.historyCount).toBe(2);
  expect(result.dashboardCount).toBe("2");
});

test("navigation retains unrelated forms and DOM while refreshing its destination from storage", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  const result = await page.evaluate(async (newWorkout) => {
    const router = await import("/src/js/router.js");
    const storage = await import("/src/js/storage/indexed-db.js");
    const doc = globalThis.document;
    const scheduleRow =
      doc.querySelector("#settingsSchedule").firstElementChild;
    const historyCard = doc.querySelector("#historyList").firstElementChild;
    const weeklyDetail = doc.querySelector(
      "#weeklyActivityDetailList",
    ).firstElementChild;
    const strengthDetail = doc.querySelector(
      "#strengthSnapshotDetail",
    ).firstElementChild;
    const workoutStats = doc.querySelector("#workoutStats").firstElementChild;
    doc.querySelector("#settingsWeightJump").value = "9";
    await router.switchScreen("profile");
    const profileKeptHistory =
      doc.querySelector("#historyList").firstElementChild === historyCard;
    await storage.saveWorkoutRecord(newWorkout);
    await router.switchScreen("history");
    const updatedCard = doc.querySelector("#historyList").firstElementChild;
    const historyCount = doc.querySelectorAll(".stats-session-card").length;
    await router.switchScreen("stats");
    const statsKeptHistory =
      doc.querySelector("#historyList").firstElementChild === updatedCard;
    const overviewKeptStrengthDetail =
      doc.querySelector("#strengthSnapshotDetail").firstElementChild ===
      strengthDetail;
    await router.switchScreen("statsStrength");
    const strengthReady = doc
      .querySelector("#exerciseProgress")
      .textContent.includes("Last 2 sessions");
    const hiddenWorkoutStatsUnchanged =
      doc.querySelector("#workoutStats").firstElementChild === workoutStats;
    await router.switchScreen("statsWorkoutStats");
    return {
      profileKeptHistory,
      statsKeptHistory,
      overviewKeptStrengthDetail,
      historyCount,
      weeklyDetailUnchanged:
        doc.querySelector("#weeklyActivityDetailList").firstElementChild ===
        weeklyDetail,
      scheduleRowUnchanged:
        doc.querySelector("#settingsSchedule").firstElementChild ===
        scheduleRow,
      enteredWeightJump: doc.querySelector("#settingsWeightJump").value,
      strengthReady,
      hiddenWorkoutStatsUnchanged,
      workoutStatsReady:
        doc.querySelector("#workoutStats strong").textContent === "2",
    };
  }, workoutFixture(1));
  expect(result).toEqual({
    profileKeptHistory: true,
    statsKeptHistory: true,
    overviewKeptStrengthDetail: true,
    historyCount: 2,
    weeklyDetailUnchanged: true,
    scheduleRowUnchanged: true,
    enteredWeightJump: "9",
    strengthReady: true,
    hiddenWorkoutStatsUnchanged: true,
    workoutStatsReady: true,
  });
});

test("settings, routines and feedback navigation do not read workout history", async ({
  page,
}) => {
  await loadApp(page);
  const result = await page.evaluate(async () => {
    const router = await import("/src/js/router.js");
    const read = globalThis.IDBObjectStore.prototype.getAll;
    let reads;
    const outcomes = {};
    globalThis.IDBObjectStore.prototype.getAll = function (...args) {
      if (["workouts", "weights", "templates"].includes(this.name))
        reads[this.name] = (reads[this.name] || 0) + 1;
      return read.apply(this, args);
    };
    try {
      for (const destination of ["settings", "templates", "feedback"]) {
        reads = {};
        await router.switchScreen(destination);
        outcomes[destination] = reads;
      }
      return outcomes;
    } finally {
      globalThis.IDBObjectStore.prototype.getAll = read;
    }
  });
  expect(result).toEqual({
    settings: { templates: 1 },
    templates: { templates: 1 },
    feedback: {},
  });
});

test("renderers consume shared record arrays without sorting or changing them in place", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(1), workoutFixture(0)] });
  expect(
    await page.evaluate(async () => {
      const records = await (
        await import("/src/js/storage/indexed-db.js")
      ).getAllApplicationRecords();
      const original = JSON.stringify(records);
      for (const collection of Object.values(records))
        Object.freeze(collection);
      const renderers = [
        ["progress", "renderDashboard"],
        ["history", "renderHistory"],
        ["routines", "renderTemplates"],
        ["profile", "renderProfile"],
        ["today", "renderTodayView"],
        ["backup", "renderBackupStatus"],
      ];
      for (const [module, render] of renderers)
        await (await import(`/src/js/screens/${module}.js`))[render](records);
      await (await import("/src/js/core/settings.js")).renderSettings(records);
      return JSON.stringify(records) === original;
    }),
  ).toBe(true);
});

test("Stats refreshes its goal preview without rebasing or rewriting the hidden goal draft", async ({
  page,
}) => {
  await loadApp(page);
  const result = await page.evaluate(async () => {
    const router = await import("/src/js/router.js");
    const local = await import("/src/js/storage/local.js");
    const progress = await import("/src/js/screens/progress.js");
    const doc = globalThis.document;
    await local.setGoals({ weeklyGoal: 4 });
    await router.renderAll();
    doc.querySelector("#weeklyGoal").value = "7";
    const detail = doc.querySelector("#goalsProgress").firstElementChild;
    await local.setGoals({ weeklyGoal: 6 });
    await router.switchScreen("stats");
    const preview = doc.querySelector("#goalsPreview").textContent;
    const detailUnchanged =
      doc.querySelector("#goalsProgress").firstElementChild === detail;
    await router.switchScreen("statsGoals");
    const entered = doc.querySelector("#weeklyGoal").value;
    const saved = await progress.saveGoalsToStorage();
    return {
      preview,
      detailUnchanged,
      entered,
      saved,
      goal: local.getGoals().weeklyGoal,
    };
  });
  expect(result.preview).toContain("/ 6");
  expect(result.detailUnchanged).toBe(true);
  expect(result.entered).toBe("7");
  expect(result.saved).toBe(false);
  expect(result.goal).toBe(6);
});

test("a delayed older navigation cannot render or finish over the latest destination", async ({
  page,
}) => {
  await loadApp(page);
  await seedStores(page, { workouts: [workoutFixture(0)] });
  await page.evaluate(async () => {
    const router = await import("/src/js/router.js");
    const read = globalThis.IDBObjectStore.prototype.getAll;
    let delayNext = true;
    globalThis.__historyBeforeNavigation =
      globalThis.document.querySelector("#historyList").firstElementChild;
    globalThis.IDBObjectStore.prototype.getAll = function (...args) {
      const request = read.apply(this, args);
      if (this.name !== "workouts" || !delayNext) return request;
      delayNext = false;
      return {
        get result() {
          return request.result;
        },
        get error() {
          return request.error;
        },
        set onsuccess(handler) {
          request.onsuccess = (event) => {
            globalThis.__releaseHistoryRead = () =>
              handler.call(request, event);
          };
        },
        set onerror(handler) {
          request.onerror = handler;
        },
      };
    };
    globalThis.__restoreHistoryRead = () => {
      globalThis.IDBObjectStore.prototype.getAll = read;
    };
    globalThis.__oldNavigation = router.switchScreen("history");
  });
  await expect
    .poll(() => page.evaluate(() => typeof globalThis.__releaseHistoryRead))
    .toBe("function");
  const result = await page.evaluate(async () => {
    try {
      const router = await import("/src/js/router.js");
      await router.switchScreen("settings");
      const doc = globalThis.document;
      doc.querySelector("#settingsWeightJump").value = "9";
      globalThis.__releaseHistoryRead();
      await globalThis.__oldNavigation;
      return {
        active: doc.querySelector(".screen.active").id,
        historyUnchanged:
          doc.querySelector("#historyList").firstElementChild ===
          globalThis.__historyBeforeNavigation,
        value: doc.querySelector("#settingsWeightJump").value,
        transitioning: doc.body.dataset.screenTransitioning,
      };
    } finally {
      globalThis.__restoreHistoryRead();
    }
  });
  expect(result).toEqual({
    active: "settings",
    historyUnchanged: true,
    value: "9",
    transitioning: "false",
  });
});
