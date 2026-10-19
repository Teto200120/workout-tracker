import assert from "node:assert/strict";
import test from "node:test";
import { buildWorkoutSessionRecord } from "../../src/js/domain/workout-session.js";
import {
  buildExerciseStats,
  completedSets,
  totalSets,
} from "../../src/js/domain/workout-metrics.js";
import { createLiveFeedbackTransport } from "../../src/js/application/feedback-transport.js";
import { createFeedbackService } from "../../src/js/application/feedback.js";
import { createFeedbackOutbox } from "../../src/js/storage/feedback-outbox.js";

test("session serialization preserves extension data, duration and typed identity without mutating inputs", () => {
  const input = {
    fields: {
      date: "2026-10-19",
      type: "Push",
      startTime: "12:00",
      endTime: "13:00",
      notes: " edited ",
    },
    exercises: [
      {
        name: " Bench ",
        notes: " cues ",
        extra: { keep: true },
        sets: [
          {
            weight: " 100 ",
            reps: " 5 ",
            rpe: "",
            done: true,
            warmup: false,
            extension: [1],
          },
          { weight: "", reps: "", rpe: "" },
        ],
      },
    ],
    original: {
      id: 0,
      startTime: "12:00",
      endTime: "13:00",
      durationMinutes: 47,
      tags: ["strong"],
      custom: { x: 1 },
      createdAt: "original",
    },
    recordId: 0,
    createdAt: "new",
  };
  const before = structuredClone(input);
  const saved = buildWorkoutSessionRecord(input);
  assert.equal(saved.id, 0);
  assert.equal(saved.durationMinutes, 47);
  assert.equal(saved.createdAt, "original");
  assert.equal(saved.exercises[0].sets.length, 1);
  assert.equal(saved.exercises[0].sets[0].weight, "100");
  assert.deepEqual(saved.custom, { x: 1 });
  saved.exercises[0].sets[0].extension.push(2);
  assert.deepEqual(input, before);
  assert.equal(
    buildWorkoutSessionRecord({ ...input, includeEmptySets: true }).exercises[0]
      .sets.length,
    2,
  );
});

test("exercise sessions count distinct workouts and working-set completion cannot exceed its denominator", () => {
  const work = {
    name: "Bench",
    sets: [
      { weight: 100, reps: 5, done: true },
      { weight: 20, reps: 5, done: true, warmup: true },
    ],
  };
  const workouts = [{ exercises: [work, structuredClone(work)] }];
  const [stats] = buildExerciseStats(workouts);
  assert.equal(stats.sessions, 1);
  assert.equal(stats.sets, 2);
  assert.equal(completedSets(workouts[0]), totalSets(workouts[0]));
});

test("feedback verification, network and body stalls finish with retryable failures", async () => {
  const never = () => new Promise(() => {});
  const stalledVerification = createLiveFeedbackTransport({
    getTurnstileToken: never,
    verificationTimeoutMs: 5,
  });
  assert.equal(
    (await stalledVerification.send({})).code,
    "verification_unavailable",
  );
  let signal;
  const stalledNetwork = createLiveFeedbackTransport({
    getTurnstileToken: async () => "token",
    requestTimeoutMs: 5,
    fetcher: async (_url, options) => {
      signal = options.signal;
      return never();
    },
  });
  assert.equal((await stalledNetwork.send({})).code, "network_failure");
  assert.equal(signal.aborted, true);
  const stalledBody = createLiveFeedbackTransport({
    getTurnstileToken: async () => "token",
    requestTimeoutMs: 5,
    fetcher: async () => ({ ok: true, json: never }),
  });
  assert.equal((await stalledBody.send({})).code, "network_failure");
});

test("queued feedback deletion waits through an in-flight retry rather than resurrecting a deleted report", async () => {
  const storage = new Map();
  const outbox = createFeedbackOutbox({
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
  });
  let finish;
  let entered;
  const started = new Promise((resolve) => {
    entered = resolve;
  });
  const service = createFeedbackService({
    outbox,
    createId: () => "123e4567-e89b-42d3-a456-426614174000",
    now: () => "2026-10-19T12:00:00.000Z",
    transport: {
      send: () => {
        entered();
        return new Promise((resolve) => {
          finish = resolve;
        });
      },
    },
  });
  const report = await service.queue({
    category: "bug",
    message: "This is a test report.",
    diagnostics: null,
    screenshot: null,
  });
  const pending = service.retry(report.id);
  await started;
  const deletion = service.remove(report.id);
  finish({ ok: false, code: "network_failure" });
  await pending;
  await deletion;
  assert.deepEqual(outbox.list(), []);
});
