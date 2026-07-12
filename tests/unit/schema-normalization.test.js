import assert from "node:assert/strict";
import test from "node:test";
import {
  normalizeApplicationData,
  normalizeDraft,
  normalizeExercise,
  normalizeGoals,
  normalizeRoutine,
  normalizeSet,
  normalizeSettings,
  normalizeWorkout,
} from "../../src/js/schema/normalize.js";
import {
  canonicalDraft,
  canonicalExercise,
  canonicalRoutine,
  canonicalSet,
  canonicalWorkout,
  legacyApplicationData,
} from "../fixtures/schema-data.js";

test("normalization is non-mutating", () => {
  const source = legacyApplicationData();
  const before = structuredClone(source);
  const normalized = normalizeApplicationData(source);
  assert.deepEqual(source, before);
  assert.notStrictEqual(normalized, source);
  assert.notStrictEqual(normalized.workouts[0], source.workouts[0]);
});

test("set normalization preserves zero and unknown safe fields", () => {
  const source = {
    weight: 0,
    reps: 0,
    rpe: 0,
    done: 0,
    warmup: "true",
    metadata: { source: "legacy" },
  };
  const normalized = normalizeSet(source);
  assert.deepEqual(normalized, {
    weight: "0",
    reps: "0",
    rpe: "0",
    done: false,
    warmup: true,
    metadata: { source: "legacy" },
  });
  assert.notStrictEqual(normalized.metadata, source.metadata);
});

test("exercise and routine normalization trims names and preserves order", () => {
  const exercise = normalizeExercise(
    canonicalExercise({
      name: "  Press  ",
      sets: [canonicalSet({ weight: "1" }), canonicalSet({ weight: "2" })],
    }),
  );
  assert.equal(exercise.name, "Press");
  assert.deepEqual(
    exercise.sets.map((set) => set.weight),
    ["1", "2"],
  );

  const routine = normalizeRoutine(
    canonicalRoutine({
      name: "  Ordered  ",
      exercises: ["  First  ", "Second"],
    }),
  );
  assert.equal(routine.name, "Ordered");
  assert.deepEqual(routine.exercises, ["First", "Second"]);
});

test("workout and draft normalization preserve IDs and dates", () => {
  const workout = canonicalWorkout({ id: "keep-id", date: "2026-01-02" });
  const normalizedWorkout = normalizeWorkout(workout);
  assert.equal(normalizedWorkout.id, "keep-id");
  assert.equal(normalizedWorkout.date, "2026-01-02");

  const draft = normalizeDraft(
    canonicalDraft({
      id: "keep-draft-id",
      date: "2026-02-03",
      activeExerciseIndex: "2",
    }),
  );
  assert.equal(draft.id, "keep-draft-id");
  assert.equal(draft.date, "2026-02-03");
  assert.equal(draft.activeExerciseIndex, 2);
});

test("missing arrays and fields receive existing defaults", () => {
  const workout = normalizeWorkout({
    id: "legacy",
    date: "2026-01-01",
    type: "Legacy",
  });
  assert.deepEqual(workout.exercises, []);
  assert.deepEqual(workout.tags, []);
  assert.equal(workout.notes, "");
  assert.equal(workout.createdAt, "");

  const exercise = normalizeExercise({ name: "Exercise" });
  assert.deepEqual(exercise.sets, []);
  assert.equal(exercise.notes, "");
});

test("settings and goals apply current defaults while preserving compatibility fields", () => {
  const settings = normalizeSettings({
    schedule: {
      1: { kind: "gym", routine: "  Custom Routine  ", color: "blue" },
    },
    animations: "false",
    futureSetting: { retained: true },
  });
  assert.equal(settings.animations, false);
  assert.equal(settings.haptics, true);
  assert.equal(settings.schedule["1"].routine, "Custom Routine");
  assert.equal(settings.schedule["1"].color, "blue");
  assert.deepEqual(settings.futureSetting, { retained: true });

  const goals = normalizeGoals({
    weeklyGoal: "3",
    targetWeight: "170",
    futureGoal: 0,
  });
  assert.equal(goals.weeklyGoal, 3);
  assert.equal(goals.targetWeight, "170");
  assert.equal(goals.futureGoal, 0);
});

test("unknown nested fields survive full application normalization", () => {
  const source = legacyApplicationData();
  const normalized = normalizeApplicationData(source);
  assert.equal(normalized.workouts[0].legacyWorkoutField, "preserve");
  assert.deepEqual(normalized.workouts[0].exercises[0].futureExerciseField, {
    source: "local",
  });
  assert.equal(normalized.goals.legacyGoalField, true);
});
