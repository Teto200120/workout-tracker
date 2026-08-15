import { createFeedbackTransportPayload } from "./feedback.js";

export const FEEDBACK_RECEIVER_URL =
  "https://workout-tracker-beta-feedback.hector-workout-tracker.workers.dev";
export const FEEDBACK_TURNSTILE_SITE_KEY = "0x4AAAAAAERByALEgaItb07n";

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
      const turnstileToken = await getTurnstileToken();
      const response = await fetcher(FEEDBACK_RECEIVER_URL, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          report: createFeedbackTransportPayload(report),
          turnstileToken,
        }),
      });
      try {
        const result = await response.json();
        return response.ok && result?.ok === true
          ? { ok: true }
          : { ok: false, code: "temporary_failure" };
      } catch {
        return { ok: false, code: "invalid_response" };
      }
    },
  });
}
