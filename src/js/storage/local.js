import { runDataOperation } from "./operation.js";
import {
  APPLICATION_SCHEMA_VERSION_KEY,
  BACKUP_META_KEY,
  DEFAULT_APP_SETTINGS,
  DRAFT_KEY,
  EDUCATION_KEY,
  GOALS_KEY,
  SETTINGS_KEY
} from "../core/constants.js";
import {
  validateSettingsInput,
  validateWeeklyGoal
} from "../domain/input-guardrails.js";
import { DataSchemaError, createValidationError } from "../schema/errors.js";
import {
  assertValidBackupMeta,
  assertValidDraft,
  assertValidGoals,
  assertValidSettings
} from "../schema/validators.js";

const LOCAL_STORAGE_FIELDS = Object.freeze({
  settings: SETTINGS_KEY,
  goals: GOALS_KEY,
  draft: DRAFT_KEY,
  backupMeta: BACKUP_META_KEY,
  applicationSchemaVersion: APPLICATION_SCHEMA_VERSION_KEY
});

function parseStoredJson(rawValue, field) {
  if (rawValue === null) return null;
  try {
    return JSON.parse(rawValue);
  } catch (cause) {
    throw new DataSchemaError(`Invalid JSON in ${LOCAL_STORAGE_FIELDS[field]}.`, {
      cause,
      code: "invalid_json",
      path: field,
      source: "application"
    });
  }
}

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

function rawSetAppSettings(settings) {
  assertValidSettings(settings, { path: "settings", source: "application" });
  const guardrails = validateSettingsInput(settings);
  if (!guardrails.valid) {
    throw createValidationError(guardrails.errors, {
      code: "input_guardrail_failed",
      source: "application",
    });
  }
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
}

function rawRemoveAppSettings() {
  localStorage.removeItem(SETTINGS_KEY);
}

export function getGoals() {
  try {
    return JSON.parse(localStorage.getItem(GOALS_KEY)) || { weeklyGoal: 4 };
  } catch {
    return { weeklyGoal: 4 };
  }
}

function rawSetGoals(goals) {
  assertValidGoals(goals, { path: "goals", source: "application" });
  const guardrails = validateWeeklyGoal(goals.weeklyGoal);
  if (!guardrails.valid) {
    throw createValidationError(
      guardrails.errors.map((error) => ({
        ...error,
        path: "goals.weeklyGoal",
      })),
      { code: "input_guardrail_failed", source: "application" },
    );
  }
  localStorage.setItem(GOALS_KEY, JSON.stringify(goals));
}

export function getDraft() {
  try {
    return JSON.parse(localStorage.getItem(DRAFT_KEY));
  } catch {
    return null;
  }
}

function rawSetDraft(draft) {
  assertValidDraft(draft, { path: "draft", source: "application" });
  localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
}

function rawRemoveDraft() {
  localStorage.removeItem(DRAFT_KEY);
}

export function getBackupMeta() {
  try {
    return JSON.parse(localStorage.getItem(BACKUP_META_KEY)) || {};
  } catch {
    return {};
  }
}

function rawSetBackupMeta(meta) {
  const value = meta || {};
  assertValidBackupMeta(value, { path: "backupMeta", source: "application" });
  localStorage.setItem(BACKUP_META_KEY, JSON.stringify(value));
}

export function getEducationRecord() {
  const rawValue = localStorage.getItem(EDUCATION_KEY);
  if (rawValue === null) return null;
  try {
    return JSON.parse(rawValue);
  } catch {
    return null;
  }
}

function rawSetEducationRecord(record) {
  localStorage.setItem(EDUCATION_KEY, JSON.stringify(record));
}

export function getRawEducationRecord() {
  return localStorage.getItem(EDUCATION_KEY);
}

function rawRestoreRawEducationRecord(rawValue) {
  if (rawValue === null || rawValue === undefined) {
    localStorage.removeItem(EDUCATION_KEY);
  } else {
    localStorage.setItem(EDUCATION_KEY, rawValue);
  }
}

function rawRemoveEducationRecord() {
  localStorage.removeItem(EDUCATION_KEY);
}

export function captureApplicationLocalStorage() {
  return Object.fromEntries(Object.entries(LOCAL_STORAGE_FIELDS).map(([field, key]) => [field, localStorage.getItem(key)]));
}

function rawRestoreApplicationLocalStorage(snapshot) {
  Object.entries(LOCAL_STORAGE_FIELDS).forEach(([field, key]) => {
    const rawValue = snapshot[field];
    if (rawValue === null || rawValue === undefined) localStorage.removeItem(key);
    else localStorage.setItem(key, rawValue);
  });
}

export function readPersistedApplicationLocalData(snapshot = captureApplicationLocalStorage()) {
  return {
    settings: parseStoredJson(snapshot.settings, "settings"),
    goals: parseStoredJson(snapshot.goals, "goals"),
    draft: parseStoredJson(snapshot.draft, "draft"),
    backupMeta: parseStoredJson(snapshot.backupMeta, "backupMeta")
  };
}

function rawWritePersistedApplicationLocalData(data) {
  if (data.settings === null) rawRemoveAppSettings();
  else rawSetAppSettings(data.settings);
  if (data.goals === null) localStorage.removeItem(GOALS_KEY);
  else rawSetGoals(data.goals);
  if (data.draft === null) rawRemoveDraft();
  else rawSetDraft(data.draft);
  if (data.backupMeta === null) localStorage.removeItem(BACKUP_META_KEY);
  else rawSetBackupMeta(data.backupMeta);
}

export function getApplicationSchemaVersionMarker() {
  return localStorage.getItem(APPLICATION_SCHEMA_VERSION_KEY);
}

function rawSetApplicationSchemaVersionMarker(version) {
  localStorage.setItem(APPLICATION_SCHEMA_VERSION_KEY, String(version));
}

function rawRemoveApplicationSchemaVersionMarker() {
  localStorage.removeItem(APPLICATION_SCHEMA_VERSION_KEY);
}

function rawClearApplicationLocalStorage() {
  localStorage.removeItem(BACKUP_META_KEY);
  localStorage.removeItem(GOALS_KEY);
  localStorage.removeItem(DRAFT_KEY);
  localStorage.removeItem(SETTINGS_KEY);
  localStorage.removeItem(EDUCATION_KEY);
  localStorage.removeItem(APPLICATION_SCHEMA_VERSION_KEY);
}

export function setAppSettings(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawSetAppSettings(snapshot), options.operation);
}

export function removeAppSettings(options = {}) {
  return runDataOperation(() => rawRemoveAppSettings(), options.operation);
}

export function setGoals(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawSetGoals(snapshot), options.operation);
}

export function setDraft(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawSetDraft(snapshot), options.operation);
}

export function removeDraft(options = {}) {
  return runDataOperation(() => rawRemoveDraft(), options.operation);
}

export function setBackupMeta(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawSetBackupMeta(snapshot), options.operation);
}

export function setEducationRecord(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawSetEducationRecord(snapshot), options.operation);
}

export function restoreRawEducationRecord(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawRestoreRawEducationRecord(snapshot), options.operation);
}

export function removeEducationRecord(options = {}) {
  return runDataOperation(() => rawRemoveEducationRecord(), options.operation);
}

export function restoreApplicationLocalStorage(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawRestoreApplicationLocalStorage(snapshot), options.operation);
}

export function writePersistedApplicationLocalData(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawWritePersistedApplicationLocalData(snapshot), options.operation);
}

export function setApplicationSchemaVersionMarker(value, options = {}) {
  const snapshot = structuredClone(value);
  return runDataOperation(() => rawSetApplicationSchemaVersionMarker(snapshot), options.operation);
}

export function removeApplicationSchemaVersionMarker(options = {}) {
  return runDataOperation(() => rawRemoveApplicationSchemaVersionMarker(), options.operation);
}

export function clearApplicationLocalStorage(options = {}) {
  return runDataOperation(() => rawClearApplicationLocalStorage(), options.operation);
}
