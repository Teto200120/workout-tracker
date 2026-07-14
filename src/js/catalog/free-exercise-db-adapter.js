import {
  compareCatalogText,
  createCatalogId,
  normalizeCatalogExercise,
  normalizeCatalogKey,
  normalizeCatalogList,
  normalizeCatalogText,
} from "./catalog-contract.js";

export const FREE_EXERCISE_DB_SOURCE = "free-exercise-db";

const DEFAULT_SOURCE_METADATA = {
  label: "Free Exercise DB",
  url: "https://github.com/yuhonas/free-exercise-db",
  license: "Unlicense",
};

function scalarList(value) {
  const text = normalizeCatalogText(value);
  return text ? [text] : [];
}

export function normalizeFreeExerciseDbRecord(
  record,
  sourceMetadata = DEFAULT_SOURCE_METADATA,
) {
  if (!record || typeof record !== "object" || Array.isArray(record)) {
    return { ok: false, errors: ["provider record must be an object"] };
  }

  const sourceId = normalizeCatalogText(record.id);
  const name = normalizeCatalogText(record.name);
  const errors = [];
  if (!sourceId) errors.push("provider id is required");
  if (!name) errors.push("provider name is required");
  if (errors.length) return { ok: false, errors };

  return normalizeCatalogExercise({
    catalogId: createCatalogId(FREE_EXERCISE_DB_SOURCE, sourceId),
    source: FREE_EXERCISE_DB_SOURCE,
    sourceId,
    name,
    category: record.category,
    difficulty: record.level,
    force: record.force,
    mechanic: record.mechanic,
    equipment: scalarList(record.equipment),
    primaryMuscles: normalizeCatalogList(record.primaryMuscles),
    secondaryMuscles: normalizeCatalogList(record.secondaryMuscles),
    instructions: normalizeCatalogList(record.instructions),
    imageReferences: [],
    attribution: {
      label: normalizeCatalogText(sourceMetadata.label),
      url: normalizeCatalogText(sourceMetadata.url),
    },
    license: normalizeCatalogText(sourceMetadata.license),
  });
}

export function adaptFreeExerciseDbRecords(
  records,
  sourceMetadata = DEFAULT_SOURCE_METADATA,
) {
  if (!Array.isArray(records)) {
    return {
      exercises: [],
      malformedRecords: [{ index: null, errors: ["records must be an array"] }],
      duplicateIds: [],
      duplicateNames: [],
      skippedRecordCount: 1,
    };
  }

  const candidates = [];
  const malformedRecords = [];
  records.forEach((record, index) => {
    const normalized = normalizeFreeExerciseDbRecord(record, sourceMetadata);
    if (!normalized.ok) {
      malformedRecords.push({ index, errors: normalized.errors });
      return;
    }
    candidates.push({ index, exercise: normalized.value });
  });

  candidates.sort(
    (left, right) =>
      compareCatalogText(left.exercise.name, right.exercise.name) ||
      compareCatalogText(left.exercise.catalogId, right.exercise.catalogId) ||
      left.index - right.index,
  );

  const exercises = [];
  const seenIds = new Set();
  const seenNames = new Set();
  const duplicateIds = [];
  const duplicateNames = [];

  for (const candidate of candidates) {
    const { exercise, index } = candidate;
    const nameKey = normalizeCatalogKey(exercise.name);
    if (seenIds.has(exercise.catalogId)) {
      duplicateIds.push({ index, catalogId: exercise.catalogId });
      continue;
    }
    if (seenNames.has(nameKey)) {
      duplicateNames.push({ index, name: exercise.name });
      continue;
    }
    seenIds.add(exercise.catalogId);
    seenNames.add(nameKey);
    exercises.push(exercise);
  }

  return {
    exercises,
    malformedRecords,
    duplicateIds,
    duplicateNames,
    skippedRecordCount:
      malformedRecords.length + duplicateIds.length + duplicateNames.length,
  };
}
