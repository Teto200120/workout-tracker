import {
  compareCatalogText,
  normalizeCatalogKey,
  normalizeCatalogText,
} from "./catalog-contract.js";

export function catalogNameMatchRank(name, query) {
  const nameKey = normalizeCatalogKey(name);
  const queryKey = normalizeCatalogKey(query);
  if (!nameKey) return null;
  if (!queryKey) return 3;
  if (nameKey === queryKey) return 0;
  if (nameKey.startsWith(queryKey)) return 1;
  if (nameKey.includes(queryKey)) return 2;
  return null;
}

function matchesFilter(exercise, field, value) {
  const key = normalizeCatalogKey(value);
  if (!key) return true;
  if (field === "primaryMuscle") {
    return exercise.primaryMuscles.some(
      (muscle) => normalizeCatalogKey(muscle) === key,
    );
  }
  if (field === "equipment") {
    return exercise.equipment.some(
      (equipment) => normalizeCatalogKey(equipment) === key,
    );
  }
  if (field === "category") {
    return normalizeCatalogKey(exercise.category) === key;
  }
  return true;
}

export function searchCatalogExercises(
  exercises = [],
  query = "",
  filters = {},
) {
  return exercises
    .map((exercise, index) => ({
      exercise,
      index,
      rank: catalogNameMatchRank(exercise?.name, query),
    }))
    .filter(
      ({ exercise, rank }) =>
        rank !== null &&
        matchesFilter(exercise, "primaryMuscle", filters.primaryMuscle) &&
        matchesFilter(exercise, "equipment", filters.equipment) &&
        matchesFilter(exercise, "category", filters.category),
    )
    .sort(
      (left, right) =>
        left.rank - right.rank ||
        compareCatalogText(left.exercise.name, right.exercise.name) ||
        compareCatalogText(
          left.exercise.catalogId,
          right.exercise.catalogId,
        ) ||
        left.index - right.index,
    )
    .map(({ exercise }) => exercise);
}

function searchLocalNames(names, query) {
  return names
    .map((name, index) => ({
      name: normalizeCatalogText(name),
      index,
      rank: catalogNameMatchRank(name, query),
    }))
    .filter(({ name, rank }) => name && rank !== null)
    .sort(
      (left, right) =>
        left.rank - right.rank ||
        compareCatalogText(left.name, right.name) ||
        left.index - right.index,
    )
    .map(({ name }) => name);
}

export function mergeLocalAndCatalogResults({
  localNames = [],
  catalogExercises = [],
  query = "",
  filters = {},
} = {}) {
  const local = searchLocalNames(localNames, query);
  const localKeys = new Set(localNames.map(normalizeCatalogKey).filter(Boolean));
  const catalog = searchCatalogExercises(catalogExercises, query, filters).filter(
    (exercise) => !localKeys.has(normalizeCatalogKey(exercise.name)),
  );
  return { local, catalog };
}

export function resolveCatalogCanonicalName(
  value,
  localNames = [],
  catalogExercises = [],
) {
  const name = normalizeCatalogText(value);
  const key = normalizeCatalogKey(name);
  if (!key) return "";
  const local = localNames.find((option) => normalizeCatalogKey(option) === key);
  if (local) return normalizeCatalogText(local);
  const catalog = catalogExercises.find(
    (exercise) => normalizeCatalogKey(exercise?.name) === key,
  );
  return catalog?.name || name;
}

export function getCatalogFilterOptions(exercises = []) {
  const unique = (values) =>
    [...new Set(values.map(normalizeCatalogText).filter(Boolean))].sort(
      compareCatalogText,
    );
  return {
    primaryMuscles: unique(exercises.flatMap((item) => item.primaryMuscles || [])),
    equipment: unique(exercises.flatMap((item) => item.equipment || [])),
    categories: unique(exercises.map((item) => item.category)),
  };
}
