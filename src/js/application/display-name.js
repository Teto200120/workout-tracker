import { createActionCoordinator } from "./action-coordinator.js";
import { ensureEducationState, forgetEducationState } from "./education.js";
import {
  firstValidationMessage,
  validateDisplayName,
} from "../domain/input-guardrails.js";
import { DataSchemaError } from "../schema/errors.js";
import { CURRENT_APPLICATION_SCHEMA_VERSION } from "../schema/versions.js";
import {
  captureApplicationLocalStorage,
  getAppSettings,
  getRawEducationRecord,
  restoreApplicationLocalStorage,
  restoreRawEducationRecord,
  setApplicationSchemaVersionMarker,
  setAppSettings,
} from "../storage/local.js";

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

export function completeOnboarding(value, rpeAware = true) {
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

    const localSnapshot = captureApplicationLocalStorage();
    const educationSnapshot = getRawEducationRecord();
    try {
      const settings = getAppSettings();
      setAppSettings({
        ...settings,
        displayName: validation.normalized,
        rpeAware: Boolean(rpeAware),
      });
      const educationResult = ensureEducationState();
      if (!educationResult.saved) throw educationResult.error;
      setApplicationSchemaVersionMarker(CURRENT_APPLICATION_SCHEMA_VERSION);
    } catch (cause) {
      const rollbackErrors = [];
      try {
        restoreApplicationLocalStorage(localSnapshot);
      } catch (error) {
        rollbackErrors.push(error);
      }
      try {
        restoreRawEducationRecord(educationSnapshot);
      } catch (error) {
        rollbackErrors.push(error);
      }
      forgetEducationState();
      throw new DataSchemaError("Onboarding could not be persisted.", {
        cause,
        code: "onboarding_persistence_failed",
        source: "application",
        rollbackErrors,
      });
    }

    return {
      saved: true,
      displayName: validation.normalized,
      rpeAware: Boolean(rpeAware),
      validation,
      message: "",
    };
  });
}
