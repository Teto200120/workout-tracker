import { readFileSync, writeFileSync } from "node:fs";
import {
  buildExerciseStats,
  buildCompletionSummary,
  getWorkoutStatsSummary,
} from "../src/js/domain/workout-metrics.js";
import {
  getWeeklyActivityData,
  getWorkoutStreak,
  countRecentWorkouts,
  resolveWorkoutDay,
} from "../src/js/domain/schedule.js";
import {
  DEFAULT_APP_SETTINGS,
  DEFAULT_SCHEDULE,
  DAY_LABELS,
} from "../src/js/core/constants.js";

const set = (weight = "", reps = "", options = {}) => ({
  weight,
  reps,
  rpe: "",
  done: false,
  warmup: false,
  ...options,
});
const exercise = (name, sets = []) => ({ name, notes: "", sets });
const workout = (id, date, exercises = [], options = {}) => ({
  id,
  date,
  type: "Push",
  startTime: "23:45",
  endTime: "00:30",
  notes: "",
  tags: [],
  createdAt: `${date}T12:00:00Z`,
  exercises,
  ...options,
});
const cases = [
  { name: "empty", today: "2026-10-22", workouts: [] },
  {
    name: "independent sources and duplicates",
    today: "2026-10-22",
    workouts: [
      workout(
        0,
        "2026-10-22",
        [
          exercise(" Bench ", [
            set("200", "1"),
            set("180", "5", { rpe: "8.5" }),
            set("100", "20"),
            set("999", "99", { warmup: true, done: true }),
          ]),
          exercise("Bench", [set("100", "20")]),
          exercise("bench", [set("50", "10")]),
          exercise("Bodyweight", [set("0", "10"), set("", "0")]),
        ],
        { durationMinutes: 0 },
      ),
      workout(
        "0",
        "2026-10-21",
        [exercise("Bench", [set("150", "10", { done: true })])],
        { durationMinutes: 30 },
      ),
    ],
  },
  {
    name: "ties keep first supplied source",
    today: "2026-10-22",
    workouts: [
      workout(2, "2026-10-20", [
        exercise("Bench", [set("200", "1", { rpe: "0" })]),
      ]),
      workout(10, "2026-10-21", [
        exercise("Bench", [set("200", "1", { rpe: "9" }), set("100", "20")]),
      ]),
    ],
  },
  {
    name: "trim case warmup and blank candidates",
    today: "2026-10-22",
    workouts: [
      workout("names", "2026-10-22", [
        exercise("\u00a0Press\ufeff", [set("10", "10")]),
        exercise("press", [set("20", "10")]),
        exercise("No record", [
          set("0", "10"),
          set("10", "0"),
          set("", ""),
          set("100", "10", { warmup: true }),
        ]),
      ]),
    ],
  },
  {
    name: "spring DST Sunday week and rolling counts",
    today: "2026-03-08",
    workouts: [
      workout(1, "2026-03-08"),
      workout(2, "2026-03-07"),
      workout(3, "2026-03-07"),
      workout(4, "2026-03-02"),
      workout(5, "2026-03-01"),
      workout(6, "2026-03-09"),
    ],
  },
  {
    name: "fall DST Sunday and zero duration average",
    today: "2026-11-01",
    workouts: [
      workout(1, "2026-11-01", [], { durationMinutes: 1 }),
      workout(2, "2026-10-31", [], { durationMinutes: 2 }),
      workout(3, "2026-10-30", [], { durationMinutes: 0 }),
    ],
  },
  {
    name: "Monday year boundary yesterday does not extend streak",
    today: "2026-01-05",
    workouts: [
      workout(1, "2026-01-04"),
      workout(2, "2026-01-03"),
      workout(3, "2025-12-30"),
      workout(4, "2025-12-29"),
      workout(5, "2026-01-06"),
    ],
  },
  {
    name: "completion case insensitive comparison and work volume gain",
    today: "2026-10-22",
    workouts: [
      workout("old", "2026-10-21", [exercise(" bench ", [set("100", "5")])]),
    ],
    completion: workout("new", "2026-10-22", [
      exercise("Bench", [set("110", "8"), set("50", "10")]),
    ]),
  },
  {
    name: "completion equal record fallback",
    today: "2026-10-22",
    workouts: [
      workout("old", "2026-10-21", [exercise("Bench", [set("100", "10")])]),
    ],
    completion: workout("new", "2026-10-22", [
      exercise("Bench", [set("100", "10")]),
    ]),
  },
  {
    name: "completion four highlight cap",
    today: "2026-10-22",
    workouts: [],
    completion: workout(
      "new",
      "2026-10-22",
      ["A", "B", "C", "D", "E"].map((name) =>
        exercise(name, [set("10", "10")]),
      ),
    ),
  },
];
const output = {
  cases: cases.map((c) => {
    const settings = structuredClone(DEFAULT_APP_SETTINGS);
    const date = new Date(`${c.today}T12:00:00`);
    const resolve = (day) =>
      resolveWorkoutDay(
        day,
        settings,
        DEFAULT_SCHEDULE,
        DEFAULT_APP_SETTINGS,
        DAY_LABELS,
      );
    const week = getWeeklyActivityData(c.workouts, date, resolve);
    const previousSame =
      [...c.workouts]
        .sort(
          (a, b) =>
            b.date.localeCompare(a.date) ||
            b.createdAt.localeCompare(a.createdAt),
        )
        .find((w) => w.type === c.completion?.type) || null;
    return {
      ...c,
      settings,
      expected: {
        totals: getWorkoutStatsSummary(c.workouts),
        exercises: buildExerciseStats(c.workouts),
        weekly: {
          ...week,
          days: week.days.map((day) =>
            Object.fromEntries(
              Object.entries(day).filter(([key]) => key !== "classes"),
            ),
          ),
        },
        streak: getWorkoutStreak(c.workouts, date),
        recent: countRecentWorkouts(c.workouts, date),
        ...(c.completion
          ? {
              completion: buildCompletionSummary(
                c.completion,
                previousSame,
                c.workouts,
              ),
            }
          : {}),
      },
    };
  }),
};
const path = new URL(
  "../tests/fixtures/android/progress.json",
  import.meta.url,
);
const text = `${JSON.stringify(output, null, 2)}\n`;
if (process.argv.includes("--check")) {
  if (readFileSync(path, "utf8") !== text)
    throw new Error(
      "Android progress fixture differs from the PWA. Regenerate it.",
    );
} else writeFileSync(path, text);
