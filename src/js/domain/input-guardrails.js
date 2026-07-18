export const INPUT_LIMITS = Object.freeze({
  weight: 10_000,
  repetitions: 10_000,
  rpeMinimum: 1,
  rpeMaximum: 10,
  defaultWeightJumpMinimum: 0.5,
  defaultWeightJumpMaximum: 1_000,
  repRangeMinimum: 1,
  repRangeMaximum: 1_000,
  weeklyGoalMinimum: 1,
  weeklyGoalMaximum: 100,
  displayNameLength: 80,
  exerciseNameLength: 120,
  routineNameLength: 80,
  workoutTypeLength: 80,
  workoutNoteLength: 4_000,
  exerciseNoteLength: 2_000,
  completionNoteLength: 1_000,
  tagLength: 80,
  searchLength: 200,
  exercisesPerWorkout: 100,
  setsPerExercise: 200,
  exercisesPerRoutine: 100,
  routines: 500,
  completionTags: 32,
  catalogResults: 60,
  catalogExercises: 10_000,
  catalogInstructions: 100,
  catalogInstructionLength: 2_000,
  catalogListItems: 100,
  catalogMetadataTextLength: 500,
  backupFileBytes: 25 * 1024 * 1024,
  backupDepth: 20,
  backupNodes: 500_000,
  durationMinutes: 24 * 60,
});

export const WARNING_THRESHOLDS = Object.freeze({
  weight: 2_000,
  repetitions: 500,
  weeklyGoal: 14,
  repRange: 100,
  exercisesPerWorkout: 50,
  setsPerExercise: 50,
  exercisesPerRoutine: 50,
});

const INVISIBLE_NAME_CHARACTERS = /[\s\u200b-\u200d\u2060\ufeff]/gu;

function containsUnsupportedControlCharacter(value, allowLineBreaks) {
  for (const character of value) {
    const code = character.codePointAt(0);
    if (code === 127) return true;
    if (code > 31) continue;
    if (allowLineBreaks && [9, 10, 13].includes(code)) continue;
    return true;
  }
  return false;
}

function issue(code, message, options = {}) {
  return { code, message, ...options };
}

function validationResult(normalized, errors = [], warnings = []) {
  return {
    valid: errors.length === 0,
    normalized,
    errors,
    warnings,
  };
}

export function textLength(value) {
  return Array.from(String(value ?? "")).length;
}

export function nameComparisonKey(value) {
  return String(value ?? "")
    .trim()
    .replace(/\s+/gu, " ")
    .toLocaleLowerCase();
}

export function validateTextInput(value, options = {}) {
  const label = options.label || "Value";
  const original = String(value ?? "");
  const normalized = options.trim ? original.trim() : original;
  const errors = [];
  const maximumLength = options.maximumLength;

  if (options.required) {
    const visible = normalized.replace(INVISIBLE_NAME_CHARACTERS, "");
    if (!visible) {
      errors.push(issue("required", `${label} is required.`));
    }
  }

  if (
    containsUnsupportedControlCharacter(
      normalized,
      Boolean(options.allowLineBreaks),
    )
  ) {
    errors.push(
      issue(
        "control_character",
        `${label} contains an unsupported control character.`,
      ),
    );
  }

  if (
    Number.isInteger(maximumLength) &&
    textLength(normalized) > maximumLength
  ) {
    errors.push(
      issue(
        "too_long",
        `${label} must be ${maximumLength.toLocaleString()} characters or fewer.`,
        { maximum: maximumLength },
      ),
    );
  }

  return validationResult(normalized, errors);
}

function parseNumericText(value) {
  if (typeof value === "number") {
    return Number.isFinite(value)
      ? { ok: true, number: value, normalized: String(value) }
      : { ok: false, code: "non_finite" };
  }

  const trimmed = String(value ?? "").trim();
  if (!trimmed) return { ok: false, code: "empty", normalized: "" };
  if (/[eE]/u.test(trimmed)) {
    return { ok: false, code: "scientific_notation", normalized: trimmed };
  }

  const commaCount = (trimmed.match(/,/gu) || []).length;
  const dotCount = (trimmed.match(/\./gu) || []).length;
  if (commaCount > 1 || dotCount > 1 || (commaCount && dotCount)) {
    return { ok: false, code: "invalid_number", normalized: trimmed };
  }

  const normalized = commaCount === 1 ? trimmed.replace(",", ".") : trimmed;
  const pattern = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/u;
  if (!pattern.test(normalized)) {
    return { ok: false, code: "invalid_number", normalized };
  }

  const number = Number(normalized);
  if (!Number.isFinite(number)) {
    return { ok: false, code: "non_finite", normalized };
  }
  return { ok: true, number, normalized };
}

export function validateNumericInput(value, options = {}) {
  const label = options.label || "Value";
  const parsed = parseNumericText(value);
  const errors = [];
  const warnings = [];

  if (parsed.code === "empty") {
    if (options.optional) return validationResult("", [], []);
    return validationResult("", [issue("required", `${label} is required.`)]);
  }
  if (!parsed.ok) {
    const message =
      parsed.code === "scientific_notation"
        ? `${label} must be entered without scientific notation.`
        : `${label} must be a finite number.`;
    return validationResult(parsed.normalized ?? String(value ?? ""), [
      issue(parsed.code, message),
    ]);
  }

  const { number, normalized } = parsed;
  if (options.integer && !Number.isInteger(number)) {
    errors.push(issue("integer_required", `${label} must be a whole number.`));
  }

  if (Number.isInteger(options.maximumDecimalPlaces)) {
    const fraction = normalized.replace(/^[+-]/u, "").split(".")[1] || "";
    if (fraction.length > options.maximumDecimalPlaces) {
      errors.push(
        issue(
          "too_many_decimals",
          `${label} supports up to ${options.maximumDecimalPlaces} decimal places.`,
        ),
      );
    }
  }

  if (typeof options.minimum === "number" && number < options.minimum) {
    errors.push(
      issue(
        "below_minimum",
        `${label} must be at least ${options.minimum.toLocaleString()}.`,
        { minimum: options.minimum },
      ),
    );
  }
  if (typeof options.maximum === "number" && number > options.maximum) {
    errors.push(
      issue(
        "above_maximum",
        `${label} must be ${options.maximum.toLocaleString()} or less.`,
        { maximum: options.maximum },
      ),
    );
  }

  if (typeof options.increment === "number") {
    const steps = number / options.increment;
    if (Math.abs(steps - Math.round(steps)) > Number.EPSILON * 16) {
      errors.push(
        issue(
          "unsupported_increment",
          `${label} must use increments of ${options.increment}.`,
        ),
      );
    }
  }

  if (
    typeof options.warningAbove === "number" &&
    number > options.warningAbove &&
    errors.length === 0
  ) {
    warnings.push(
      issue(
        "unusual_value",
        `${label} is unusually high. Check it before saving.`,
        { threshold: options.warningAbove },
      ),
    );
  }

  return validationResult(normalized, errors, warnings);
}

export function validateWeight(value) {
  return validateNumericInput(value, {
    label: "Weight",
    optional: true,
    minimum: 0,
    maximum: INPUT_LIMITS.weight,
    maximumDecimalPlaces: 2,
    warningAbove: WARNING_THRESHOLDS.weight,
  });
}

export function validateRepetitions(value) {
  return validateNumericInput(value, {
    label: "Repetitions",
    optional: true,
    integer: true,
    minimum: 0,
    maximum: INPUT_LIMITS.repetitions,
    warningAbove: WARNING_THRESHOLDS.repetitions,
  });
}

export function validateRpe(value, options = {}) {
  return validateNumericInput(value, {
    label: "RPE",
    optional: true,
    minimum: options.allowHistoricalZero ? 0 : INPUT_LIMITS.rpeMinimum,
    maximum: INPUT_LIMITS.rpeMaximum,
    maximumDecimalPlaces: 1,
    increment: 0.5,
  });
}

export function validateDefaultWeightJump(value) {
  return validateNumericInput(value, {
    label: "Default load jump",
    minimum: INPUT_LIMITS.defaultWeightJumpMinimum,
    maximum: INPUT_LIMITS.defaultWeightJumpMaximum,
    maximumDecimalPlaces: 2,
  });
}

export function validateWeeklyGoal(value) {
  return validateNumericInput(value, {
    label: "Weekly workout goal",
    integer: true,
    minimum: INPUT_LIMITS.weeklyGoalMinimum,
    maximum: INPUT_LIMITS.weeklyGoalMaximum,
    warningAbove: WARNING_THRESHOLDS.weeklyGoal,
  });
}

export function validateRepRange(minimum, maximum, label = "Rep range") {
  const minResult = validateNumericInput(minimum, {
    label: `${label} minimum`,
    integer: true,
    minimum: INPUT_LIMITS.repRangeMinimum,
    maximum: INPUT_LIMITS.repRangeMaximum,
    warningAbove: WARNING_THRESHOLDS.repRange,
  });
  const maxResult = validateNumericInput(maximum, {
    label: `${label} maximum`,
    integer: true,
    minimum: INPUT_LIMITS.repRangeMinimum,
    maximum: INPUT_LIMITS.repRangeMaximum,
    warningAbove: WARNING_THRESHOLDS.repRange,
  });
  const errors = [
    ...minResult.errors.map((entry) => ({ ...entry, field: "minimum" })),
    ...maxResult.errors.map((entry) => ({ ...entry, field: "maximum" })),
  ];
  if (
    minResult.valid &&
    maxResult.valid &&
    Number(minResult.normalized) > Number(maxResult.normalized)
  ) {
    errors.push(
      issue(
        "invalid_range",
        `${label} minimum must not exceed its maximum.`,
        { field: "maximum" },
      ),
    );
  }
  return validationResult(
    { minimum: minResult.normalized, maximum: maxResult.normalized },
    errors,
    [...minResult.warnings, ...maxResult.warnings],
  );
}

export function validateExerciseName(value, options = {}) {
  return validateTextInput(value, {
    label: "Exercise name",
    required: options.required !== false,
    trim: true,
    maximumLength: INPUT_LIMITS.exerciseNameLength,
  });
}

export function validateDisplayName(value) {
  return validateTextInput(value, {
    label: "Display name",
    required: true,
    trim: true,
    maximumLength: INPUT_LIMITS.displayNameLength,
  });
}

export function validateRoutineName(value) {
  return validateTextInput(value, {
    label: "Routine name",
    required: true,
    trim: true,
    maximumLength: INPUT_LIMITS.routineNameLength,
  });
}

export function validateWorkoutType(value) {
  return validateTextInput(value, {
    label: "Routine",
    required: true,
    trim: true,
    maximumLength: INPUT_LIMITS.workoutTypeLength,
  });
}

export function validateWorkoutNotes(value) {
  return validateTextInput(value, {
    label: "Workout notes",
    allowLineBreaks: true,
    maximumLength: INPUT_LIMITS.workoutNoteLength,
  });
}

export function validateExerciseNotes(value) {
  return validateTextInput(value, {
    label: "Exercise notes",
    allowLineBreaks: true,
    maximumLength: INPUT_LIMITS.exerciseNoteLength,
  });
}

export function validateCompletionNote(value) {
  return validateTextInput(value, {
    label: "Custom note",
    allowLineBreaks: true,
    maximumLength: INPUT_LIMITS.completionNoteLength,
  });
}

export function validateSearchText(value) {
  return validateTextInput(value, {
    label: "Search",
    maximumLength: INPUT_LIMITS.searchLength,
  });
}

export function validateDateInput(value, options = {}) {
  const label = options.label || "Workout date";
  const normalized = String(value ?? "").trim();
  const errors = [];
  const warnings = [];
  if (!normalized) {
    if (options.optional) return validationResult("");
    return validationResult("", [issue("required", `${label} is required.`)]);
  }
  if (!/^\d{4}-\d{2}-\d{2}$/u.test(normalized)) {
    return validationResult(normalized, [
      issue("invalid_date", `${label} must use a valid YYYY-MM-DD date.`),
    ]);
  }
  const parsed = new Date(`${normalized}T00:00:00Z`);
  if (
    Number.isNaN(parsed.getTime()) ||
    parsed.toISOString().slice(0, 10) !== normalized
  ) {
    errors.push(
      issue("invalid_date", `${label} must use a real calendar date.`),
    );
  }
  if (
    errors.length === 0 &&
    options.todayValue &&
    normalized > options.todayValue
  ) {
    warnings.push(
      issue(
        "future_date",
        "Workout date is in the future. Check it before saving.",
      ),
    );
  }
  return validationResult(normalized, errors, warnings);
}

export function validateTimeInput(value, options = {}) {
  const label = options.label || "Time";
  const normalized = String(value ?? "").trim();
  if (!normalized && options.optional !== false) return validationResult("");
  if (!/^\d{2}:\d{2}$/u.test(normalized)) {
    return validationResult(normalized, [
      issue("invalid_time", `${label} must use a valid HH:MM time.`),
    ]);
  }
  const [hour, minute] = normalized.split(":").map(Number);
  if (hour > 23 || minute > 59) {
    return validationResult(normalized, [
      issue("invalid_time", `${label} must use a real 24-hour time.`),
    ]);
  }
  return validationResult(normalized);
}

export function validateWorkoutTiming(
  { date, startTime, endTime },
  options = {},
) {
  const dateResult = validateDateInput(date, {
    todayValue: options.todayValue,
  });
  const startResult = validateTimeInput(startTime, {
    label: "Start time",
  });
  const endResult = validateTimeInput(endTime, { label: "End time" });
  const errors = [
    ...dateResult.errors.map((entry) => ({ ...entry, field: "date" })),
    ...startResult.errors.map((entry) => ({ ...entry, field: "startTime" })),
    ...endResult.errors.map((entry) => ({ ...entry, field: "endTime" })),
  ];
  const warnings = dateResult.warnings.map((entry) => ({
    ...entry,
    field: "date",
  }));
  if (
    startResult.valid &&
    endResult.valid &&
    startResult.normalized &&
    endResult.normalized &&
    endResult.normalized < startResult.normalized
  ) {
    warnings.push(
      issue(
        "overnight_time",
        "End time is earlier than start time and will be treated as overnight.",
        { field: "endTime" },
      ),
    );
  }
  return validationResult(
    {
      date: dateResult.normalized,
      startTime: startResult.normalized,
      endTime: endResult.normalized,
    },
    errors,
    warnings,
  );
}

export function validateCollectionSize(collection, options = {}) {
  const label = options.label || "Items";
  const size = Array.isArray(collection) ? collection.length : Number(collection);
  const errors = [];
  const warnings = [];
  if (!Number.isInteger(size) || size < 0) {
    errors.push(issue("invalid_collection", `${label} must be a list.`));
    return validationResult(size, errors, warnings);
  }
  if (size > options.maximum) {
    errors.push(
      issue(
        "collection_too_large",
        `${label} is limited to ${options.maximum.toLocaleString()}.`,
        { maximum: options.maximum },
      ),
    );
  } else if (
    typeof options.warningAbove === "number" &&
    size > options.warningAbove
  ) {
    warnings.push(
      issue(
        "unusual_collection_size",
        `${label} is unusually large. Check it before saving.`,
        { threshold: options.warningAbove },
      ),
    );
  }
  return validationResult(size, errors, warnings);
}

function withPath(result, path) {
  return {
    errors: result.errors.map((entry) => ({ ...entry, path })),
    warnings: result.warnings.map((entry) => ({ ...entry, path })),
  };
}

function mergeValidationParts(parts, normalized) {
  return validationResult(
    normalized,
    parts.flatMap((part) => part.errors),
    parts.flatMap((part) => part.warnings),
  );
}

export function validateSetInput(set = {}, options = {}) {
  const parts = [
    withPath(validateWeight(set.weight), `${options.path || "set"}.weight`),
    withPath(validateRepetitions(set.reps), `${options.path || "set"}.reps`),
    withPath(
      validateRpe(set.rpe, {
        allowHistoricalZero: options.allowHistoricalRpeZero,
      }),
      `${options.path || "set"}.rpe`,
    ),
  ];
  return mergeValidationParts(parts, set);
}

export function validateWorkoutInput(workout = {}, options = {}) {
  const path = options.path || "workout";
  const exercises = Array.isArray(workout.exercises) ? workout.exercises : [];
  const timing = validateWorkoutTiming(workout, {
    todayValue: options.todayValue,
  });
  const parts = [
    withPath(validateWorkoutType(workout.type), `${path}.type`),
    withPath(validateWorkoutNotes(workout.notes), `${path}.notes`),
    {
      errors: timing.errors.map((entry) => ({
        ...entry,
        path: entry.field ? `${path}.${entry.field}` : path,
      })),
      warnings: timing.warnings.map((entry) => ({
        ...entry,
        path: entry.field ? `${path}.${entry.field}` : path,
      })),
    },
    withPath(
      validateCollectionSize(exercises, {
        label: "Exercises per workout",
        maximum: INPUT_LIMITS.exercisesPerWorkout,
        warningAbove: WARNING_THRESHOLDS.exercisesPerWorkout,
      }),
      `${path}.exercises`,
    ),
  ];

  const tags = Array.isArray(workout.tags) ? workout.tags : [];
  parts.push(
    withPath(
      validateCollectionSize(tags, {
        label: "Completion tags",
        maximum: INPUT_LIMITS.completionTags,
      }),
      `${path}.tags`,
    ),
  );
  tags.forEach((tag, index) => {
    parts.push(
      withPath(
        validateTextInput(tag, {
          label: "Completion tag",
          required: true,
          trim: true,
          maximumLength: INPUT_LIMITS.tagLength,
        }),
        `${path}.tags[${index}]`,
      ),
    );
  });

  exercises.forEach((exercise, exerciseIndex) => {
    const exercisePath = `${path}.exercises[${exerciseIndex}]`;
    parts.push(
      withPath(
        validateExerciseName(exercise?.name, {
          required: options.allowIncompleteExerciseNames !== true,
        }),
        `${exercisePath}.name`,
      ),
    );
    parts.push(
      withPath(
        validateExerciseNotes(exercise?.notes),
        `${exercisePath}.notes`,
      ),
    );
    const sets = Array.isArray(exercise?.sets) ? exercise.sets : [];
    parts.push(
      withPath(
        validateCollectionSize(sets, {
          label: "Sets per exercise",
          maximum: INPUT_LIMITS.setsPerExercise,
          warningAbove: WARNING_THRESHOLDS.setsPerExercise,
        }),
        `${exercisePath}.sets`,
      ),
    );
    sets.forEach((set, setIndex) => {
      const result = validateSetInput(set, {
        path: `${exercisePath}.sets[${setIndex}]`,
        allowHistoricalRpeZero: options.allowHistoricalRpeZero,
      });
      parts.push({ errors: result.errors, warnings: result.warnings });
    });
  });

  if (
    workout.durationMinutes !== undefined &&
    workout.durationMinutes !== null &&
    workout.durationMinutes !== ""
  ) {
    parts.push(
      withPath(
        validateNumericInput(workout.durationMinutes, {
          label: "Workout duration",
          minimum: 0,
          maximum: INPUT_LIMITS.durationMinutes,
        }),
        `${path}.durationMinutes`,
      ),
    );
  }
  return mergeValidationParts(parts, workout);
}

export function validateRoutineInput(routine = {}, options = {}) {
  const path = options.path || "routine";
  const exercises = Array.isArray(routine.exercises) ? routine.exercises : [];
  const parts = [
    withPath(validateRoutineName(routine.name), `${path}.name`),
    withPath(
      validateCollectionSize(exercises, {
        label: "Exercises per routine",
        maximum: INPUT_LIMITS.exercisesPerRoutine,
        warningAbove: WARNING_THRESHOLDS.exercisesPerRoutine,
      }),
      `${path}.exercises`,
    ),
  ];
  exercises.forEach((name, index) => {
    parts.push(
      withPath(validateExerciseName(name), `${path}.exercises[${index}]`),
    );
  });
  return mergeValidationParts(parts, routine);
}

export function validateSettingsInput(settings = {}) {
  const parts = [
    withPath(
      validateDefaultWeightJump(settings.defaultWeightJump),
      "settings.defaultWeightJump",
    ),
  ];
  if (settings.displayName !== null && settings.displayName !== undefined) {
    parts.push(
      withPath(
        validateDisplayName(settings.displayName),
        "settings.displayName",
      ),
    );
  }
  for (const [key, label] of [
    ["compound", "Compound rep range"],
    ["pull", "Pull rep range"],
    ["isolation", "Isolation rep range"],
    ["general", "General rep range"],
  ]) {
    const result = validateRepRange(
      settings[`${key}Min`],
      settings[`${key}Max`],
      label,
    );
    parts.push({
      errors: result.errors.map((entry) => ({
        ...entry,
        path: `settings.${key}${entry.field === "minimum" ? "Min" : "Max"}`,
      })),
      warnings: result.warnings.map((entry) => ({
        ...entry,
        path: `settings.${key}${entry.field === "minimum" ? "Min" : "Max"}`,
      })),
    });
  }
  return mergeValidationParts(parts, settings);
}

export function validateBackupFileSize(size) {
  if (!Number.isFinite(size) || size < 0) {
    return validationResult(size, [
      issue("invalid_file_size", "Backup file size could not be read."),
    ]);
  }
  if (size === 0) {
    return validationResult(size, [
      issue("empty_file", "The selected backup file is empty."),
    ]);
  }
  if (size > INPUT_LIMITS.backupFileBytes) {
    return validationResult(size, [
      issue(
        "backup_too_large",
        "Backup files are limited to 25 MB.",
        { maximum: INPUT_LIMITS.backupFileBytes },
      ),
    ]);
  }
  return validationResult(size);
}

export function validateBackupComplexity(value, options = {}) {
  const maximumDepth = options.maximumDepth ?? INPUT_LIMITS.backupDepth;
  const maximumNodes = options.maximumNodes ?? INPUT_LIMITS.backupNodes;
  const stack = [{ value, depth: 0 }];
  const seen = new Set();
  let nodes = 0;

  while (stack.length) {
    const current = stack.pop();
    nodes += 1;
    if (nodes > maximumNodes) {
      return validationResult(value, [
        issue(
          "backup_too_complex",
          "Backup contains too many nested values to import safely.",
        ),
      ]);
    }
    if (
      current.value === null ||
      typeof current.value !== "object" ||
      seen.has(current.value)
    ) {
      continue;
    }
    seen.add(current.value);
    if (current.depth > maximumDepth) {
      return validationResult(value, [
        issue(
          "backup_too_deep",
          `Backup nesting is limited to ${maximumDepth} levels.`,
        ),
      ]);
    }
    const values = Array.isArray(current.value)
      ? current.value
      : Object.values(current.value);
    values.forEach((entry) =>
      stack.push({ value: entry, depth: current.depth + 1 }),
    );
  }
  return validationResult(value);
}

function duplicateIdentifierErrors(records, path) {
  const seen = new Set();
  const errors = [];
  records.forEach((record, index) => {
    if (record?.id === null || record?.id === undefined) return;
    const key = `${typeof record.id}:${String(record.id)}`;
    if (seen.has(key)) {
      errors.push(
        issue(
          "duplicate_id",
          `Backup contains a duplicate ID in ${path}.`,
          { path: `${path}[${index}].id` },
        ),
      );
    }
    seen.add(key);
  });
  return errors;
}

export function validateImportedApplicationData(data = {}) {
  const workouts = Array.isArray(data.workouts) ? data.workouts : [];
  const routines = Array.isArray(data.routines) ? data.routines : [];
  const legacyWeights = Array.isArray(data.legacyWeights)
    ? data.legacyWeights
    : [];
  const parts = [
    withPath(
      validateCollectionSize(routines, {
        label: "Saved routines",
        maximum: INPUT_LIMITS.routines,
      }),
      "routines",
    ),
  ];

  workouts.forEach((workout, index) => {
    const result = validateWorkoutInput(workout, {
      path: `workouts[${index}]`,
      allowHistoricalRpeZero: true,
    });
    parts.push({ errors: result.errors, warnings: [] });
  });
  routines.forEach((routine, index) => {
    const result = validateRoutineInput(routine, {
      path: `routines[${index}]`,
    });
    parts.push({ errors: result.errors, warnings: [] });
  });
  legacyWeights.forEach((record, index) => {
    parts.push(
      withPath(
        validateNumericInput(record?.weight, {
          label: "Legacy body weight",
          minimum: 0,
          maximum: INPUT_LIMITS.weight,
          maximumDecimalPlaces: 2,
        }),
        `legacyWeights[${index}].weight`,
      ),
    );
    parts.push(
      withPath(
        validateExerciseNotes(record?.notes),
        `legacyWeights[${index}].notes`,
      ),
    );
  });
  if (data.settings !== null && data.settings !== undefined) {
    const result = validateSettingsInput(data.settings);
    parts.push({ errors: result.errors, warnings: [] });
  }
  if (data.goals !== null && data.goals !== undefined) {
    parts.push(
      withPath(validateWeeklyGoal(data.goals.weeklyGoal), "goals.weeklyGoal"),
    );
  }

  const errors = [
    ...parts.flatMap((part) => part.errors),
    ...duplicateIdentifierErrors(workouts, "workouts"),
    ...duplicateIdentifierErrors(routines, "routines"),
    ...duplicateIdentifierErrors(legacyWeights, "legacyWeights"),
  ];
  return validationResult(data, errors, []);
}

export function firstValidationMessage(result) {
  return result?.errors?.[0]?.message || result?.warnings?.[0]?.message || "";
}
