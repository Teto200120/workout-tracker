/* global Headers, Response, TextDecoder, URLSearchParams, atob, fetch */

const CATEGORIES = new Set(["bug", "idea", "other"]);
const SCREENSHOT_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const DIAGNOSTIC_KEYS = [
  "appVersion",
  "viewport",
  "displayMode",
  "connection",
  "currentScreen",
];
const MAX_MESSAGE_LENGTH = 2000;
const MAX_SCREENSHOT_BYTES = 425 * 1024;
const MAX_SCREENSHOT_EDGE = 1280;
const MAX_REQUEST_BYTES = 600 * 1024;
const TURNSTILE_VERIFY_URL =
  "https://challenges.cloudflare.com/turnstile/v0/siteverify";

function json(body, status, origin) {
  const headers = new Headers({ "content-type": "application/json" });
  if (origin) headers.set("access-control-allow-origin", origin);
  if (origin) headers.set("vary", "Origin");
  return new Response(JSON.stringify(body), { status, headers });
}

function corsHeaders(origin) {
  return new Headers({
    "access-control-allow-origin": origin,
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "content-type",
    "access-control-max-age": "600",
    vary: "Origin",
  });
}

function validIsoDate(value) {
  return typeof value === "string" && !Number.isNaN(Date.parse(value));
}

function hasOnlyKeys(value, allowed) {
  return (
    value &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    Object.keys(value).every((key) => allowed.includes(key))
  );
}

function validDiagnostics(diagnostics) {
  if (diagnostics === null) return true;
  return (
    hasOnlyKeys(diagnostics, DIAGNOSTIC_KEYS) &&
    DIAGNOSTIC_KEYS.every((key) => key in diagnostics) &&
    diagnostics.appVersion === "1.0.0" &&
    (/^\d{1,5}x\d{1,5}$/u.test(diagnostics.viewport) ||
      diagnostics.viewport === "unknown") &&
    ["browser", "installed"].includes(diagnostics.displayMode) &&
    ["online", "offline"].includes(diagnostics.connection) &&
    diagnostics.currentScreen === "feedback"
  );
}

function decodeScreenshot(screenshot) {
  if (screenshot === null) return null;
  if (
    !hasOnlyKeys(screenshot, ["dataUrl", "mimeType", "width", "height", "size"]) ||
    !SCREENSHOT_TYPES.has(screenshot.mimeType) ||
    !Number.isInteger(screenshot.width) ||
    !Number.isInteger(screenshot.height) ||
    screenshot.width < 1 ||
    screenshot.height < 1 ||
    screenshot.width > MAX_SCREENSHOT_EDGE ||
    screenshot.height > MAX_SCREENSHOT_EDGE ||
    !Number.isInteger(screenshot.size) ||
    screenshot.size < 1 ||
    screenshot.size > MAX_SCREENSHOT_BYTES ||
    typeof screenshot.dataUrl !== "string"
  ) {
    return undefined;
  }
  const prefix = `data:${screenshot.mimeType};base64,`;
  if (
    !screenshot.dataUrl.startsWith(prefix) ||
    screenshot.dataUrl.length > prefix.length + Math.ceil((MAX_SCREENSHOT_BYTES * 4) / 3) + 4
  ) {
    return undefined;
  }
  try {
    const bytes = Uint8Array.from(atob(screenshot.dataUrl.slice(prefix.length)), (char) =>
      char.charCodeAt(0),
    );
    return bytes.byteLength === screenshot.size && bytes.byteLength <= MAX_SCREENSHOT_BYTES
      ? bytes
      : undefined;
  } catch {
    return undefined;
  }
}

function validReport(report) {
  if (
    !hasOnlyKeys(report, [
      "schemaVersion",
      "id",
      "category",
      "message",
      "diagnostics",
      "screenshot",
      "createdAt",
    ]) ||
    report.schemaVersion !== 1 ||
    typeof report.id !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(
      report.id,
    ) ||
    !CATEGORIES.has(report.category) ||
    typeof report.message !== "string" ||
    report.message.trim().length < 10 ||
    report.message.trim().length > MAX_MESSAGE_LENGTH ||
    !validIsoDate(report.createdAt) ||
    !validDiagnostics(report.diagnostics)
  ) {
    return false;
  }
  return decodeScreenshot(report.screenshot) !== undefined;
}

async function verifyTurnstile(token, secret, fetcher) {
  if (typeof token !== "string" || !token || typeof secret !== "string" || !secret) {
    return false;
  }
  try {
    const response = await fetcher(TURNSTILE_VERIFY_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ secret, response: token }),
    });
    return response.ok && (await response.json()).success === true;
  } catch {
    return false;
  }
}

function screenshotKey(id, mimeType) {
  const extension = mimeType === "image/jpeg" ? "jpg" : mimeType.slice(6);
  return `feedback/${id}/screenshot.${extension}`;
}

async function readBoundedBody(request) {
  if (!request.body) return new Uint8Array();
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_REQUEST_BYTES) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

export function createFeedbackReceiver({ fetcher = fetch, now = () => new Date().toISOString() } = {}) {
  return {
    async fetch(request, env) {
      const origin = request.headers.get("origin");
      if (!env.ALLOWED_ORIGIN || origin !== env.ALLOWED_ORIGIN) {
        return json({ ok: false, code: "origin_not_allowed" }, 403);
      }
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: corsHeaders(origin) });
      }
      if (request.method !== "POST") {
        return json({ ok: false, code: "method_not_allowed" }, 405, origin);
      }
      if (!request.headers.get("content-type")?.startsWith("application/json")) {
        return json({ ok: false, code: "invalid_request" }, 415, origin);
      }
      const contentLength = Number(request.headers.get("content-length"));
      if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
        return json({ ok: false, code: "invalid_request" }, 413, origin);
      }

      let body;
      let screenshotObjectKey = null;
      try {
        const bodyBytes = await readBoundedBody(request);
        if (!bodyBytes) {
          return json({ ok: false, code: "invalid_request" }, 413, origin);
        }
        body = JSON.parse(new TextDecoder().decode(bodyBytes));
      } catch {
        return json({ ok: false, code: "invalid_request" }, 400, origin);
      }
      if (!hasOnlyKeys(body, ["report", "turnstileToken"]) || !validReport(body.report)) {
        return json({ ok: false, code: "invalid_request" }, 400, origin);
      }

      const rateKey = request.headers.get("cf-connecting-ip") || "unknown-client";
      const rate = await env.FEEDBACK_RATE_LIMITER.limit({ key: rateKey });
      if (!rate.success) return json({ ok: false, code: "rate_limited" }, 429, origin);
      if (!(await verifyTurnstile(body.turnstileToken, env.TURNSTILE_SECRET, fetcher))) {
        return json({ ok: false, code: "challenge_failed" }, 403, origin);
      }

      const report = body.report;
      try {
        const existing = await env.FEEDBACK_DB.prepare(
          "SELECT id FROM feedback_reports WHERE id = ?",
        )
          .bind(report.id)
          .first();
        if (existing) return json({ ok: true, duplicate: true }, 200, origin);

        const claim = await env.FEEDBACK_DB.prepare(
          "INSERT OR IGNORE INTO feedback_report_claims (id) VALUES (?)",
        )
          .bind(report.id)
          .run();
        if (!claim.meta.changes) {
          return json({ ok: false, code: "temporary_failure" }, 503, origin);
        }

        const screenshotBytes = decodeScreenshot(report.screenshot);
        if (screenshotBytes) {
          screenshotObjectKey = screenshotKey(report.id, report.screenshot.mimeType);
          await env.FEEDBACK_SCREENSHOTS.put(screenshotObjectKey, screenshotBytes, {
            httpMetadata: { contentType: report.screenshot.mimeType },
          });
        }

        await env.FEEDBACK_DB.prepare(
          "INSERT INTO feedback_reports (id, category, message, diagnostics_json, screenshot_key, screenshot_mime_type, screenshot_width, screenshot_height, client_created_at, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
          .bind(
            report.id,
            report.category,
            report.message.trim(),
            report.diagnostics === null ? null : JSON.stringify(report.diagnostics),
            screenshotObjectKey,
            report.screenshot?.mimeType || null,
            report.screenshot?.width || null,
            report.screenshot?.height || null,
            report.createdAt,
            now(),
          )
          .run();
      } catch {
        // A failed cross-store write is not delivery. The client keeps its local
        // report and may retry its same deterministic ID without data loss.
        if (screenshotObjectKey) {
          try {
            await env.FEEDBACK_SCREENSHOTS.delete(screenshotObjectKey);
          } catch {
            // A later retry safely overwrites this private deterministic key.
          }
        }
        await env.FEEDBACK_DB.prepare(
          "DELETE FROM feedback_report_claims WHERE id = ? AND NOT EXISTS (SELECT 1 FROM feedback_reports WHERE id = ?)",
        )
          .bind(report.id, report.id)
          .run();
        return json({ ok: false, code: "temporary_failure" }, 503, origin);
      }
      return json({ ok: true }, 201, origin);
    },
  };
}

export default createFeedbackReceiver();
