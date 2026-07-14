import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { EXERCISE_CATALOG_ALIASES } from "../../src/js/catalog/exercise-aliases.js";
import {
  createExerciseCatalogResolver,
  normalizeExerciseName,
  resolveExerciseCatalogEntry,
} from "../../src/js/catalog/exercise-catalog-resolver.js";

function catalogExercise(
  name,
  catalogId = `test:${name.replaceAll(" ", "_")}`,
) {
  return {
    catalogId,
    source: "test",
    sourceId: catalogId,
    name,
    equipment: [],
    primaryMuscles: [],
    secondaryMuscles: [],
    instructions: ["First step"],
  };
}

const benchPress = catalogExercise("Bench Press", "test:bench-press");
const inclineBenchPress = catalogExercise(
  "Incline Bench Press",
  "test:incline-bench-press",
);
const barbellRow = catalogExercise("Barbell Row", "test:barbell-row");
const seatedCableRow = catalogExercise(
  "Seated Cable Row",
  "test:seated-cable-row",
);
const oneArmDumbbellRow = catalogExercise(
  "One Arm Dumbbell Row",
  "test:one-arm-dumbbell-row",
);
const catalog = [
  benchPress,
  inclineBenchPress,
  barbellRow,
  seatedCableRow,
  oneArmDumbbellRow,
];

test("exact resolution normalizes case, repeated spaces, and safe punctuation", () => {
  const resolver = createExerciseCatalogResolver(catalog);
  for (const name of ["Bench Press", "bench press", "  Bench   Press  "]) {
    const result = resolver.resolve(name);
    assert.equal(result.status, "exact");
    assert.equal(result.exercise, benchPress);
    assert.equal(result.matchedBy, "normalized-name");
  }

  assert.equal(
    normalizeExerciseName("One-Arm Dumbbell Row"),
    "one arm dumbbell row",
  );
  assert.equal(
    resolver.resolve("One-Arm Dumbbell Row").exercise,
    oneArmDumbbellRow,
  );
});

test("reviewed aliases resolve only when their deterministic target exists", () => {
  const target = catalogExercise("Standing Calf Raises", "test:calves");
  const aliases = [
    { localName: "Standing Calf Raise", catalogId: target.catalogId },
  ];
  const result = resolveExerciseCatalogEntry("Standing Calf Raise", [target], {
    aliases,
  });
  assert.equal(result.status, "alias");
  assert.equal(result.exercise, target);
  assert.equal(result.matchedBy, "reviewed-alias");

  const missing = resolveExerciseCatalogEntry("Standing Calf Raise", [], {
    aliases,
  });
  assert.equal(missing.status, "unmatched");
  assert.equal(missing.matchedBy, "catalog-unavailable");

  const missingFromPopulatedCatalog = resolveExerciseCatalogEntry(
    "Standing Calf Raise",
    [benchPress],
    { aliases },
  );
  assert.equal(missingFromPopulatedCatalog.status, "unmatched");
  assert.equal(missingFromPopulatedCatalog.matchedBy, "alias-target-missing");
});

test("every shipped alias points to a present catalog record", async () => {
  const payload = JSON.parse(
    await readFile("src/data/exercise-catalog.json", "utf8"),
  );
  const ids = new Set(payload.exercises.map((exercise) => exercise.catalogId));
  for (const alias of EXERCISE_CATALOG_ALIASES) {
    assert.ok(ids.has(alias.catalogId), `${alias.localName} target is missing`);
  }
});

test("related press and row variants remain distinct without fuzzy guesses", () => {
  const resolver = createExerciseCatalogResolver(catalog);
  assert.equal(resolver.resolve("Bench Press").exercise, benchPress);
  assert.notEqual(resolver.resolve("Bench Press").exercise, inclineBenchPress);
  assert.equal(resolver.resolve("Decline Bench Press").status, "unmatched");
  assert.equal(resolver.resolve("Close-Grip Bench Press").status, "unmatched");
  assert.equal(resolver.resolve("Row").status, "unmatched");
  assert.equal(resolver.resolve("V-Bar Row").status, "unmatched");
  assert.equal(resolver.resolve("My Saturday Row").status, "unmatched");
});

test("a broad lat pulldown name does not select a grip-specific variant", () => {
  const resolver = createExerciseCatalogResolver([
    catalogExercise("Wide-Grip Lat Pulldown", "test:wide-lat-pulldown"),
    catalogExercise("Close-Grip Lat Pulldown", "test:close-lat-pulldown"),
  ]);
  const result = resolver.resolve("Lat Pulldown");
  assert.equal(result.status, "unmatched");
  assert.equal(result.matchedBy, "no-safe-match");
});

test("resolution is deterministic and does not mutate names or catalog records", () => {
  const frozenCatalog = catalog.map((exercise) =>
    Object.freeze({
      ...exercise,
      equipment: Object.freeze([...exercise.equipment]),
      primaryMuscles: Object.freeze([...exercise.primaryMuscles]),
      secondaryMuscles: Object.freeze([...exercise.secondaryMuscles]),
      instructions: Object.freeze([...exercise.instructions]),
    }),
  );
  Object.freeze(frozenCatalog);
  const localName = "  BENCH   PRESS ";
  const before = JSON.stringify(frozenCatalog);
  const resolver = createExerciseCatalogResolver(frozenCatalog);
  const first = resolver.resolve(localName);
  const second = resolver.resolve(localName);

  assert.deepEqual(first, second);
  assert.equal(localName, "  BENCH   PRESS ");
  assert.equal(first.exercise.name, "Bench Press");
  assert.equal(JSON.stringify(frozenCatalog), before);
});

test("duplicate normalized names fail closed", () => {
  const result = resolveExerciseCatalogEntry("Bench Press", [
    benchPress,
    catalogExercise(" bench   press ", "test:duplicate-bench"),
  ]);
  assert.equal(result.status, "unmatched");
  assert.equal(result.matchedBy, "ambiguous-normalized-name");
});

test("missing and malformed catalogs return a structured unmatched result", () => {
  assert.deepEqual(resolveExerciseCatalogEntry("Bench Press", null), {
    status: "unmatched",
    exercise: null,
    confidence: null,
    matchedBy: "catalog-unavailable",
  });
  assert.equal(
    resolveExerciseCatalogEntry("Bench Press", [
      null,
      {},
      { name: "Bench Press" },
    ]).status,
    "unmatched",
  );
});

test("records with missing instructions still resolve without changing the local name", () => {
  const exercise = catalogExercise("Standing Calf Raise", "test:calf");
  delete exercise.instructions;
  const savedName = "standing calf raise";
  const result = resolveExerciseCatalogEntry(savedName, [exercise]);
  assert.equal(result.status, "exact");
  assert.equal(result.exercise, exercise);
  assert.equal(savedName, "standing calf raise");
});
