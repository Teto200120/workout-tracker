import { saveDisplayName } from "../application/display-name.js";
import {
  firstValidationMessage,
  validateDisplayName,
} from "../domain/input-guardrails.js";

let completionHandler = null;
let isBound = false;
let submissionPending = false;

function elements() {
  return {
    app: document.querySelector("#appShell"),
    root: document.querySelector("#onboarding"),
    form: document.querySelector("#onboardingForm"),
    input: document.querySelector("#onboardingDisplayName"),
    error: document.querySelector("#onboardingError"),
    startupError: document.querySelector("#onboardingStartupError"),
    submit: document.querySelector("#onboardingSubmit"),
    retry: document.querySelector("#onboardingRetry"),
  };
}

function showError(message, { focus = true } = {}) {
  const { input, error } = elements();
  if (error) error.textContent = message;
  if (input) {
    input.setAttribute("aria-invalid", "true");
    if (focus) input.focus();
  }
}

function clearError() {
  const { input, error } = elements();
  input?.removeAttribute("aria-invalid");
  if (error) error.textContent = "";
}

export function showApplicationShell() {
  const { app, root } = elements();
  if (root) root.hidden = true;
  if (app) app.hidden = false;
  document.body.classList.remove("onboarding-active");
}

export function showOnboarding({ resetInput = false } = {}) {
  const { app, root, form, input, retry, startupError } = elements();
  if (app) app.hidden = true;
  if (root) root.hidden = false;
  if (form) form.hidden = false;
  if (startupError) startupError.textContent = "";
  if (retry) retry.classList.add("hidden");
  document.body.classList.add("onboarding-active");
  if (resetInput && input) input.value = "";
  clearError();
  requestAnimationFrame(() => input?.focus());
}

export function showStartupFailure(message) {
  const { app, root, form, startupError, retry } = elements();
  if (app) app.hidden = true;
  if (root) root.hidden = false;
  if (form) form.hidden = true;
  if (startupError) startupError.textContent = message;
  if (retry) {
    retry.classList.remove("hidden");
    retry.onclick = () => window.location.reload();
    requestAnimationFrame(() => retry.focus());
  }
  document.body.classList.add("onboarding-active");
}

async function submitOnboarding(event) {
  event.preventDefault();
  if (submissionPending) return;
  const { input, submit } = elements();
  const validation = validateDisplayName(input?.value);
  if (!validation.valid) {
    showError(firstValidationMessage(validation));
    return;
  }

  submissionPending = true;
  if (submit) {
    submit.disabled = true;
    submit.textContent = "Saving...";
  }
  clearError();
  try {
    const operation = saveDisplayName(input.value);
    const result = await operation.promise;
    if (!result.saved) {
      showError(result.message || "Enter a valid display name.");
      return;
    }
    await completionHandler?.(result.displayName);
  } catch (error) {
    console.info("Display name save failed.", error);
    showError("Could not save your name. Check browser storage and try again.");
  } finally {
    submissionPending = false;
    if (submit) {
      submit.disabled = false;
      submit.textContent = "Get started";
    }
  }
}

export function bindOnboarding({ onComplete } = {}) {
  completionHandler = onComplete || completionHandler;
  if (isBound) return;
  const { form, input } = elements();
  if (!form || !input) return;
  isBound = true;
  form.addEventListener("submit", submitOnboarding);
  input.addEventListener("input", () => {
    if (validateDisplayName(input.value).valid) clearError();
  });
}
