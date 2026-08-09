export const FEEDBACK_CATEGORIES = Object.freeze(["bug", "idea", "other"]);
export const FEEDBACK_MESSAGE_MIN_LENGTH = 10;
export const FEEDBACK_MESSAGE_MAX_LENGTH = 2000;
export const FEEDBACK_SCREENSHOT_MAX_SOURCE_BYTES = 8 * 1024 * 1024;
export const FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES = 425 * 1024;
export const FEEDBACK_SCREENSHOT_TYPES = Object.freeze([
  "image/jpeg",
  "image/png",
  "image/webp",
]);

export const FEEDBACK_DIAGNOSTIC_ALLOWLIST = Object.freeze({
  appVersion: "App version",
  viewport: "Screen size",
  displayMode: "Browser or installed app",
  connection: "Online or offline at save time",
  currentScreen: "Feedback screen",
});

const FEEDBACK_DIAGNOSTIC_KEYS = Object.freeze(
  Object.keys(FEEDBACK_DIAGNOSTIC_ALLOWLIST),
);
const FEEDBACK_SCREENS = new Set(["feedback"]);
const UUID_V4_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
let fallbackIdSequence = 0;

function finiteDimension(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? Math.round(number) : 0;
}

function fallbackRandomByte(random) {
  const value = Number(random());
  if (!Number.isFinite(value)) return 0;
  return Math.floor(Math.abs(value % 1) * 256);
}

function fillPortableFallbackBytes(bytes, now, random) {
  let timestamp = Math.max(0, Math.floor(Number(now()) || 0));
  for (let index = 5; index >= 0; index -= 1) {
    bytes[index] = timestamp % 256;
    timestamp = Math.floor(timestamp / 256);
  }

  fallbackIdSequence = (fallbackIdSequence + 1) % 4096;
  bytes[6] = fallbackIdSequence >> 8;
  bytes[7] = fallbackIdSequence & 255;
  for (let index = 8; index < bytes.length; index += 1) {
    bytes[index] = fallbackRandomByte(random);
  }
}

function uuidFromBytes(bytes) {
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0"));
  return [
    hex.slice(0, 4).join(""),
    hex.slice(4, 6).join(""),
    hex.slice(6, 8).join(""),
    hex.slice(8, 10).join(""),
    hex.slice(10, 16).join(""),
  ].join("-");
}

export function createFeedbackReportId({
  cryptoObject = globalThis.crypto,
  now = () => Date.now(),
  random = () => Math.random(),
} = {}) {
  if (typeof cryptoObject?.randomUUID === "function") {
    try {
      const nativeId = cryptoObject.randomUUID();
      if (UUID_V4_PATTERN.test(nativeId)) return nativeId.toLowerCase();
    } catch {
      // Some mobile browsers expose randomUUID but reject it outside a secure context.
    }
  }

  const bytes = new Uint8Array(16);
  if (typeof cryptoObject?.getRandomValues === "function") {
    cryptoObject.getRandomValues(bytes);
  } else {
    // IDs are local correlation keys, not secrets. Time plus a per-page sequence
    // keeps this last resort distinct even if Math.random repeats.
    fillPortableFallbackBytes(bytes, now, random);
  }
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  return uuidFromBytes(bytes);
}

export function collectFeedbackDiagnostics(context = {}) {
  const width = finiteDimension(context.viewportWidth);
  const height = finiteDimension(context.viewportHeight);
  return Object.freeze({
    appVersion: "1.0.0",
    viewport: width && height ? `${width}x${height}` : "unknown",
    displayMode: context.standalone === true ? "installed" : "browser",
    connection: context.online === false ? "offline" : "online",
    currentScreen: FEEDBACK_SCREENS.has(context.currentScreen)
      ? context.currentScreen
      : "feedback",
  });
}

export function validateFeedbackDraft(draft) {
  const category = String(draft?.category || "");
  const message = String(draft?.message || "").trim();
  if (!FEEDBACK_CATEGORIES.includes(category)) {
    return { valid: false, message: "Choose a feedback type." };
  }
  if (message.length < FEEDBACK_MESSAGE_MIN_LENGTH) {
    return {
      valid: false,
      message: `Add at least ${FEEDBACK_MESSAGE_MIN_LENGTH} characters so the report is useful.`,
    };
  }
  if (message.length > FEEDBACK_MESSAGE_MAX_LENGTH) {
    return {
      valid: false,
      message: `Keep feedback to ${FEEDBACK_MESSAGE_MAX_LENGTH} characters or fewer.`,
    };
  }
  return { valid: true, category, message };
}

export function validateFeedbackScreenshotFile(file) {
  if (!file) return { valid: true };
  if (!FEEDBACK_SCREENSHOT_TYPES.includes(file.type)) {
    return {
      valid: false,
      message: "Choose a PNG, JPEG, or WebP screenshot.",
    };
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    return { valid: false, message: "That screenshot is empty or unreadable." };
  }
  if (file.size > FEEDBACK_SCREENSHOT_MAX_SOURCE_BYTES) {
    return {
      valid: false,
      message: "Choose a screenshot smaller than 8 MB.",
    };
  }
  return { valid: true };
}

function hasOnlyKeys(value, allowedKeys) {
  return (
    value &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    Object.keys(value).every((key) => allowedKeys.includes(key))
  );
}

function validScreenshot(screenshot) {
  if (screenshot === null) return true;
  if (
    !hasOnlyKeys(screenshot, [
      "dataUrl",
      "mimeType",
      "width",
      "height",
      "size",
    ]) ||
    !FEEDBACK_SCREENSHOT_TYPES.includes(screenshot.mimeType) ||
    !Number.isInteger(screenshot.width) ||
    screenshot.width <= 0 ||
    !Number.isInteger(screenshot.height) ||
    screenshot.height <= 0 ||
    !Number.isInteger(screenshot.size) ||
    screenshot.size <= 0 ||
    screenshot.size > FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES ||
    screenshot.width > MAX_SCREENSHOT_STORED_EDGE ||
    screenshot.height > MAX_SCREENSHOT_STORED_EDGE
  ) {
    return false;
  }
  return (
    typeof screenshot.dataUrl === "string" &&
    screenshot.dataUrl.startsWith(`data:${screenshot.mimeType};base64,`) &&
    screenshot.dataUrl.length <= FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES * 1.4 + 64
  );
}

const MAX_SCREENSHOT_STORED_EDGE = 1280;

function validDiagnostics(diagnostics) {
  if (!hasOnlyKeys(diagnostics, FEEDBACK_DIAGNOSTIC_KEYS)) return false;
  return (
    diagnostics.appVersion === "1.0.0" &&
    (/^\d{1,5}x\d{1,5}$/u.test(diagnostics.viewport) ||
      diagnostics.viewport === "unknown") &&
    ["browser", "installed"].includes(diagnostics.displayMode) &&
    ["online", "offline"].includes(diagnostics.connection) &&
    diagnostics.currentScreen === "feedback"
  );
}

export function isFeedbackReport(report) {
  if (
    !hasOnlyKeys(report, [
      "schemaVersion",
      "id",
      "category",
      "message",
      "diagnostics",
      "screenshot",
      "createdAt",
      "updatedAt",
      "attempts",
      "lastAttemptAt",
      "lastFailure",
    ]) ||
    report.schemaVersion !== 1 ||
    typeof report.id !== "string" ||
    !report.id ||
    !FEEDBACK_CATEGORIES.includes(report.category) ||
    typeof report.message !== "string" ||
    !validateFeedbackDraft(report).valid ||
    !Number.isInteger(report.attempts) ||
    report.attempts < 0 ||
    typeof report.createdAt !== "string" ||
    typeof report.updatedAt !== "string" ||
    (report.lastAttemptAt !== null && typeof report.lastAttemptAt !== "string") ||
    (report.lastFailure !== null &&
      !["unavailable", "temporary_failure", "invalid_response"].includes(
        report.lastFailure,
      )) ||
    !validScreenshot(report.screenshot)
  ) {
    return false;
  }
  if (report.diagnostics === null) return true;
  return validDiagnostics(report.diagnostics);
}

export function createFeedbackReport(
  draft,
  {
    now = () => new Date().toISOString(),
    createId = createFeedbackReportId,
  } = {},
) {
  const validation = validateFeedbackDraft(draft);
  if (!validation.valid) throw new Error(validation.message);
  const timestamp = now();
  const report = {
    schemaVersion: 1,
    id: createId(),
    category: validation.category,
    message: validation.message,
    diagnostics: draft.diagnostics || null,
    screenshot: draft.screenshot || null,
    createdAt: timestamp,
    updatedAt: timestamp,
    attempts: 0,
    lastAttemptAt: null,
    lastFailure: null,
  };
  if (!isFeedbackReport(report)) {
    throw new Error("The feedback report contains unsupported data.");
  }
  return report;
}
