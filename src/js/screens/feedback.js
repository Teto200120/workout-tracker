import "../core/globals.js";
import {
  createFeedbackService,
} from "../application/feedback.js";
import {
  createLiveFeedbackTransport,
  FEEDBACK_TURNSTILE_SITE_KEY,
} from "../application/feedback-transport.js";
import {
  collectFeedbackDiagnostics,
  FEEDBACK_MESSAGE_MAX_LENGTH,
  FEEDBACK_SCREENSHOT_MAX_DECODED_PIXELS,
  FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES,
  readFeedbackScreenshotDimensions,
  validateFeedbackDraft,
  validateFeedbackScreenshotDimensions,
  validateFeedbackScreenshotFile,
} from "../domain/feedback.js";
import { createFeedbackOutbox } from "../storage/feedback-outbox.js";

const MAX_SCREENSHOT_EDGE = 1280;
const outbox = createFeedbackOutbox();
let turnstileLoad = null;
let transport = createLiveFeedbackTransport({ getTurnstileToken });
let service = createFeedbackService({ outbox, transport });
let screenshot = null;
let actionsBound = false;
let screenshotPending = false;

const CATEGORY_LABELS = Object.freeze({
  bug: "Something is broken",
  idea: "Idea or improvement",
  other: "Other feedback",
});

function setText(id, value) {
  const element = $(id);
  if (element) element.textContent = value;
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toFixed(bytes >= 1024 * 100 ? 0 : 1)} KB`;
}

function formatReportDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Saved on this device";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function failureText(code) {
  if (code === "temporary_failure") return "The last send attempt failed. Your report is still saved.";
  if (code === "invalid_response") return "The receiver did not confirm delivery. Your report is still saved.";
  return "Saved on this device and ready to retry.";
}

function loadTurnstile() {
  if (globalThis.turnstile) return Promise.resolve(globalThis.turnstile);
  if (turnstileLoad) return turnstileLoad;
  turnstileLoad = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src =
      "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
    script.async = true;
    script.defer = true;
    script.onload = () =>
      globalThis.turnstile
        ? resolve(globalThis.turnstile)
        : reject(new Error("Verification could not start."));
    script.onerror = () => reject(new Error("Verification could not start."));
    document.head.append(script);
  });
  turnstileLoad.catch(() => {
    turnstileLoad = null;
  });
  return turnstileLoad;
}

async function getTurnstileToken() {
  if (globalThis.navigator.onLine === false) {
    throw new Error("Connect to the internet to verify and send this report.");
  }
  setText(
    "feedbackFormStatus",
    "Complete the verification to send your saved report.",
  );
  const turnstile = await loadTurnstile();
  const container = $("feedbackTurnstile");
  if (!container) throw new Error("Verification could not start.");
  container.replaceChildren();
  return new Promise((resolve, reject) => {
    turnstile.render(container, {
      sitekey: FEEDBACK_TURNSTILE_SITE_KEY,
      callback: resolve,
      "error-callback": () =>
        reject(new Error("Verification did not complete.")),
      "expired-callback": () =>
        reject(new Error("Verification expired. Try sending again.")),
    });
  });
}

function setScreenshotPending(pending) {
  screenshotPending = pending;
  const input = $("feedbackScreenshot");
  const submit = $("feedbackSubmit");
  if (input) input.disabled = pending;
  if (submit) submit.disabled = pending;
  $("feedbackForm")?.setAttribute("aria-busy", String(pending));
}

function clearScreenshot({ clearStatus = true } = {}) {
  screenshot = null;
  const input = $("feedbackScreenshot");
  if (input) input.value = "";
  const image = $("feedbackScreenshotImage");
  if (image) image.removeAttribute("src");
  $("feedbackScreenshotPreview")?.classList.add("hidden");
  if (clearStatus) setText("feedbackScreenshotStatus", "");
}

function canvasToBlob(canvas, quality) {
  return new Promise((resolve) => {
    canvas.toBlob(resolve, "image/jpeg", quality);
  });
}

function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("The compressed screenshot could not be read."));
    reader.readAsDataURL(blob);
  });
}

async function compressScreenshot(file) {
  const validation = validateFeedbackScreenshotFile(file);
  if (!validation.valid) throw new Error(validation.message);

  const headerDimensions = await readFeedbackScreenshotDimensions(file);
  const headerValidation = validateFeedbackScreenshotDimensions(headerDimensions);
  if (!headerValidation.valid) throw new Error(headerValidation.message);

  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error("That screenshot could not be decoded safely.");
  }

  try {
    if (
      bitmap.width <= 0 ||
      bitmap.height <= 0 ||
      bitmap.height >
        Math.floor(FEEDBACK_SCREENSHOT_MAX_DECODED_PIXELS / bitmap.width)
    ) {
      throw new Error("That screenshot has unsupported dimensions.");
    }

    const initialScale = Math.min(
      1,
      MAX_SCREENSHOT_EDGE / Math.max(bitmap.width, bitmap.height),
    );
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * initialScale));
    canvas.height = Math.max(1, Math.round(bitmap.height * initialScale));
    let context = canvas.getContext("2d", { alpha: false });
    if (!context) throw new Error("Screenshot compression is not supported here.");
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);

    let quality = 0.82;
    let blob = await canvasToBlob(canvas, quality);
    while (blob && blob.size > FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES && quality > 0.46) {
      quality -= 0.09;
      blob = await canvasToBlob(canvas, quality);
    }

    while (
      blob &&
      blob.size > FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES &&
      canvas.width > 360 &&
      canvas.height > 360
    ) {
      const smaller = document.createElement("canvas");
      smaller.width = Math.max(1, Math.round(canvas.width * 0.8));
      smaller.height = Math.max(1, Math.round(canvas.height * 0.8));
      context = smaller.getContext("2d", { alpha: false });
      if (!context) break;
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, smaller.width, smaller.height);
      context.drawImage(canvas, 0, 0, smaller.width, smaller.height);
      canvas.width = smaller.width;
      canvas.height = smaller.height;
      canvas.getContext("2d", { alpha: false }).drawImage(smaller, 0, 0);
      blob = await canvasToBlob(canvas, 0.64);
    }

    if (!blob || blob.size > FEEDBACK_SCREENSHOT_MAX_OUTPUT_BYTES) {
      throw new Error("That screenshot could not be compressed enough for safe local storage.");
    }

    return {
      dataUrl: await blobToDataUrl(blob),
      mimeType: "image/jpeg",
      width: canvas.width,
      height: canvas.height,
      size: blob.size,
    };
  } finally {
    bitmap.close();
  }
}

async function handleScreenshotChange(event) {
  const file = event.target.files[0];
  clearScreenshot({ clearStatus: false });
  if (!file) {
    setText("feedbackScreenshotStatus", "");
    return;
  }
  setScreenshotPending(true);
  setText("feedbackScreenshotStatus", "Validating and compressing screenshot on this device...");
  try {
    screenshot = await compressScreenshot(file);
    const image = $("feedbackScreenshotImage");
    if (image) image.src = screenshot.dataUrl;
    $("feedbackScreenshotPreview")?.classList.remove("hidden");
    setText(
      "feedbackScreenshotStatus",
      `Ready: compressed from ${formatBytes(file.size)} to ${formatBytes(screenshot.size)} (${screenshot.width}x${screenshot.height}).`,
    );
  } catch (error) {
    clearScreenshot({ clearStatus: false });
    setText("feedbackScreenshotStatus", error.message);
  } finally {
    setScreenshotPending(false);
  }
}

function feedbackDiagnostics() {
  if (!$("feedbackIncludeDiagnostics")?.checked) return null;
  return collectFeedbackDiagnostics({
    viewportWidth: globalThis.innerWidth,
    viewportHeight: globalThis.innerHeight,
    standalone: globalThis.matchMedia?.("(display-mode: standalone)").matches,
    online: globalThis.navigator.onLine,
    currentScreen: "feedback",
  });
}

function clearFormAfterQueue() {
  $("feedbackForm")?.reset();
  $("feedbackIncludeDiagnostics").checked = true;
  clearScreenshot();
  setText("feedbackCharacterCount", `0/${FEEDBACK_MESSAGE_MAX_LENGTH}`);
  setText("feedbackMessageError", "");
  setText("feedbackCategoryError", "");
}

function pendingReportElement(report) {
  const article = document.createElement("article");
  article.className = "feedback-pending-report";
  article.dataset.reportId = report.id;

  const heading = document.createElement("div");
  heading.className = "feedback-pending-heading";
  const title = document.createElement("strong");
  title.textContent = CATEGORY_LABELS[report.category];
  const date = document.createElement("span");
  date.textContent = formatReportDate(report.createdAt);
  heading.append(title, date);

  const message = document.createElement("p");
  message.textContent = report.message;

  const metadata = document.createElement("div");
  metadata.className = "feedback-pending-meta";
  const attempts = document.createElement("span");
  attempts.textContent = `${report.attempts} send ${report.attempts === 1 ? "attempt" : "attempts"}`;
  const attachment = document.createElement("span");
  attachment.textContent = report.screenshot ? "Screenshot attached" : "No screenshot";
  const diagnostics = document.createElement("span");
  diagnostics.textContent = report.diagnostics ? "Safe diagnostics included" : "No diagnostics";
  metadata.append(attempts, attachment, diagnostics);

  const state = document.createElement("p");
  state.className = "feedback-pending-state";
  state.textContent = failureText(report.lastFailure);

  const actions = document.createElement("div");
  actions.className = "feedback-pending-actions";
  const retry = document.createElement("button");
  retry.className = "primary";
  retry.type = "button";
  retry.dataset.feedbackAction = "retry";
  retry.textContent = "Try sending";
  const remove = document.createElement("button");
  remove.className = "ghost";
  remove.type = "button";
  remove.dataset.feedbackAction = "delete";
  remove.textContent = "Delete";
  actions.append(retry, remove);

  article.append(heading, message, metadata, state, actions);
  return article;
}

export function renderFeedback() {
  const list = $("feedbackOutboxList");
  if (!list) return;
  let reports;
  try {
    reports = service.list();
  } catch (error) {
    list.replaceChildren();
    const warning = document.createElement("p");
    warning.className = "feedback-outbox-warning";
    warning.setAttribute("role", "alert");
    warning.textContent = error.message;
    list.append(warning);
    setText("feedbackPendingCount", "Needs attention");
    setText("profileFeedbackSummary", "Pending feedback needs attention");
    return;
  }

  const countText = `${reports.length} pending`;
  setText("feedbackPendingCount", countText);
  setText(
    "profileFeedbackSummary",
    reports.length
      ? `${countText} on this device`
      : "Send feedback or manage pending reports",
  );
  list.replaceChildren();
  if (!reports.length) {
    const empty = document.createElement("p");
    empty.className = "feedback-outbox-empty";
    empty.textContent = "No pending reports on this device.";
    list.append(empty);
    return;
  }
  list.append(...reports.map(pendingReportElement));
}

async function handleSubmit(event) {
  event.preventDefault();
  if (screenshotPending) return;
  const draft = {
    category: $("feedbackCategory").value,
    message: $("feedbackMessage").value,
    diagnostics: feedbackDiagnostics(),
    screenshot,
  };
  const validation = validateFeedbackDraft(draft);
  setText("feedbackMessageError", validation.valid ? "" : validation.message);
  if (!validation.valid) {
    $("feedbackMessage").focus();
    return;
  }

  const submit = $("feedbackSubmit");
  submit.disabled = true;
  $("feedbackForm").setAttribute("aria-busy", "true");
  setText("feedbackFormStatus", "Saving the report on this device...");
  try {
    const result = await service.submit(draft);
    clearFormAfterQueue();
    if (result.sent) {
      setText("feedbackFormStatus", "Report sent. Its local pending copy was removed after confirmation.");
    } else if (result.code === "local_cleanup_failed") {
      setText("feedbackFormStatus", "Delivery was confirmed, but the local copy could not be cleared. It remains visible for safety.");
    } else {
      setText("feedbackFormStatus", `${failureText(result.code)} You can retry it below.`);
    }
    renderFeedback();
  } catch (error) {
    setText("feedbackFormStatus", error.message);
  } finally {
    submit.disabled = false;
    $("feedbackForm").setAttribute("aria-busy", "false");
  }
}

async function handleOutboxAction(event) {
  const button = event.target.closest("[data-feedback-action]");
  if (!button) return;
  const report = button.closest("[data-report-id]");
  const id = report?.dataset.reportId;
  if (!id) return;

  if (button.dataset.feedbackAction === "delete") {
    if (!confirm("Delete this pending feedback report from this device? This cannot be undone.")) return;
    try {
      service.remove(id);
      setText("feedbackFormStatus", "Pending report deleted from this device.");
      renderFeedback();
    } catch (error) {
      setText("feedbackFormStatus", error.message);
    }
    return;
  }

  button.disabled = true;
  button.textContent = "Trying...";
  try {
    const result = await service.retry(id);
    setText(
      "feedbackFormStatus",
      result.sent
        ? "Report sent. Its local pending copy was removed after confirmation."
        : `${failureText(result.code)} You can retry it again.`,
    );
    renderFeedback();
  } catch (error) {
    setText("feedbackFormStatus", error.message);
    renderFeedback();
  }
}

export function bindFeedbackActions() {
  if (actionsBound) return;
  actionsBound = true;
  $("feedbackForm")?.addEventListener("submit", handleSubmit);
  $("feedbackMessage")?.addEventListener("input", (event) => {
    setText(
      "feedbackCharacterCount",
      `${event.target.value.length}/${FEEDBACK_MESSAGE_MAX_LENGTH}`,
    );
    if (event.target.value.trim().length >= 10) setText("feedbackMessageError", "");
  });
  $("feedbackScreenshot")?.addEventListener("change", handleScreenshotChange);
  $("feedbackRemoveScreenshot")?.addEventListener("click", () => clearScreenshot());
  $("feedbackOutboxList")?.addEventListener("click", handleOutboxAction);
}

export function setFeedbackTransport(nextTransport) {
  if (!nextTransport || typeof nextTransport.send !== "function") {
    throw new TypeError("A feedback transport with send() is required.");
  }
  transport = nextTransport;
  service = createFeedbackService({ outbox, transport });
}
