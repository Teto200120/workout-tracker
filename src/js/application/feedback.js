import { createFeedbackReport, isFeedbackReport } from "../domain/feedback.js";

const TRANSPORT_FAILURES = new Set([
  "challenge_failed",
  "invalid_request",
  "network_failure",
  "origin_not_allowed",
  "rate_limited",
  "unavailable",
  "temporary_failure",
  "verification_unavailable",
  "invalid_response",
]);

function normalizeFailure(code) {
  return TRANSPORT_FAILURES.has(code) ? code : "invalid_response";
}

export function createFeedbackTransportPayload(report) {
  if (!isFeedbackReport(report)) {
    throw new TypeError("A valid feedback report is required.");
  }
  return structuredClone({
    schemaVersion: report.schemaVersion,
    id: report.id,
    category: report.category,
    message: report.message,
    diagnostics: report.diagnostics,
    screenshot: report.screenshot,
    createdAt: report.createdAt,
  });
}

export function createUnavailableFeedbackTransport() {
  return Object.freeze({
    async send() {
      return { ok: false, code: "unavailable" };
    },
  });
}

export function createMockFeedbackTransport(outcomes = ["success"]) {
  const calls = [];
  let index = 0;
  return Object.freeze({
    get calls() {
      return calls.map((report) => structuredClone(report));
    },
    async send(report) {
      calls.push(structuredClone(report));
      const outcome = outcomes[Math.min(index, outcomes.length - 1)];
      index += 1;
      if (outcome === "throw") throw new Error("Mock transport failure");
      if (outcome === "failure") {
        return { ok: false, code: "temporary_failure" };
      }
      return { ok: true };
    },
  });
}

export function createFeedbackService({
  outbox,
  transport,
  now = () => new Date().toISOString(),
  createId,
}) {
  if (!outbox || typeof outbox.save !== "function") {
    throw new TypeError("A feedback outbox is required.");
  }
  if (!transport || typeof transport.send !== "function") {
    throw new TypeError("A feedback transport is required.");
  }

  async function retry(id) {
    const current = outbox.get(id);
    if (!current) throw new Error("That pending report no longer exists.");
    const attemptedAt = now();
    const attempted = {
      ...current,
      attempts: current.attempts + 1,
      lastAttemptAt: attemptedAt,
      updatedAt: attemptedAt,
      lastFailure: null,
    };

    // Persist the attempt before transport work. A crash or failed request never
    // drops the user's only local copy.
    outbox.save(attempted);

    let result;
    try {
      result = await transport.send(createFeedbackTransportPayload(attempted));
    } catch {
      result = { ok: false, code: "temporary_failure" };
    }

    if (result?.ok === true) {
      try {
        outbox.remove(id);
      } catch (error) {
        return {
          sent: false,
          code: "local_cleanup_failed",
          report: outbox.get(id) || attempted,
          error,
        };
      }
      return { sent: true, report: attempted };
    }

    const pending = {
      ...attempted,
      lastFailure: normalizeFailure(result?.code),
    };
    outbox.save(pending);
    return { sent: false, code: pending.lastFailure, report: pending };
  }

  function queue(draft) {
    const report = createFeedbackReport(draft, { now, createId });
    outbox.save(report);
    return report;
  }

  return Object.freeze({
    list() {
      return outbox.list();
    },
    queue,
    async submit(draft) {
      const report = queue(draft);
      return retry(report.id);
    },
    retry,
    remove(id) {
      return outbox.remove(id);
    },
  });
}
