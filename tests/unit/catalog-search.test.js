import assert from "node:assert/strict";
import test from "node:test";
import {
  catalogNameMatchRank,
  getCatalogFilterOptions,
  mergeLocalAndCatalogResults,
  resolveCatalogCanonicalName,
  searchCatalogExercises,
} from "../../src/js/catalog/catalog-search.js";

function catalogExercise(name, overrides = {}) {
  const sourceId = name.replaceAll(" ", "_");
  return {
    catalogId: `free-exercise-db:${sourceId}`,
    source: "free-exercise-db",
    sourceId,
    name,
    category: "strength",
    difficulty: "beginner",
    force: null,
    mechanic: null,
    equipment: ["barbell"],
    primaryMuscles: ["chest"],
    secondaryMuscles: [],
    instructions: [],
    imageReferences: [],
    attribution: { label: "Free Exercise DB", url: "https://example.test" },
    license: "Unlicense",
    ...overrides,
  };
}

const catalog = [
  catalogExercise("Incline Press"),
  catalogExercise("Press Under"),
  catalogExercise("Barbell Press"),
  catalogExercise("Press", { equipment: ["body only"] }),
  catalogExercise("Cable Row", {
    category: "strength",
    equipment: ["cable"],
    primaryMuscles: ["middle back"],
  }),
  catalogExercise("Chest Stretch", {
    category: "stretching",
    equipment: [],
  }),
];

test("name matching is trimmed and ranks exact, prefix, then substring", () => {
  assert.equal(catalogNameMatchRank("Press", " press "), 0);
  assert.equal(catalogNameMatchRank("Press Under", "press"), 1);
  assert.equal(catalogNameMatchRank("Barbell Press", "press"), 2);
  assert.equal(catalogNameMatchRank("Cable Row", "press"), null);
});

test("search ranking is exact, prefix, substring, then stable by name", () => {
  assert.deepEqual(
    searchCatalogExercises(catalog, "press").map((item) => item.name),
    ["Press", "Press Under", "Barbell Press", "Incline Press"],
  );
});

test("empty query returns a deterministic name ordering", () => {
  assert.deepEqual(
    searchCatalogExercises(catalog, "   ").map((item) => item.name),
    [
      "Barbell Press",
      "Cable Row",
      "Chest Stretch",
      "Incline Press",
      "Press",
      "Press Under",
    ],
  );
});

test("muscle, equipment, and category filters compose", () => {
  assert.deepEqual(
    searchCatalogExercises(catalog, "", { primaryMuscle: "middle BACK" }).map(
      (item) => item.name,
    ),
    ["Cable Row"],
  );
  assert.deepEqual(
    searchCatalogExercises(catalog, "", { equipment: "body only" }).map(
      (item) => item.name,
    ),
    ["Press"],
  );
  assert.deepEqual(
    searchCatalogExercises(catalog, "chest", { category: "stretching" }).map(
      (item) => item.name,
    ),
    ["Chest Stretch"],
  );
});

test("local results stay first and preserve their canonical name", () => {
  const merged = mergeLocalAndCatalogResults({
    localNames: ["BARBELL PRESS", "Cable Curl"],
    catalogExercises: catalog,
    query: "press",
  });
  assert.deepEqual(merged.local, ["BARBELL PRESS"]);
  assert.equal(
    merged.catalog.some((exercise) => exercise.name === "Barbell Press"),
    false,
  );
  assert.deepEqual(
    merged.catalog.map((exercise) => exercise.name),
    ["Press", "Press Under", "Incline Press"],
  );
});

test("catalog filters never hide matching local options", () => {
  const merged = mergeLocalAndCatalogResults({
    localNames: ["Home Press"],
    catalogExercises: catalog,
    query: "press",
    filters: { equipment: "cable" },
  });
  assert.deepEqual(merged.local, ["Home Press"]);
  assert.deepEqual(merged.catalog, []);
});

test("custom matching prefers local then catalog canonical capitalization", () => {
  assert.equal(
    resolveCatalogCanonicalName(" barbell press ", ["BARBELL PRESS"], catalog),
    "BARBELL PRESS",
  );
  assert.equal(
    resolveCatalogCanonicalName(" incline press ", [], catalog),
    "Incline Press",
  );
  assert.equal(
    resolveCatalogCanonicalName(" New Custom Move ", [], catalog),
    "New Custom Move",
  );
});

test("filter option extraction is unique and stable", () => {
  assert.deepEqual(getCatalogFilterOptions(catalog), {
    primaryMuscles: ["chest", "middle back"],
    equipment: ["barbell", "body only", "cable"],
    categories: ["strength", "stretching"],
  });
});
