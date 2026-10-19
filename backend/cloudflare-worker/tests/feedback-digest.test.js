import assert from "node:assert/strict";
import test from "node:test";
import { runFeedbackDigest } from "../src/feedback-digest.js";
import { database } from "./sqlite-fixture.js";
const now = () => new Date("2026-08-15T23:00:00Z");
function env(count = 0) {
  const FEEDBACK_DB = database();
  for (let i = 0; i < count; i += 1)
    FEEDBACK_DB.db
      .prepare(
        "INSERT INTO feedback_reports (id, category, message, client_created_at, received_at) VALUES (?, 'bug', 'private message', 'today', 'today')",
      )
      .run(`report-${i}`);
  return {
    RESEND_API_KEY: "secret",
    RESEND_FROM: "a@example.test",
    DIGEST_RECIPIENT: "b@example.test",
    D1_REVIEW_URL: "https://d1.example.test",
    R2_REVIEW_URL: "https://r2.example.test",
    FEEDBACK_DB,
  };
}
test("does not send an empty digest", async () => {
  assert.deepEqual(
    await runFeedbackDigest(env(), {
      now,
      fetcher: () => assert.fail("empty send"),
    }),
    { empty: true },
  );
});
test("sends only aggregate-safe summary and atomically marks the batch", async () => {
  const value = env(1);
  let body;
  const result = await runFeedbackDigest(value, {
    now,
    fetcher: async (_url, request) => {
      body = request.body;
      assert.equal(
        request.headers["Idempotency-Key"],
        "workout-feedback/2026-08-15",
      );
      return { ok: true };
    },
  });
  assert.equal(result.delivered, true);
  assert.equal(body.includes("report-0"), false);
  assert.equal(body.includes("private message"), false);
  assert.equal(
    value.FEEDBACK_DB.db
      .prepare("SELECT COUNT(*) AS count FROM feedback_digest_deliveries")
      .get().count,
    1,
  );
  assert.deepEqual(
    await runFeedbackDigest(value, {
      now,
      fetcher: () => assert.fail("duplicate send"),
    }),
    { skipped: true },
  );
});
test("an ambiguous send retries the frozen batch with the same provider key", async () => {
  const value = env(1);
  const requests = [];
  const fetcher = async (_url, request) => {
    requests.push(request);
    return { ok: true };
  };
  const batch = value.FEEDBACK_DB.batch;
  value.FEEDBACK_DB.batch = async () => {
    throw new Error("D1 unavailable after email");
  };
  await assert.rejects(runFeedbackDigest(value, { now, fetcher }));
  value.FEEDBACK_DB.db
    .prepare(
      "INSERT INTO feedback_reports (id, category, message, client_created_at, received_at) VALUES ('later', 'idea', 'later private message', 'today', 'today')",
    )
    .run();
  value.FEEDBACK_DB.batch = batch;
  await runFeedbackDigest(value, { now, fetcher });
  assert.equal(requests[0].body, requests[1].body);
  assert.equal(
    requests[0].headers["Idempotency-Key"],
    requests[1].headers["Idempotency-Key"],
  );
  assert.equal(
    value.FEEDBACK_DB.db
      .prepare("SELECT COUNT(*) AS count FROM feedback_digest_deliveries")
      .get().count,
    1,
  );
});
test("provider failure leaves the batch unmarked and old ambiguity blocks later batches", async () => {
  const value = env(1);
  assert.deepEqual(
    await runFeedbackDigest(value, {
      now,
      fetcher: async () => ({ ok: false }),
    }),
    { delivered: false },
  );
  const nextDay = () => new Date("2026-08-16T23:00:00Z");
  assert.deepEqual(
    await runFeedbackDigest(value, {
      now: nextDay,
      fetcher: () => assert.fail("unsafe resend"),
    }),
    { delivered: false, code: "reconciliation_required" },
  );
});
test("concurrent cron invocations use the identical durable batch", async () => {
  const value = env(2);
  const requests = [];
  await Promise.all(
    [1, 2].map(() =>
      runFeedbackDigest(value, {
        now,
        fetcher: async (_url, options) => {
          requests.push(options);
          return { ok: true };
        },
      }),
    ),
  );
  assert.equal(requests[0].body, requests[1].body);
  assert.equal(
    requests[0].headers["Idempotency-Key"],
    requests[1].headers["Idempotency-Key"],
  );
  assert.equal(
    value.FEEDBACK_DB.db
      .prepare("SELECT COUNT(*) AS count FROM feedback_digest_deliveries")
      .get().count,
    2,
  );
});
