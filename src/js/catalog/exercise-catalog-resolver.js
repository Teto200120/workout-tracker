import { EXERCISE_CATALOG_ALIASES } from "./exercise-aliases.js";

const SAFE_DASHES = /[-\u2010-\u2015]+/g;
const SAFE_APOSTROPHES = /['\u2019]+/g;
const SAFE_SEPARATORS = /[.,:;/\\()[\]{}]+/g;

export function normalizeExerciseName(value) {
  if (typeof value !== "string") return "";
  return value
    .normalize("NFKC")
    .trim()
    .toLocaleLowerCase("en-US")
    .replace(SAFE_APOSTROPHES, "")
    .replace(SAFE_DASHES, " ")
    .replace(SAFE_SEPARATORS, " ")
    .replace(/\s+/g, " ");
}

function unmatched(matchedBy) {
  return {
    status: "unmatched",
    exercise: null,
    confidence: null,
    matchedBy,
  };
}

function matched(status, exercise, matchedBy) {
  return {
    status,
    exercise,
    confidence: 1,
    matchedBy,
  };
}

function isIndexableExercise(exercise) {
  return Boolean(
    exercise &&
      typeof exercise === "object" &&
      !Array.isArray(exercise) &&
      typeof exercise.catalogId === "string" &&
      exercise.catalogId.trim() &&
      normalizeExerciseName(exercise.name),
  );
}

function addToIndex(index, key, exercise) {
  const entries = index.get(key) || [];
  if (!entries.some((entry) => entry.catalogId === exercise.catalogId)) {
    entries.push(exercise);
  }
  index.set(key, entries);
}

function buildAliasIndex(aliases) {
  const index = new Map();
  if (!Array.isArray(aliases)) return index;

  for (const alias of aliases) {
    const key = normalizeExerciseName(alias?.localName);
    const catalogId =
      typeof alias?.catalogId === "string" ? alias.catalogId.trim() : "";
    if (!key || !catalogId) continue;
    const targets = index.get(key) || [];
    if (!targets.includes(catalogId)) targets.push(catalogId);
    index.set(key, targets);
  }
  return index;
}

export function createExerciseCatalogResolver(
  catalogExercises = [],
  { aliases = EXERCISE_CATALOG_ALIASES } = {},
) {
  const nameIndex = new Map();
  const idIndex = new Map();
  const aliasIndex = buildAliasIndex(aliases);
  const exercises = Array.isArray(catalogExercises) ? catalogExercises : [];

  for (const exercise of exercises) {
    if (!isIndexableExercise(exercise)) continue;
    addToIndex(nameIndex, normalizeExerciseName(exercise.name), exercise);
    addToIndex(idIndex, exercise.catalogId.trim(), exercise);
  }

  return Object.freeze({
    resolve(localExerciseName) {
      const key = normalizeExerciseName(localExerciseName);
      if (!key) return unmatched("empty-name");
      if (!nameIndex.size) return unmatched("catalog-unavailable");

      const exactMatches = nameIndex.get(key) || [];
      if (exactMatches.length === 1) {
        return matched("exact", exactMatches[0], "normalized-name");
      }
      if (exactMatches.length > 1) {
        return unmatched("ambiguous-normalized-name");
      }

      const aliasTargets = aliasIndex.get(key) || [];
      if (aliasTargets.length > 1) return unmatched("ambiguous-alias");
      if (aliasTargets.length === 1) {
        const aliasMatches = idIndex.get(aliasTargets[0]) || [];
        if (aliasMatches.length === 1) {
          return matched("alias", aliasMatches[0], "reviewed-alias");
        }
        return unmatched(
          aliasMatches.length > 1
            ? "ambiguous-alias-target"
            : "alias-target-missing",
        );
      }

      // Deliberately no fuzzy fallback: related exercise variants are safer as
      // false negatives than as an incorrect automatic guide match.
      return unmatched("no-safe-match");
    },
  });
}

export function resolveExerciseCatalogEntry(
  localExerciseName,
  catalogExercises = [],
  options = {},
) {
  return createExerciseCatalogResolver(catalogExercises, options).resolve(
    localExerciseName,
  );
}
