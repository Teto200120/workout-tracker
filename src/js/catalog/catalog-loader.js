import { parseCatalogEnvelope } from "./catalog-contract.js";
import { searchCatalogExercises } from "./catalog-search.js";
import { createExerciseCatalogResolver } from "./exercise-catalog-resolver.js";

export const CATALOG_ASSET_URL = new URL(
  "../../data/exercise-catalog.json",
  import.meta.url,
);

const state = {
  status: "idle",
  metadata: null,
  exercises: [],
  skippedRecordCount: 0,
  warnings: [],
  error: null,
  promise: null,
  resolver: null,
};

function snapshot() {
  return {
    status: state.status,
    metadata: state.metadata,
    exercises: state.exercises,
    skippedRecordCount: state.skippedRecordCount,
    warnings: [...state.warnings],
    error: state.error,
  };
}

export function parseCatalogPayload(payload) {
  return parseCatalogEnvelope(payload);
}

export function loadCatalog({ fetchImpl = globalThis.fetch, force = false } = {}) {
  if (!force && state.status === "ready") return Promise.resolve(snapshot());
  if (!force && state.promise) return state.promise;

  state.status = "loading";
  state.error = null;
  state.promise = (async () => {
    try {
      if (typeof fetchImpl !== "function") throw new Error("Fetch is unavailable.");
      const response = await fetchImpl(CATALOG_ASSET_URL, { cache: "default" });
      if (!response.ok) {
        throw new Error(`Catalog request failed with HTTP ${response.status}.`);
      }
      const parsed = parseCatalogPayload(await response.json());
      if (!parsed.ok) throw new Error(parsed.errors.join(" "));
      state.status = "ready";
      state.metadata = parsed.metadata;
      state.exercises = parsed.exercises;
      state.resolver = createExerciseCatalogResolver(parsed.exercises);
      state.skippedRecordCount = parsed.skippedRecordCount;
      state.warnings = parsed.warnings;
      return snapshot();
    } catch (error) {
      state.status = "unavailable";
      state.metadata = null;
      state.exercises = [];
      state.skippedRecordCount = 0;
      state.warnings = [];
      state.error = error instanceof Error ? error.message : String(error);
      state.resolver = null;
      return snapshot();
    } finally {
      state.promise = null;
    }
  })();

  return state.promise;
}

export function searchCatalog(query = "", filters = {}) {
  return searchCatalogExercises(state.exercises, query, filters);
}

export function getCatalogExercise(catalogId) {
  return (
    state.exercises.find((exercise) => exercise.catalogId === catalogId) || null
  );
}

export function getCatalogStatus() {
  return {
    status: state.status,
    skippedRecordCount: state.skippedRecordCount,
    warnings: [...state.warnings],
    error: state.error,
  };
}

export function getCatalogMetadata() {
  return state.metadata ? { ...state.metadata } : null;
}

export function getLoadedCatalogExercises() {
  return [...state.exercises];
}

export function resolveLoadedCatalogExercise(exerciseName) {
  if (!state.resolver) {
    return {
      status: "unmatched",
      exercise: null,
      confidence: null,
      matchedBy: "catalog-unavailable",
    };
  }
  return state.resolver.resolve(exerciseName);
}

export function resetCatalogStateForTests() {
  state.status = "idle";
  state.metadata = null;
  state.exercises = [];
  state.skippedRecordCount = 0;
  state.warnings = [];
  state.error = null;
  state.promise = null;
  state.resolver = null;
}
