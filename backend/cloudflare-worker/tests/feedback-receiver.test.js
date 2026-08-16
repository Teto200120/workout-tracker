/* global ReadableStream, Request, Response */

import assert from "node:assert/strict";
import test from "node:test";
import { createFeedbackReceiver } from "../src/feedback-receiver.js";

const ORIGIN = "https://beta.example.test";
const REPORT = Object.freeze({
  schemaVersion: 1,
  id: "123e4567-e89b-42d3-a456-426614174000",
  category: "bug",
  message: "The save button did not respond.",
  diagnostics: {
    appVersion: "0.4.0",
    viewport: "412x915",
    displayMode: "browser",
    connection: "online",
    currentScreen: "feedback",
  },
  screenshot: null,
  createdAt: "2026-08-15T12:00:00.000Z",
});

function createEnvironment({ limited = false } = {}) {
  const rows = new Map();
  const claims = new Map();
  const objects = new Map();
  let deletedObjectKey = null;
  return {
    ALLOWED_ORIGIN: ORIGIN,
    TURNSTILE_SECRET: "test-secret",
    FEEDBACK_RATE_LIMITER: {
      async limit() {
        return { success: !limited };
      },
    },
    FEEDBACK_SCREENSHOTS: {
      async put(key, value, options) {
        objects.set(key, { value, options });
      },
      async delete(key) {
        deletedObjectKey = key;
        objects.delete(key);
      },
    },
    FEEDBACK_DB: {
      prepare(query) {
        return {
          bind(...values) {
            return {
              async first() {
                return rows.get(values[0]) || null;
              },
              async run() {
                const [id] = values;
                if (query.startsWith("INSERT INTO feedback_report_claims")) {
                  const [, claimedAt, reclaimBefore] = values;
                  if (claims.has(id) && claims.get(id) > reclaimBefore) {
                    return { success: true, meta: { changes: 0 } };
                  }
                  claims.set(id, claimedAt);
                  return { success: true, meta: { changes: 1 } };
                }
                if (query.startsWith("DELETE FROM feedback_report_claims")) {
                  if (!rows.has(id)) claims.delete(id);
                  return { success: true, meta: { changes: 1 } };
                }
                if (query.startsWith("INSERT INTO feedback_reports")) {
                  rows.set(id, { id, values, query });
                }
                return { success: true, meta: { changes: 1 } };
              },
            };
          },
        };
      },
    },
    rows,
    claims,
    objects,
    get deletedObjectKey() {
      return deletedObjectKey;
    },
  };
}

function request(body, options = {}) {
  return new Request("https://receiver.example.test/feedback", {
    method: "POST",
    headers: {
      origin: options.origin || ORIGIN,
      "content-type": "application/json",
      "cf-connecting-ip": "198.51.100.10",
    },
    body: JSON.stringify(body),
  });
}

function receiver({ verified = true, log, nowMs } = {}) {
  return createFeedbackReceiver({
    now: () => "2026-08-15T12:05:00.000Z",
    ...(nowMs ? { nowMs } : {}),
    ...(log ? { log } : {}),
    async fetcher() {
      return Response.json({ success: verified });
    },
  });
}

test("stores only a validated, Turnstile-verified report in D1", async () => {
  const env = createEnvironment();
  const response = await receiver().fetch(
    request({ report: REPORT, turnstileToken: "token" }),
    env,
  );

  assert.equal(response.status, 201);
  assert.deepEqual(await response.json(), { ok: true });
  assert.equal(env.rows.get(REPORT.id).values[1], "bug");
  assert.equal(env.rows.get(REPORT.id).values[2], REPORT.message);
  assert.equal(
    env.rows.get(REPORT.id).values.at(-1),
    "2026-08-15T12:05:00.000Z",
  );
  assert.equal(env.objects.size, 0);
});

test("accepts a queued legacy diagnostic version without relabeling it", async () => {
  const env = createEnvironment();
  const report = {
    ...REPORT,
    id: "123e4567-e89b-42d3-a456-426614174001",
    diagnostics: { ...REPORT.diagnostics, appVersion: "1.0.0" },
  };
  const response = await receiver().fetch(
    request({ report, turnstileToken: "token" }),
    env,
  );

  assert.equal(response.status, 201);
  assert.equal(env.rows.get(report.id).values[3], JSON.stringify(report.diagnostics));
});

test("rejects an origin mismatch before request parsing or persistence", async () => {
  const env = createEnvironment();
  const response = await receiver().fetch(
    request(
      { report: REPORT, turnstileToken: "token" },
      { origin: "https://other.test" },
    ),
    env,
  );

  assert.equal(response.status, 403);
  assert.deepEqual(await response.json(), {
    ok: false,
    code: "origin_not_allowed",
  });
  assert.equal(env.rows.size, 0);
});

test("allows an exact-origin preflight without exposing a report endpoint", async () => {
  const env = createEnvironment();
  const response = await receiver().fetch(
    new Request("https://receiver.example.test/feedback", {
      method: "OPTIONS",
      headers: { origin: ORIGIN },
    }),
    env,
  );

  assert.equal(response.status, 204);
  assert.equal(response.headers.get("access-control-allow-origin"), ORIGIN);
  assert.equal(
    response.headers.get("access-control-allow-methods"),
    "POST, OPTIONS",
  );
  assert.equal(env.rows.size, 0);
});

test("rejects a failed Turnstile verification without writing a report", async () => {
  const env = createEnvironment();
  const response = await receiver({ verified: false }).fetch(
    request({ report: REPORT, turnstileToken: "token" }),
    env,
  );

  assert.equal(response.status, 403);
  assert.deepEqual(await response.json(), {
    ok: false,
    code: "challenge_failed",
  });
  assert.equal(env.rows.size, 0);
});

test("rate limiting and malformed payloads do not persist data", async () => {
  const limited = createEnvironment({ limited: true });
  const rateResponse = await receiver().fetch(
    request({ report: REPORT, turnstileToken: "token" }),
    limited,
  );
  assert.equal(rateResponse.status, 429);
  assert.equal(limited.rows.size, 0);

  const malformed = createEnvironment();
  const badResponse = await receiver().fetch(
    request({
      report: { ...REPORT, diagnostics: { location: "no" } },
      turnstileToken: "token",
    }),
    malformed,
  );
  assert.equal(badResponse.status, 400);
  assert.equal(malformed.rows.size, 0);

  const unknownRelease = createEnvironment();
  const unknownReleaseResponse = await receiver().fetch(
    request({
      report: {
        ...REPORT,
        diagnostics: { ...REPORT.diagnostics, appVersion: "0.3.9" },
      },
      turnstileToken: "token",
    }),
    unknownRelease,
  );
  assert.equal(unknownReleaseResponse.status, 400);
  assert.equal(unknownRelease.rows.size, 0);
});

test("rejects oversized request bodies before challenge or storage work", async () => {
  const env = createEnvironment();
  const response = await receiver().fetch(
    new Request("https://receiver.example.test/feedback", {
      method: "POST",
      headers: {
        origin: ORIGIN,
        "content-type": "application/json",
        "content-length": String(601 * 1024),
      },
      body: JSON.stringify({ report: REPORT, turnstileToken: "token" }),
    }),
    env,
  );

  assert.equal(response.status, 413);
  assert.equal(env.rows.size, 0);
});

test("stops a chunked oversized body before challenge or storage work", async () => {
  const env = createEnvironment();
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(new Uint8Array(400 * 1024));
      controller.enqueue(new Uint8Array(201 * 1024));
      controller.close();
    },
  });
  const response = await receiver().fetch(
    new Request("https://receiver.example.test/feedback", {
      method: "POST",
      headers: { origin: ORIGIN, "content-type": "application/json" },
      body: stream,
      duplex: "half",
    }),
    env,
  );

  assert.equal(response.status, 413);
  assert.equal(env.rows.size, 0);
});

test("an optional screenshot is decoded into private R2 and duplicate retries stay idempotent", async () => {
  const env = createEnvironment();
  const screenshot = {
    dataUrl: "data:image/png;base64,AQID",
    mimeType: "image/png",
    width: 1,
    height: 1,
    size: 3,
  };
  const body = { report: { ...REPORT, screenshot }, turnstileToken: "token" };

  const first = await receiver().fetch(request(body), env);
  assert.equal(first.status, 201);
  assert.equal(env.objects.size, 1);
  assert.equal([...env.objects.values()][0].value.byteLength, 3);
  const retried = await receiver().fetch(request(body), env);
  assert.equal(retried.status, 200);
  assert.deepEqual(await retried.json(), { ok: true, duplicate: true });
  assert.equal(env.rows.size, 1);
});

test("a storage failure compensates a screenshot write and is not acknowledged", async () => {
  const env = createEnvironment();
  const logs = [];
  const prepare = env.FEEDBACK_DB.prepare;
  env.FEEDBACK_DB.prepare = (query) => {
    if (!query.startsWith("INSERT INTO feedback_reports"))
      return prepare(query);
    return {
      bind() {
        return {
          async run() {
            throw new Error("D1 unavailable");
          },
        };
      },
    };
  };
  const screenshot = {
    dataUrl: "data:image/png;base64,AQID",
    mimeType: "image/png",
    width: 1,
    height: 1,
    size: 3,
  };
  const response = await receiver({ log: (...args) => logs.push(args) }).fetch(
    request({ report: { ...REPORT, screenshot }, turnstileToken: "token" }),
    env,
  );

  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    ok: false,
    code: "temporary_failure",
  });
  assert.equal(env.objects.size, 0);
  assert.equal(env.claims.has(REPORT.id), false);
  assert.deepEqual(logs, [["feedback_delivery_failure", "report"]]);
});

test("a concurrent duplicate cannot delete the winning screenshot", async () => {
  const env = createEnvironment();
  const screenshot = {
    dataUrl: "data:image/png;base64,AQID",
    mimeType: "image/png",
    width: 1,
    height: 1,
    size: 3,
  };
  const body = { report: { ...REPORT, screenshot }, turnstileToken: "token" };
  const originalPut = env.FEEDBACK_SCREENSHOTS.put;
  let signalPutStarted;
  let allowPut;
  const putStarted = new Promise((resolve) => {
    signalPutStarted = resolve;
  });
  const putAllowed = new Promise((resolve) => {
    allowPut = resolve;
  });
  env.FEEDBACK_SCREENSHOTS.put = async (...args) => {
    signalPutStarted();
    await putAllowed;
    return originalPut(...args);
  };

  const winner = receiver().fetch(request(body), env);
  await putStarted;
  const duplicate = await receiver().fetch(request(body), env);
  assert.equal(duplicate.status, 503);
  assert.deepEqual(await duplicate.json(), {
    ok: false,
    code: "temporary_failure",
  });
  assert.equal(env.deletedObjectKey, null);

  allowPut();
  const accepted = await winner;
  assert.equal(accepted.status, 201);
  assert.equal(env.objects.size, 1);
  assert.equal(
    [...env.objects.keys()].some((key) =>
      key.startsWith(`feedback/${REPORT.id}/`),
    ),
    true,
  );
});

test("reclaims an abandoned claim after its bounded lease expires", async () => {
  const env = createEnvironment();
  const nowMs = 1_000_000;
  env.claims.set(REPORT.id, nowMs - 5 * 60 * 1000);
  const response = await receiver({ nowMs: () => nowMs }).fetch(
    request({ report: REPORT, turnstileToken: "token" }),
    env,
  );

  assert.equal(response.status, 201);
  assert.equal(env.rows.size, 1);
  assert.equal(env.claims.get(REPORT.id), nowMs);
});
