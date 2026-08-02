export const EDUCATION_SCHEMA_VERSION = 1;

export const EDUCATION_STATUSES = Object.freeze([
  "unseen",
  "offered",
  "in_progress",
  "completed",
  "skipped",
  "dismissed",
  "deferred",
]);

const STATUS_SET = new Set(EDUCATION_STATUSES);

export const EDUCATION_EXPERIENCES = Object.freeze({
  homeTour: Object.freeze({ contentVersion: 1, stepCount: 4 }),
  activeWorkoutBasics: Object.freeze({ contentVersion: 1, stepCount: 3 }),
  rpeBasics: Object.freeze({ contentVersion: 1, stepCount: 1 }),
  routineEditorBasics: Object.freeze({ contentVersion: 1, stepCount: 3 }),
  historyBasics: Object.freeze({ contentVersion: 1, stepCount: 1 }),
  statsBasics: Object.freeze({ contentVersion: 1, stepCount: 1 }),
  exerciseGuideBasics: Object.freeze({ contentVersion: 1, stepCount: 1 }),
});

function isRecord(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function isPositiveInteger(value) {
  return Number.isInteger(value) && value > 0;
}

function safeTimestamp(value) {
  return typeof value === "string" && value && !Number.isNaN(Date.parse(value))
    ? value
    : null;
}

function cloneUnknown(value, seen = new WeakMap()) {
  if (value === null || typeof value !== "object") return value;
  if (seen.has(value)) return null;
  const clone = Array.isArray(value) ? [] : {};
  seen.set(value, clone);
  Object.entries(value).forEach(([key, item]) => {
    if (["__proto__", "prototype", "constructor"].includes(key)) return;
    clone[key] = cloneUnknown(item, seen);
  });
  return clone;
}

function defaultExperience(definition) {
  return {
    contentVersion: definition.contentVersion,
    status: "unseen",
    lastStep: 0,
    updatedAt: null,
    completedAt: null,
  };
}

function normalizeLastStep(value, definition, contentVersion) {
  const step = Number.isInteger(value) ? Math.max(0, value) : 0;
  if (contentVersion > definition.contentVersion) return step;
  return Math.min(step, Math.max(0, definition.stepCount - 1));
}

function normalizeKnownExperience(value, definition) {
  if (!isRecord(value)) return defaultExperience(definition);
  const storedContentVersion = isPositiveInteger(value.contentVersion)
    ? value.contentVersion
    : definition.contentVersion;

  if (storedContentVersion < definition.contentVersion) {
    return defaultExperience(definition);
  }

  const status = STATUS_SET.has(value.status) ? value.status : "unseen";
  return {
    contentVersion: storedContentVersion,
    status,
    lastStep: normalizeLastStep(
      value.lastStep,
      definition,
      storedContentVersion,
    ),
    updatedAt: safeTimestamp(value.updatedAt),
    completedAt:
      status === "completed" ? safeTimestamp(value.completedAt) : null,
  };
}

export function createDefaultEducationState() {
  return {
    schemaVersion: EDUCATION_SCHEMA_VERSION,
    experiences: Object.fromEntries(
      Object.entries(EDUCATION_EXPERIENCES).map(([id, definition]) => [
        id,
        defaultExperience(definition),
      ]),
    ),
  };
}

export function normalizeEducationState(value) {
  const source = isRecord(value) ? value : {};
  const sourceExperiences = isRecord(source.experiences)
    ? source.experiences
    : {};
  const experiences = {};

  Object.entries(sourceExperiences).forEach(([id, experience]) => {
    if (Object.hasOwn(EDUCATION_EXPERIENCES, id)) return;
    experiences[id] = cloneUnknown(experience);
  });

  Object.entries(EDUCATION_EXPERIENCES).forEach(([id, definition]) => {
    experiences[id] = normalizeKnownExperience(
      sourceExperiences[id],
      definition,
    );
  });

  return {
    schemaVersion:
      isPositiveInteger(source.schemaVersion) &&
      source.schemaVersion > EDUCATION_SCHEMA_VERSION
        ? source.schemaVersion
        : EDUCATION_SCHEMA_VERSION,
    experiences,
  };
}

export function transitionEducationExperience(
  state,
  experienceId,
  status,
  options = {},
) {
  if (!Object.hasOwn(EDUCATION_EXPERIENCES, experienceId)) {
    throw new TypeError(`Unknown education experience: ${experienceId}`);
  }
  if (!STATUS_SET.has(status)) {
    throw new TypeError(`Unknown education status: ${status}`);
  }

  const normalized = normalizeEducationState(state);
  const definition = EDUCATION_EXPERIENCES[experienceId];
  const current = normalized.experiences[experienceId];
  const updatedAt = safeTimestamp(options.now);
  const lastStep = status === "unseen"
    ? 0
    : normalizeLastStep(
        options.lastStep ?? current.lastStep,
        definition,
        current.contentVersion,
      );

  normalized.experiences[experienceId] = {
    ...current,
    status,
    lastStep,
    updatedAt,
    completedAt: status === "completed" ? updatedAt : null,
  };
  return normalized;
}

export function replayEducationExperience(state, experienceId, now = null) {
  return transitionEducationExperience(state, experienceId, "unseen", { now });
}

export function resetAllEducation(state) {
  const normalized = normalizeEducationState(state);
  Object.keys(EDUCATION_EXPERIENCES).forEach((experienceId) => {
    const current = normalized.experiences[experienceId];
    normalized.experiences[experienceId] = {
      ...current,
      status: "unseen",
      lastStep: 0,
      updatedAt: null,
      completedAt: null,
    };
  });
  return normalized;
}
