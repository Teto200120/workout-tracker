import {
  BACKUP_META_KEY,
  DEFAULT_APP_SETTINGS,
  DRAFT_KEY,
  GOALS_KEY,
  SETTINGS_KEY
} from "../core/constants.js";

export function cloneDefaultSettings() {
  return JSON.parse(JSON.stringify(DEFAULT_APP_SETTINGS));
}

export function getAppSettings() {
  try {
    const stored = JSON.parse(localStorage.getItem(SETTINGS_KEY)) || {};
    return {
      ...cloneDefaultSettings(),
      ...stored,
      schedule: { ...cloneDefaultSettings().schedule, ...(stored.schedule || {}) }
    };
  } catch {
    return cloneDefaultSettings();
  }
}

export function setAppSettings(settings) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
}

export function removeAppSettings() {
  localStorage.removeItem(SETTINGS_KEY);
}

export function getGoals() {
  try {
    return JSON.parse(localStorage.getItem(GOALS_KEY)) || { weeklyGoal: 4 };
  } catch {
    return { weeklyGoal: 4 };
  }
}

export function setGoals(goals) {
  localStorage.setItem(GOALS_KEY, JSON.stringify(goals));
}

export function getDraft() {
  try {
    return JSON.parse(localStorage.getItem(DRAFT_KEY));
  } catch {
    return null;
  }
}

export function setDraft(draft) {
  localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
}

export function removeDraft() {
  localStorage.removeItem(DRAFT_KEY);
}

export function getBackupMeta() {
  try {
    return JSON.parse(localStorage.getItem(BACKUP_META_KEY)) || {};
  } catch {
    return {};
  }
}

export function setBackupMeta(meta) {
  localStorage.setItem(BACKUP_META_KEY, JSON.stringify(meta || {}));
}

export function clearApplicationLocalStorage() {
  localStorage.removeItem(BACKUP_META_KEY);
  localStorage.removeItem(GOALS_KEY);
  localStorage.removeItem(DRAFT_KEY);
  localStorage.removeItem(SETTINGS_KEY);
}
