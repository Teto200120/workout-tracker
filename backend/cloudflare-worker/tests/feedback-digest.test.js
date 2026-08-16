import assert from "node:assert/strict";
import test from "node:test";
import { runFeedbackDigest } from "../src/feedback-digest.js";

const now = () => new Date("2026-08-15T23:00:00Z");
function env(rows = []) {
  const writes = [];
  return {
    RESEND_API_KEY: "secret",
    RESEND_FROM: "a@example.test",
    DIGEST_RECIPIENT: "b@example.test",
    D1_REVIEW_URL: "https://d1.example.test",
    R2_REVIEW_URL: "https://r2.example.test",
    FEEDBACK_DB: {
      prepare: () => ({
        all: async () => ({ results: rows }),
        bind: (...values) => ({ values }),
      }),
      batch: async (items) => writes.push(...items),
    },
    writes,
  };
}
test("does not send an empty digest", async () => {
  let calls = 0;
  const result = await runFeedbackDigest(env(), {
    now,
    fetcher: async () => {
      calls += 1;
    },
  });
  assert.deepEqual(result, { empty: true });
  assert.equal(calls, 0);
});
test("sends only aggregate-safe summary then marks reports", async () => {
  const value = env([{ id: "opaque", category: "bug", has_screenshot: 1 }]);
  let body;
  const result = await runFeedbackDigest(value, {
    now,
    fetcher: async (_url, request) => {
      body = request.body;
      return { ok: true };
    },
  });
  assert.equal(result.delivered, true);
  assert.equal(body.includes("opaque"), false);
  assert.equal(value.writes.length, 1);
});
test("provider failure leaves reports unmarked for retry", async () => {
  const value = env([{ id: "opaque", category: "idea", has_screenshot: 0 }]);
  const result = await runFeedbackDigest(value, {
    now,
    fetcher: async () => ({ ok: false }),
  });
  assert.deepEqual(result, { delivered: false });
  assert.equal(value.writes.length, 0);
});
