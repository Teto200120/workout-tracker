import { completeOnboarding } from "../application/display-name.js";
import {
  firstValidationMessage,
  validateDisplayName,
} from "../domain/input-guardrails.js";

let completionHandler = null;
let isBound = false;
let submissionPending = false;
let currentStep = 1;

function elements() {
  return {
    app: document.querySelector("#appShell"),
    root: document.querySelector("#onboarding"),
    form: document.querySelector("#onboardingForm"),
    input: document.querySelector("#onboardingDisplayName"),
    error: document.querySelector("#onboardingError"),
    saveError: document.querySelector("#onboardingSaveError"),
    stepOne: document.querySelector("#onboardingStepOne"),
    stepTwo: document.querySelector("#onboardingStepTwo"),
    continueButton: document.querySelector("#onboardingContinue"),
    backButton: document.querySelector("#onboardingBack"),
    rpeAware: document.querySelector("#onboardingRpeAware"),
    startupError: document.querySelector("#onboardingStartupError"),
    submit: document.querySelector("#onboardingSubmit"),
    retry: document.querySelector("#onboardingRetry"),
  };
}

function showStep(step, { focus = true } = {}) {
  const { root, stepOne, stepTwo, input, rpeAware } = elements();
  currentStep = step === 2 ? 2 : 1;
  stepOne?.classList.toggle("hidden", currentStep !== 1);
  stepOne?.setAttribute("aria-hidden", String(currentStep !== 1));
  stepTwo?.classList.toggle("hidden", currentStep !== 2);
  stepTwo?.setAttribute("aria-hidden", String(currentStep !== 2));
  root?.setAttribute(
    "aria-labelledby",
    currentStep === 1 ? "onboardingTitle" : "onboardingRpeTitle",
  );
  if (!focus) return;
  requestAnimationFrame(() => {
    if (currentStep === 1) input?.focus();
    else rpeAware?.focus();
  });
}

function showError(message, { focus = true, save = false } = {}) {
  const { input, error, saveError } = elements();
  if (save) {
    if (saveError) saveError.textContent = message;
  } else if (error) {
    error.textContent = message;
  }
  if (input) {
    input.setAttribute("aria-invalid", "true");
    if (focus && !save) input.focus();
  }
}

function clearError() {
  const { input, error, saveError } = elements();
  input?.removeAttribute("aria-invalid");
  if (error) error.textContent = "";
  if (saveError) saveError.textContent = "";
}

export function showApplicationShell() {
  const { app, root } = elements();
  if (root) root.hidden = true;
  if (app) app.hidden = false;
  document.body.classList.remove("onboarding-active");
}

export function showOnboarding({ resetInput = false } = {}) {
  const { app, root, form, input, rpeAware, retry, startupError } = elements();
  if (app) app.hidden = true;
  if (root) root.hidden = false;
  if (form) form.hidden = false;
  if (startupError) startupError.textContent = "";
  if (retry) retry.classList.add("hidden");
  document.body.classList.add("onboarding-active");
  if (resetInput && input) input.value = "";
  if (resetInput && rpeAware) rpeAware.checked = true;
  clearError();
  showStep(1);
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
  if (currentStep === 1) {
    advanceOnboarding();
    return;
  }
  if (submissionPending) return;
  const { input, rpeAware, submit } = elements();
  const validation = validateDisplayName(input?.value);
  if (!validation.valid) {
    showError(firstValidationMessage(validation), { focus: false, save: true });
    return;
  }

  submissionPending = true;
  if (submit) {
    submit.disabled = true;
    submit.textContent = "Saving...";
  }
  clearError();
  try {
    const operation = completeOnboarding(input.value, rpeAware?.checked ?? true);
    const result = await operation.promise;
    if (!result.saved) {
      showError(result.message || "Enter a valid display name.", {
        focus: false,
        save: true,
      });
      return;
    }
    await completionHandler?.(result.displayName);
  } catch (error) {
    console.info("Display name save failed.", error);
    showError(
      "Could not finish setup. Your name and RPE choice are still here. Check browser storage and try again.",
      { focus: false, save: true },
    );
  } finally {
    submissionPending = false;
    if (submit) {
      submit.disabled = false;
      submit.textContent = "Finish setup";
    }
  }
}

function advanceOnboarding() {
  const { input } = elements();
  const validation = validateDisplayName(input?.value);
  if (!validation.valid) {
    showError(firstValidationMessage(validation));
    return false;
  }
  clearError();
  showStep(2);
  return true;
}

export function bindOnboarding({ onComplete } = {}) {
  completionHandler = onComplete || completionHandler;
  if (isBound) return;
  const { form, input, continueButton, backButton } = elements();
  if (!form || !input) return;
  isBound = true;
  form.addEventListener("submit", submitOnboarding);
  continueButton?.addEventListener("click", advanceOnboarding);
  backButton?.addEventListener("click", () => showStep(1));
  input.addEventListener("input", () => {
    if (validateDisplayName(input.value).valid) clearError();
  });
}
