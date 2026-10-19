import { withDeadline } from "./deadline.js";

export const FEEDBACK_RECEIVER_URL =
  "https://workout-tracker-beta-feedback.hector-workout-tracker.workers.dev";
export const FEEDBACK_TURNSTILE_SITE_KEY = "0x4AAAAAAERByALEgaItb07n";

const RECEIVER_FAILURE_CODES = new Set([
  "challenge_failed",
  "invalid_request",
  "network_failure",
  "origin_not_allowed",
  "rate_limited",
  "temporary_failure",
  "verification_unavailable",
]);

export function createLiveFeedbackTransport({
  getTurnstileToken,
  fetcher = globalThis.fetch,
  verificationTimeoutMs = 120_000,
  requestTimeoutMs = 20_000,
} = {}) {
  if (typeof getTurnstileToken !== "function") {
    throw new TypeError("A Turnstile token provider is required.");
  }
  if (typeof fetcher !== "function") {
    throw new TypeError("A fetch implementation is required.");
  }

  return Object.freeze({
    async send(report) {
      let turnstileToken;
      try {
        turnstileToken = await withDeadline(
          () => getTurnstileToken(),
          verificationTimeoutMs,
        );
      } catch {
        return { ok: false, code: "verification_unavailable" };
      }

      try {
        return await withDeadline(async (signal) => {
          const response = await fetcher(FEEDBACK_RECEIVER_URL, {
            method: "POST",
            signal,
            headers: { "content-type": "application/json" },
            body: JSON.stringify({ report, turnstileToken }),
          });
          try {
            const result = await response.json();
            if (response.ok && result?.ok === true) return { ok: true };
            return {
              ok: false,
              code: RECEIVER_FAILURE_CODES.has(result?.code)
                ? result.code
                : "temporary_failure",
            };
          } catch {
            if (signal.aborted) throw new Error("Request timed out.");
            return { ok: false, code: "invalid_response" };
          }
        }, requestTimeoutMs);
      } catch {
        return { ok: false, code: "network_failure" };
      }
    },
  });
}
