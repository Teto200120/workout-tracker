import assert from "node:assert/strict";
import test from "node:test";
import {
  DEFAULT_APP_SETTINGS,
  DEFAULT_SCHEDULE,
  DAY_LABELS,
} from "../../src/js/core/constants.js";
import {
  countRecentWorkouts,
  dateKeyFromDate,
  getWeeklyActivityData,
  getWorkoutStreak,
  mondayFirstWeekDates,
  resolveWorkoutDay,
} from "../../src/js/domain/schedule.js";

test("Monday-first weeks resolve correctly across a Sunday boundary", () => {
  const dates = mondayFirstWeekDates(new Date("2026-07-12T12:00:00Z"));
  assert.equal(dateKeyFromDate(dates[0]), "2026-07-06");
  assert.equal(dateKeyFromDate(dates[6]), "2026-07-12");
});

test("schedule resolution uses saved settings without changing defaults", () => {
  const settings = structuredClone(DEFAULT_APP_SETTINGS);
  settings.schedule[1] = { kind: "gym", routine: "Custom Monday" };
  const plan = resolveWorkoutDay(
    "2026-07-13",
    settings,
    DEFAULT_SCHEDULE,
    DEFAULT_APP_SETTINGS,
    DAY_LABELS,
  );
  assert.deepEqual(plan, {
    kind: "gym",
    title: "Custom Monday",
    routine: "Custom Monday",
    note: "Suggested from your Monday schedule.",
  });
  const rest = resolveWorkoutDay(
    "2026-07-12",
    settings,
    DEFAULT_SCHEDULE,
    DEFAULT_APP_SETTINGS,
    DAY_LABELS,
  );
  assert.equal(rest.kind, "rest");
  assert.equal(rest.title, "Rest Day");
});

test("weekly activity receives time and plan resolution explicitly", () => {
  const currentDate = new Date("2026-07-15T12:00:00Z");
  const workouts = [{ date: "2026-07-13" }, { date: "2026-07-15" }];
  const activity = getWeeklyActivityData(workouts, currentDate, (key) => ({
    kind: key === "2026-07-14" ? "rest" : "gym",
    title: "Plan",
  }));
  assert.equal(activity.gymDays, 6);
  assert.equal(activity.completedGymDays, 2);
  assert.equal(
    activity.days.find((day) => day.key === "2026-07-14").status,
    "Rest",
  );
  assert.equal(
    activity.days.find((day) => day.key === "2026-07-15").status,
    "Logged",
  );
});

test("streak and recent-workout rules are deterministic at date boundaries", () => {
  const currentDate = new Date("2026-07-15T12:00:00Z");
  const workouts = [
    { date: "2026-07-15" },
    { date: "2026-07-14" },
    { date: "2026-07-13" },
    { date: "2026-07-07" },
  ];
  assert.equal(getWorkoutStreak(workouts, currentDate), 3);
  assert.equal(countRecentWorkouts(workouts, currentDate), 3);
  assert.equal(getWorkoutStreak([], currentDate), 0);
});
