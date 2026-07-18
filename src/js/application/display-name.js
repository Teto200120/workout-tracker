import { createActionCoordinator } from "./action-coordinator.js";
import {
  firstValidationMessage,
  validateDisplayName,
} from "../domain/input-guardrails.js";
import { getAppSettings, setAppSettings } from "../storage/local.js";

const displayNameCoordinator = createActionCoordinator();

export function getDisplayName(settings = getAppSettings()) {
  const validation = validateDisplayName(settings?.displayName);
  return validation.valid ? validation.normalized : null;
}

export function isOnboardingRequired(settings = getAppSettings()) {
  return getDisplayName(settings) === null;
}

export function saveDisplayName(value) {
  return displayNameCoordinator.run(() => {
    const validation = validateDisplayName(value);
    if (!validation.valid) {
      return {
        saved: false,
        displayName: null,
        validation,
        message: firstValidationMessage(validation),
      };
    }
    const settings = getAppSettings();
    setAppSettings({ ...settings, displayName: validation.normalized });
    return {
      saved: true,
      displayName: validation.normalized,
      validation,
      message: "",
    };
  });
}
