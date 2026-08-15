import { createFeedbackTransportPayload } from "./feedback.js";

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
        turnstileToken = await getTurnstileToken();
      } catch {
        return { ok: false, code: "verification_unavailable" };
      }

      let response;
      try {
        response = await fetcher(FEEDBACK_RECEIVER_URL, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({
            report: createFeedbackTransportPayload(report),
            turnstileToken,
          }),
        });
      } catch {
        return { ok: false, code: "network_failure" };
      }

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
        return { ok: false, code: "invalid_response" };
      }
    },
  });
}
