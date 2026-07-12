import assert from "node:assert/strict";
import test from "node:test";
import {
  buildCompletionSummary,
  buildExerciseStats,
  completedSets,
  durationLabel,
  estimatedOneRepMax,
  getBestSet,
  getWorkoutStatsSummary,
  hasSaveableWorkoutContent,
  setVolume,
  totalSets,
  workoutDurationMinutes,
  workoutVolume,
} from "../../src/js/domain/workout-metrics.js";

function workout(overrides = {}) {
  return {
    id: "workout-1",
    date: "2026-07-10",
    type: "Push",
    startTime: "23:45",
    endTime: "00:30",
    exercises: [],
    ...overrides,
  };
}

test("Epley estimated 1RM preserves the production formula", () => {
  assert.equal(estimatedOneRepMax(100, 10), 100 * (1 + 10 / 30));
  assert.equal(estimatedOneRepMax(0, 10), 0);
  assert.equal(estimatedOneRepMax(undefined, undefined), 0);
  assert.ok(Number.isNaN(estimatedOneRepMax("invalid", 8)));
});

test("set and workout volume exclude warm-ups and tolerate missing values", () => {
  assert.equal(setVolume({ weight: "135", reps: "8", warmup: false }), 1080);
  assert.equal(setVolume({ weight: "95", reps: "10", warmup: true }), 0);
  assert.equal(setVolume({}), 0);
  assert.equal(
    workoutVolume(
      workout({
        exercises: [
          {
            sets: [
              { weight: "95", reps: "10", warmup: true },
              { weight: "135", reps: "8" },
            ],
          },
          {
            sets: [
              { weight: "50", reps: "12" },
              { weight: "", reps: "8" },
            ],
          },
        ],
      }),
    ),
    1680,
  );
  assert.equal(workoutVolume({}), 0);
});

test("working and completed-set counts preserve current warm-up behavior", () => {
  const value = workout({
    exercises: [
      {
        sets: [
          { done: true, warmup: true },
          { done: true, warmup: false },
          { done: false, warmup: false },
        ],
      },
    ],
  });
  assert.equal(totalSets(value), 2);
  assert.equal(completedSets(value), 2);
  assert.equal(totalSets({}), 0);
  assert.equal(completedSets({}), 0);
});

test("best set ignores warm-ups, zero values, and invalid candidates", () => {
  const best = getBestSet({
    sets: [
      { weight: "200", reps: "5", warmup: true },
      { weight: "", reps: "8" },
      { weight: "100", reps: "10", rpe: "8" },
      { weight: "110", reps: "6", rpe: "9" },
    ],
  });
  assert.deepEqual(best, {
    weight: 100,
    reps: 10,
    estimated1rm: 100 * (1 + 10 / 30),
    volume: 1000,
    rpe: "8",
  });
  assert.equal(getBestSet({ sets: [] }), null);
});

test("duration calculation supports saved values, empty clocks, and midnight", () => {
  assert.equal(workoutDurationMinutes({ durationMinutes: 37 }), 37);
  assert.equal(
    workoutDurationMinutes({ startTime: "23:45", endTime: "00:30" }),
    45,
  );
  assert.equal(workoutDurationMinutes({ startTime: "10:00" }), 0);
  assert.equal(durationLabel(0), "-");
  assert.equal(durationLabel(45), "45m");
  assert.equal(durationLabel(60), "1h");
  assert.equal(durationLabel(75), "1h 15m");
});

test("exercise and workout aggregation keep stored shapes unchanged", () => {
  const workouts = [
    workout({
      date: "2026-07-08",
      exercises: [{ name: "Bench", sets: [{ weight: "100", reps: "8" }] }],
    }),
    workout({
      id: "workout-2",
      date: "2026-07-10",
      durationMinutes: 30,
      exercises: [
        {
          name: "Bench",
          sets: [
            { weight: "105", reps: "8" },
            { weight: "45", reps: "10", warmup: true },
          ],
        },
      ],
    }),
  ];
  const [bench] = buildExerciseStats(workouts);
  assert.equal(bench.sessions, 2);
  assert.equal(bench.sets, 3);
  assert.equal(bench.bestWeight, 105);
  assert.equal(bench.history.length, 2);
  assert.deepEqual(getWorkoutStatsSummary(workouts), {
    workouts: 2,
    sets: 2,
    volume: 1640,
    avgDuration: 38,
  });
});

test("completion summary reports PRs and comparisons without mutating workouts", () => {
  const current = workout({
    exercises: [{ name: "Bench", sets: [{ weight: "110", reps: "8" }] }],
  });
  const previous = workout({
    id: "previous",
    date: "2026-07-03",
    exercises: [{ name: "Bench", sets: [{ weight: "100", reps: "8" }] }],
  });
  const before = structuredClone(current);
  const summary = buildCompletionSummary(current, previous, [previous]);
  assert.equal(summary.volume, 880);
  assert.equal(summary.sets, 1);
  assert.ok(summary.highlights.some((item) => item.type === "PR"));
  assert.ok(summary.highlights.some((item) => item.type === "Volume"));
  assert.deepEqual(current, before);

  const emptySummary = buildCompletionSummary(workout(), null, []);
  assert.equal(emptySummary.highlights[0].type, "Saved");
});

test("saveability matches the existing exercise-presence rule", () => {
  assert.equal(hasSaveableWorkoutContent({ exercises: [] }), false);
  assert.equal(hasSaveableWorkoutContent({}), false);
  assert.equal(
    hasSaveableWorkoutContent({ exercises: [{ name: "Bench", sets: [] }] }),
    true,
  );
});
