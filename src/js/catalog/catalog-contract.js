import { INPUT_LIMITS } from "../domain/input-guardrails.js";

export const CATALOG_FORMAT_VERSION = 1;

export function normalizeCatalogText(value) {
  return typeof value === "string" ? value.trim() : "";
}

export function normalizeCatalogKey(value) {
  return normalizeCatalogText(value).toLowerCase();
}

export function normalizeCatalogList(value) {
  if (!Array.isArray(value)) return [];
  const seen = new Set();
  const items = [];

  for (const entry of value) {
    const item = normalizeCatalogText(entry);
    const key = normalizeCatalogKey(item);
    if (!item || seen.has(key)) continue;
    seen.add(key);
    items.push(item);
  }

  return items;
}

export function compareCatalogText(left, right) {
  const leftKey = normalizeCatalogKey(left);
  const rightKey = normalizeCatalogKey(right);
  if (leftKey < rightKey) return -1;
  if (leftKey > rightKey) return 1;
  const leftText = normalizeCatalogText(left);
  const rightText = normalizeCatalogText(right);
  if (leftText < rightText) return -1;
  if (leftText > rightText) return 1;
  return 0;
}

export function createCatalogId(source, sourceId) {
  const sourceKey = normalizeCatalogKey(source);
  const providerId = normalizeCatalogText(sourceId);
  if (!sourceKey || !providerId) return "";
  return `${sourceKey}:${encodeURIComponent(providerId)}`;
}

function optionalText(value) {
  const text = normalizeCatalogText(value);
  return text || null;
}

function normalizeAttribution(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return { label: "", url: "" };
  }
  return {
    label: normalizeCatalogText(value.label),
    url: normalizeCatalogText(value.url),
  };
}

function validateCatalogTextLength(errors, label, value, maximum) {
  if (Array.from(value || "").length > maximum) {
    errors.push(`${label} exceeds ${maximum} characters`);
  }
}

function validateCatalogListBounds(errors, label, values, maximumLength) {
  if (values.length > INPUT_LIMITS.catalogListItems) {
    errors.push(`${label} contains too many items`);
  }
  values.forEach((value) =>
    validateCatalogTextLength(errors, label, value, maximumLength),
  );
}

export function normalizeCatalogExercise(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return { ok: false, errors: ["record must be an object"], value: null };
  }

  const source = normalizeCatalogKey(input.source);
  const sourceId = normalizeCatalogText(input.sourceId);
  const name = normalizeCatalogText(input.name);
  const expectedCatalogId = createCatalogId(source, sourceId);
  const catalogId = normalizeCatalogText(input.catalogId);
  const errors = [];

  if (!source) errors.push("source is required");
  if (!sourceId) errors.push("sourceId is required");
  if (!name) errors.push("name is required");
  if (!catalogId) errors.push("catalogId is required");
  else if (expectedCatalogId && catalogId !== expectedCatalogId) {
    errors.push("catalogId is not deterministic for source and sourceId");
  }

  validateCatalogTextLength(
    errors,
    "name",
    name,
    INPUT_LIMITS.exerciseNameLength,
  );
  for (const [label, value] of [
    ["source", source],
    ["sourceId", sourceId],
    ["catalogId", catalogId],
  ]) {
    validateCatalogTextLength(
      errors,
      label,
      value,
      INPUT_LIMITS.catalogMetadataTextLength,
    );
  }

  const equipment = normalizeCatalogList(input.equipment);
  const primaryMuscles = normalizeCatalogList(input.primaryMuscles);
  const secondaryMuscles = normalizeCatalogList(input.secondaryMuscles);
  const instructions = normalizeCatalogList(input.instructions);
  const imageReferences = normalizeCatalogList(input.imageReferences);
  for (const [label, value] of [
    ["category", optionalText(input.category) || ""],
    ["difficulty", optionalText(input.difficulty) || ""],
    ["force", optionalText(input.force) || ""],
    ["mechanic", optionalText(input.mechanic) || ""],
    ["license", normalizeCatalogText(input.license)],
  ]) {
    validateCatalogTextLength(
      errors,
      label,
      value,
      INPUT_LIMITS.catalogMetadataTextLength,
    );
  }
  validateCatalogListBounds(
    errors,
    "instructions",
    instructions,
    INPUT_LIMITS.catalogInstructionLength,
  );
  if (instructions.length > INPUT_LIMITS.catalogInstructions) {
    errors.push("instructions contains too many steps");
  }
  for (const [label, values] of [
    ["equipment", equipment],
    ["primaryMuscles", primaryMuscles],
    ["secondaryMuscles", secondaryMuscles],
    ["imageReferences", imageReferences],
  ]) {
    validateCatalogListBounds(
      errors,
      label,
      values,
      INPUT_LIMITS.catalogMetadataTextLength,
    );
  }

  const attribution = normalizeAttribution(input.attribution);
  validateCatalogTextLength(
    errors,
    "attribution label",
    attribution.label,
    INPUT_LIMITS.catalogMetadataTextLength,
  );
  validateCatalogTextLength(
    errors,
    "attribution URL",
    attribution.url,
    INPUT_LIMITS.catalogMetadataTextLength,
  );

  if (errors.length) return { ok: false, errors, value: null };

  return {
    ok: true,
    errors: [],
    value: {
      catalogId,
      source,
      sourceId,
      name,
      category: optionalText(input.category),
      difficulty: optionalText(input.difficulty),
      force: optionalText(input.force),
      mechanic: optionalText(input.mechanic),
      equipment,
      primaryMuscles,
      secondaryMuscles,
      instructions,
      imageReferences,
      attribution,
      license: normalizeCatalogText(input.license),
    },
  };
}

export function validateCatalogMetadata(metadata) {
  const errors = [];
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) {
    return { ok: false, errors: ["metadata must be an object"] };
  }

  if (metadata.catalogFormatVersion !== CATALOG_FORMAT_VERSION) {
    errors.push(
      `catalogFormatVersion must be ${CATALOG_FORMAT_VERSION}`,
    );
  }
  for (const field of [
    "source",
    "sourceUrl",
    "sourceRevision",
    "sourceCommitDate",
    "license",
    "attribution",
  ]) {
    const value = normalizeCatalogText(metadata[field]);
    if (!value) errors.push(`${field} is required`);
    validateCatalogTextLength(
      errors,
      field,
      value,
      INPUT_LIMITS.catalogMetadataTextLength,
    );
  }
  if (!Number.isInteger(metadata.exerciseCount) || metadata.exerciseCount < 0) {
    errors.push("exerciseCount must be a non-negative integer");
  }
  if (!Number.isInteger(metadata.normalizerVersion) || metadata.normalizerVersion < 1) {
    errors.push("normalizerVersion must be a positive integer");
  }
  if (metadata.imagesIncluded !== false) {
    errors.push("imagesIncluded must be false for this catalog format");
  }

  return { ok: errors.length === 0, errors };
}

export function parseCatalogEnvelope(input, { strict = false } = {}) {
  const errors = [];
  const warnings = [];
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return {
      ok: false,
      errors: ["catalog envelope must be an object"],
      warnings,
      metadata: null,
      exercises: [],
      skippedRecordCount: 0,
    };
  }

  const metadataValidation = validateCatalogMetadata(input.metadata);
  errors.push(...metadataValidation.errors);
  if (!Array.isArray(input.exercises)) {
    errors.push("exercises must be an array");
    return {
      ok: false,
      errors,
      warnings,
      metadata: input.metadata || null,
      exercises: [],
      skippedRecordCount: 0,
    };
  }
  if (input.exercises.length > INPUT_LIMITS.catalogExercises) {
    errors.push(
      `catalog contains more than ${INPUT_LIMITS.catalogExercises} exercises`,
    );
    return {
      ok: false,
      errors,
      warnings,
      metadata: input.metadata || null,
      exercises: [],
      skippedRecordCount: 0,
    };
  }

  const exercises = [];
  const seenIds = new Set();
  const seenNames = new Set();
  let skippedRecordCount = 0;

  input.exercises.forEach((record, index) => {
    const normalized = normalizeCatalogExercise(record);
    if (!normalized.ok) {
      skippedRecordCount += 1;
      warnings.push(`exercise ${index}: ${normalized.errors.join(", ")}`);
      return;
    }
    const nameKey = normalizeCatalogKey(normalized.value.name);
    if (seenIds.has(normalized.value.catalogId) || seenNames.has(nameKey)) {
      skippedRecordCount += 1;
      warnings.push(`exercise ${index}: duplicate catalog ID or name`);
      return;
    }
    seenIds.add(normalized.value.catalogId);
    seenNames.add(nameKey);
    exercises.push(normalized.value);
  });

  if (!exercises.length) errors.push("catalog contains no usable exercises");
  if (
    metadataValidation.ok &&
    input.metadata.exerciseCount !== input.exercises.length
  ) {
    const message = "metadata exerciseCount does not match the record count";
    if (strict) errors.push(message);
    else warnings.push(message);
  }
  if (strict && skippedRecordCount) {
    errors.push(`catalog contains ${skippedRecordCount} skipped record(s)`);
  }

  return {
    ok: errors.length === 0,
    errors,
    warnings,
    metadata: input.metadata || null,
    exercises,
    skippedRecordCount,
  };
}
