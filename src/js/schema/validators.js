import {
  APPLICATION_DATA_COLLECTIONS,
  APPLICATION_LOCAL_DATA_FIELDS,
  SCHEDULE_DAYS,
  SCHEDULE_KINDS,
  SET_BOOLEAN_FIELDS,
  SET_SCALAR_FIELDS,
  SETTINGS_BOOLEAN_FIELDS,
  SETTINGS_NUMBER_FIELDS
} from "./contracts.js";
import { createValidationError } from "./errors.js";

const hasOwn = (value, key) => Object.prototype.hasOwnProperty.call(value, key);

export function isPlainObject(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function validationResult(errors) {
  return { valid: errors.length === 0, errors };
}

function addError(errors, path, code, message) {
  errors.push({ path, code, message });
}

function required(errors, value, key, path, legacy) {
  if (hasOwn(value, key)) return true;
  if (!legacy) addError(errors, `${path}.${key}`, "required_field", `${key} is required.`);
  return false;
}

function isFiniteNumberLike(value, allowEmpty = false) {
  if (allowEmpty && value === "") return true;
  if (typeof value === "number") return Number.isFinite(value);
  return typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value));
}

function isLegacyBoolean(value) {
  return value === null
    || typeof value === "boolean"
    || value === 0
    || value === 1
    || value === "0"
    || value === "1"
    || value === "true"
    || value === "false";
}

function validateIdentifier(value, path, errors, options = {}) {
  if (value === null && options.deferIdConstraints) return;
  if (typeof value === "string") {
    if (!value.length) addError(errors, path, "empty_id", "ID must not be empty.");
    return;
  }
  if (typeof value === "number" && Number.isFinite(value)) return;
  addError(errors, path, "invalid_id", "ID must be a non-empty string or finite number.");
}

function isValidDateOnly(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function isValidTime(value) {
  if (value === "") return true;
  if (typeof value !== "string" || !/^\d{2}:\d{2}$/.test(value)) return false;
  const [hour, minute] = value.split(":").map(Number);
  return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
}

function isValidTimestamp(value) {
  return value === "" || (typeof value === "string" && !Number.isNaN(new Date(value).getTime()));
}

function validateStringField(value, key, path, errors, options = {}) {
  if (!required(errors, value, key, path, options.legacy)) return;
  const field = value[key];
  if (options.legacy && field === null) return;
  if (typeof field !== "string") addError(errors, `${path}.${key}`, "expected_string", `${key} must be a string.`);
}

function validateTimestampField(value, key, path, errors, options = {}) {
  if (!required(errors, value, key, path, options.legacy)) return;
  const field = value[key];
  if (options.legacy && (field === null || field === undefined)) return;
  if (!isValidTimestamp(field)) addError(errors, `${path}.${key}`, "invalid_timestamp", `${key} must be an ISO timestamp or an empty string.`);
}

function validateStringArray(value, key, path, errors, options = {}) {
  if (!required(errors, value, key, path, options.legacy)) return;
  const array = value[key];
  if (!Array.isArray(array)) {
    addError(errors, `${path}.${key}`, "expected_array", `${key} must be an array.`);
    return;
  }
  array.forEach((item, index) => {
    if (typeof item !== "string") addError(errors, `${path}.${key}[${index}]`, "expected_string", `${key} entries must be strings.`);
  });
}

export function validateSet(value, options = {}) {
  const path = options.path || "set";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Set must be an object.");
    return validationResult(errors);
  }

  SET_SCALAR_FIELDS.forEach((field) => {
    if (!required(errors, value, field, path, legacy)) return;
    const scalar = value[field];
    if (legacy && (scalar === null || scalar === undefined)) return;
    if (legacy) {
      if (!isFiniteNumberLike(scalar, true)) addError(errors, `${path}.${field}`, "invalid_numeric_value", `${field} must be numeric or empty.`);
      return;
    }
    if (typeof scalar !== "string") {
      addError(errors, `${path}.${field}`, "expected_string", `${field} must be a numeric string or empty.`);
      return;
    }
    if (!isFiniteNumberLike(scalar, true)) addError(errors, `${path}.${field}`, "invalid_numeric_value", `${field} must be numeric or empty.`);
  });

  if (hasOwn(value, "rpe") && value.rpe !== "" && value.rpe !== null && value.rpe !== undefined && isFiniteNumberLike(value.rpe)) {
    const rpe = Number(value.rpe);
    if (rpe < 0 || rpe > 10) addError(errors, `${path}.rpe`, "out_of_range", "RPE must be between 0 and 10.");
  }

  SET_BOOLEAN_FIELDS.forEach((field) => {
    if (!required(errors, value, field, path, legacy)) return;
    const flag = value[field];
    if (legacy ? !isLegacyBoolean(flag) : typeof flag !== "boolean") {
      addError(errors, `${path}.${field}`, "expected_boolean", `${field} must be a boolean.`);
    }
  });
  return validationResult(errors);
}

export function validateExercise(value, options = {}) {
  const path = options.path || "exercise";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Exercise must be an object.");
    return validationResult(errors);
  }
  validateStringField(value, "name", path, errors, { legacy });
  validateStringField(value, "notes", path, errors, { legacy });
  if (required(errors, value, "sets", path, legacy)) {
    if (!Array.isArray(value.sets)) {
      addError(errors, `${path}.sets`, "expected_array", "Sets must be an array.");
    } else {
      value.sets.forEach((set, index) => errors.push(...validateSet(set, { path: `${path}.sets[${index}]`, legacy }).errors));
    }
  }
  return validationResult(errors);
}

export function validateWorkout(value, options = {}) {
  const path = options.path || "workout";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Workout must be an object.");
    return validationResult(errors);
  }

  if (hasOwn(value, "id")) validateIdentifier(value.id, `${path}.id`, errors, options);
  else addError(errors, `${path}.id`, "required_field", "id is required.");

  if (!hasOwn(value, "date")) addError(errors, `${path}.date`, "required_field", "date is required.");
  else if (!isValidDateOnly(value.date)) addError(errors, `${path}.date`, "invalid_date", "date must use YYYY-MM-DD.");

  if (!hasOwn(value, "type")) addError(errors, `${path}.type`, "required_field", "type is required.");
  else if (typeof value.type !== "string") addError(errors, `${path}.type`, "expected_string", "type must be a string.");

  for (const field of ["startTime", "endTime"]) {
    if (!required(errors, value, field, path, legacy)) continue;
    const time = value[field];
    if (legacy && (time === null || time === undefined)) continue;
    if (!isValidTime(time)) addError(errors, `${path}.${field}`, "invalid_time", `${field} must use HH:MM or be empty.`);
  }
  validateStringField(value, "notes", path, errors, { legacy });
  validateTimestampField(value, "createdAt", path, errors, { legacy });
  validateStringArray(value, "tags", path, errors, { legacy });

  if (required(errors, value, "exercises", path, legacy)) {
    if (!Array.isArray(value.exercises)) {
      addError(errors, `${path}.exercises`, "expected_array", "Exercises must be an array.");
    } else {
      value.exercises.forEach((exercise, index) => errors.push(...validateExercise(exercise, { path: `${path}.exercises[${index}]`, legacy }).errors));
    }
  }

  if (hasOwn(value, "durationMinutes") && value.durationMinutes !== null && value.durationMinutes !== "") {
    if (legacy) {
      if (!isFiniteNumberLike(value.durationMinutes) || Number(value.durationMinutes) < 0) {
        addError(errors, `${path}.durationMinutes`, "invalid_number", "durationMinutes must be a non-negative number.");
      }
    } else if (typeof value.durationMinutes !== "number" || !Number.isFinite(value.durationMinutes) || value.durationMinutes < 0) {
      addError(errors, `${path}.durationMinutes`, "invalid_number", "durationMinutes must be a non-negative number.");
    }
  }
  return validationResult(errors);
}

export function validateRoutine(value, options = {}) {
  const path = options.path || "routine";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Routine must be an object.");
    return validationResult(errors);
  }
  if (hasOwn(value, "id")) validateIdentifier(value.id, `${path}.id`, errors, options);
  else addError(errors, `${path}.id`, "required_field", "id is required.");
  if (!hasOwn(value, "name")) addError(errors, `${path}.name`, "required_field", "name is required.");
  else if (typeof value.name !== "string") addError(errors, `${path}.name`, "expected_string", "name must be a string.");
  validateStringArray(value, "exercises", path, errors, { legacy });
  validateTimestampField(value, "createdAt", path, errors, { legacy });
  validateTimestampField(value, "updatedAt", path, errors, { legacy });
  return validationResult(errors);
}

function validateSchedule(schedule, path, errors, legacy) {
  if (!isPlainObject(schedule)) {
    addError(errors, path, "expected_object", "schedule must be an object.");
    return;
  }
  SCHEDULE_DAYS.forEach((day) => {
    if (!hasOwn(schedule, day)) {
      if (!legacy) addError(errors, `${path}.${day}`, "required_field", `Schedule day ${day} is required.`);
      return;
    }
    const entry = schedule[day];
    if (!isPlainObject(entry)) {
      addError(errors, `${path}.${day}`, "expected_object", "Schedule entry must be an object.");
      return;
    }
    if (!hasOwn(entry, "kind")) {
      if (!legacy) addError(errors, `${path}.${day}.kind`, "required_field", "kind is required.");
    } else if (!SCHEDULE_KINDS.includes(entry.kind)) {
      addError(errors, `${path}.${day}.kind`, "invalid_enum", "kind must be gym, rest, or soccer.");
    }
    if (!hasOwn(entry, "routine")) {
      if (!legacy) addError(errors, `${path}.${day}.routine`, "required_field", "routine is required.");
    } else if (typeof entry.routine !== "string") {
      addError(errors, `${path}.${day}.routine`, "expected_string", "routine must be a string.");
    }
  });
}

export function validateSettings(value, options = {}) {
  const path = options.path || "settings";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Settings must be an object.");
    return validationResult(errors);
  }
  if (required(errors, value, "schedule", path, legacy)) validateSchedule(value.schedule, `${path}.schedule`, errors, legacy);
  SETTINGS_NUMBER_FIELDS.forEach((field) => {
    if (!required(errors, value, field, path, legacy)) return;
    const setting = value[field];
    if (legacy && (setting === null || setting === undefined)) return;
    if (legacy ? !isFiniteNumberLike(setting) : typeof setting !== "number" || !Number.isFinite(setting)) {
      addError(errors, `${path}.${field}`, "invalid_number", `${field} must be a finite number.`);
      return;
    }
    if (field === "defaultWeightJump" && Number(setting) <= 0) addError(errors, `${path}.${field}`, "out_of_range", "defaultWeightJump must be greater than zero.");
    if (field !== "defaultWeightJump" && Number(setting) < 1) addError(errors, `${path}.${field}`, "out_of_range", `${field} must be at least one.`);
  });
  SETTINGS_BOOLEAN_FIELDS.forEach((field) => {
    if (!required(errors, value, field, path, legacy)) return;
    const setting = value[field];
    if (legacy ? !isLegacyBoolean(setting) : typeof setting !== "boolean") addError(errors, `${path}.${field}`, "expected_boolean", `${field} must be a boolean.`);
  });

  if (!legacy) {
    for (const [minimum, maximum] of [["compoundMin", "compoundMax"], ["pullMin", "pullMax"], ["isolationMin", "isolationMax"], ["generalMin", "generalMax"]]) {
      if (typeof value[minimum] === "number" && typeof value[maximum] === "number" && value[minimum] > value[maximum]) {
        addError(errors, `${path}.${maximum}`, "invalid_range", `${maximum} must be greater than or equal to ${minimum}.`);
      }
    }
  }
  return validationResult(errors);
}

export function validateGoals(value, options = {}) {
  const path = options.path || "goals";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Goals must be an object.");
    return validationResult(errors);
  }
  if (required(errors, value, "weeklyGoal", path, legacy)) {
    const goal = value.weeklyGoal;
    if (legacy && (goal === null || goal === undefined)) {
      // Missing legacy values receive the existing default.
    } else if (!isFiniteNumberLike(goal) || !Number.isInteger(Number(goal)) || Number(goal) < 1) {
      addError(errors, `${path}.weeklyGoal`, "invalid_integer", "weeklyGoal must be a positive integer.");
    } else if (!legacy && typeof goal !== "number") {
      addError(errors, `${path}.weeklyGoal`, "invalid_number", "weeklyGoal must be stored as a number.");
    }
  }
  if (hasOwn(value, "targetWeight")) {
    const target = value.targetWeight;
    if (target !== null && target !== "" && !isFiniteNumberLike(target)) addError(errors, `${path}.targetWeight`, "invalid_numeric_value", "targetWeight must be numeric, empty, or null.");
  }
  return validationResult(errors);
}

export function validateDraft(value, options = {}) {
  const path = options.path || "draft";
  const legacy = Boolean(options.legacy);
  const result = validateWorkout(value, { ...options, path, legacy });
  if (!isPlainObject(value)) return result;
  const errors = [...result.errors];

  if (required(errors, value, "editingWorkoutId", path, legacy)) {
    if (value.editingWorkoutId !== null) validateIdentifier(value.editingWorkoutId, `${path}.editingWorkoutId`, errors, options);
  }
  if (required(errors, value, "activeExerciseIndex", path, legacy)) {
    const index = value.activeExerciseIndex;
    if (legacy && (index === null || index === undefined)) {
      // Missing legacy indexes default to the first exercise.
    } else if (!isFiniteNumberLike(index) || !Number.isInteger(Number(index)) || Number(index) < 0) {
      addError(errors, `${path}.activeExerciseIndex`, "invalid_integer", "activeExerciseIndex must be a non-negative integer.");
    } else if (!legacy && typeof index !== "number") {
      addError(errors, `${path}.activeExerciseIndex`, "invalid_number", "activeExerciseIndex must be stored as a number.");
    }
  }
  validateTimestampField(value, "savedAt", path, errors, { legacy });
  return validationResult(errors);
}

export function validateBackupMeta(value, options = {}) {
  const path = options.path || "backupMeta";
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Backup metadata must be an object.");
    return validationResult(errors);
  }
  if (hasOwn(value, "lastExportedAt") && value.lastExportedAt !== null && !isValidTimestamp(value.lastExportedAt)) {
    addError(errors, `${path}.lastExportedAt`, "invalid_timestamp", "lastExportedAt must be an ISO timestamp, empty, or null.");
  }
  return validationResult(errors);
}

export function validateLegacyWeight(value, options = {}) {
  const path = options.path || "legacyWeight";
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, path, "expected_object", "Legacy weight must be an object.");
    return validationResult(errors);
  }
  if (hasOwn(value, "id")) validateIdentifier(value.id, `${path}.id`, errors, options);
  else addError(errors, `${path}.id`, "required_field", "id is required.");
  if (!hasOwn(value, "date")) addError(errors, `${path}.date`, "required_field", "date is required.");
  else if (!isValidDateOnly(value.date)) addError(errors, `${path}.date`, "invalid_date", "date must use YYYY-MM-DD.");
  if (!hasOwn(value, "weight")) addError(errors, `${path}.weight`, "required_field", "weight is required.");
  else if (!isFiniteNumberLike(value.weight) || Number(value.weight) < 0 || (!legacy && typeof value.weight !== "number")) {
    addError(errors, `${path}.weight`, "invalid_number", "weight must be a non-negative number.");
  }
  validateStringField(value, "notes", path, errors, { legacy });
  validateTimestampField(value, "createdAt", path, errors, { legacy });
  return validationResult(errors);
}

export function validateApplicationData(value, options = {}) {
  const legacy = Boolean(options.legacy);
  const errors = [];
  if (!isPlainObject(value)) {
    addError(errors, "", "expected_object", "Application data must be an object.");
    return validationResult(errors);
  }
  APPLICATION_DATA_COLLECTIONS.forEach((field) => {
    if (!hasOwn(value, field)) {
      addError(errors, field, "required_field", `${field} is required.`);
      return;
    }
    if (!Array.isArray(value[field])) addError(errors, field, "expected_array", `${field} must be an array.`);
  });
  if (Array.isArray(value.workouts)) value.workouts.forEach((workout, index) => errors.push(...validateWorkout(workout, { ...options, path: `workouts[${index}]`, legacy }).errors));
  if (Array.isArray(value.routines)) value.routines.forEach((routine, index) => errors.push(...validateRoutine(routine, { ...options, path: `routines[${index}]`, legacy }).errors));
  if (Array.isArray(value.legacyWeights)) value.legacyWeights.forEach((weight, index) => errors.push(...validateLegacyWeight(weight, { ...options, path: `legacyWeights[${index}]`, legacy }).errors));

  APPLICATION_LOCAL_DATA_FIELDS.forEach((field) => {
    if (!hasOwn(value, field)) addError(errors, field, "required_field", `${field} is required.`);
    else if (value[field] === undefined) addError(errors, field, "expected_object_or_null", `${field} must be an object or null.`);
  });
  if (value.settings !== null && value.settings !== undefined) errors.push(...validateSettings(value.settings, { path: "settings", legacy }).errors);
  if (value.goals !== null && value.goals !== undefined) errors.push(...validateGoals(value.goals, { path: "goals", legacy }).errors);
  if (value.draft !== null && value.draft !== undefined) errors.push(...validateDraft(value.draft, { ...options, path: "draft", legacy }).errors);
  if (value.backupMeta !== null && value.backupMeta !== undefined) errors.push(...validateBackupMeta(value.backupMeta, { path: "backupMeta", legacy }).errors);
  return validationResult(errors);
}

function assertResult(result, options = {}) {
  if (!result.valid) throw createValidationError(result.errors, options);
  return true;
}

export function assertValidApplicationData(value, options = {}) {
  return assertResult(validateApplicationData(value, options), options);
}

export function assertValidWorkout(value, options = {}) {
  return assertResult(validateWorkout(value, options), options);
}

export function assertValidRoutine(value, options = {}) {
  return assertResult(validateRoutine(value, options), options);
}

export function assertValidSettings(value, options = {}) {
  return assertResult(validateSettings(value, options), options);
}

export function assertValidGoals(value, options = {}) {
  return assertResult(validateGoals(value, options), options);
}

export function assertValidDraft(value, options = {}) {
  return assertResult(validateDraft(value, options), options);
}

export function assertValidBackupMeta(value, options = {}) {
  return assertResult(validateBackupMeta(value, options), options);
}

export function assertValidLegacyWeight(value, options = {}) {
  return assertResult(validateLegacyWeight(value, options), options);
}
