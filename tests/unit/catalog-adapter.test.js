import assert from "node:assert/strict";
import test from "node:test";
import {
  CATALOG_FORMAT_VERSION,
  createCatalogId,
  normalizeCatalogExercise,
  parseCatalogEnvelope,
  validateCatalogMetadata,
} from "../../src/js/catalog/catalog-contract.js";
import {
  adaptFreeExerciseDbRecords,
  normalizeFreeExerciseDbRecord,
} from "../../src/js/catalog/free-exercise-db-adapter.js";
import {
  getCatalogStatus,
  loadCatalog,
  resetCatalogStateForTests,
  searchCatalog,
} from "../../src/js/catalog/catalog-loader.js";
import { INPUT_LIMITS } from "../../src/js/domain/input-guardrails.js";

function providerRecord(overrides = {}) {
  return {
    id: "Barbell_Bench_Press",
    name: " Barbell Bench Press ",
    force: "push",
    level: "beginner",
    mechanic: "compound",
    equipment: "barbell",
    primaryMuscles: ["chest", " chest "],
    secondaryMuscles: ["triceps", "shoulders"],
    instructions: ["Lie on the bench.", "Press the bar upward."],
    category: "strength",
    images: ["Barbell_Bench_Press/0.jpg"],
    ...overrides,
  };
}

function metadata(overrides = {}) {
  return {
    catalogFormatVersion: CATALOG_FORMAT_VERSION,
    normalizerVersion: 1,
    source: "free-exercise-db",
    sourceUrl: "https://github.com/yuhonas/free-exercise-db",
    sourceRevision: "revision-1",
    sourceCommitDate: "2026-05-24T03:09:39Z",
    license: "Unlicense",
    attribution: "Free Exercise DB",
    exerciseCount: 1,
    imagesIncluded: false,
    ...overrides,
  };
}

test("provider records normalize into the stable internal contract", () => {
  const normalized = normalizeFreeExerciseDbRecord(providerRecord());
  assert.equal(normalized.ok, true);
  assert.deepEqual(normalized.value, {
    catalogId: "free-exercise-db:Barbell_Bench_Press",
    source: "free-exercise-db",
    sourceId: "Barbell_Bench_Press",
    name: "Barbell Bench Press",
    category: "strength",
    difficulty: "beginner",
    force: "push",
    mechanic: "compound",
    equipment: ["barbell"],
    primaryMuscles: ["chest"],
    secondaryMuscles: ["triceps", "shoulders"],
    instructions: ["Lie on the bench.", "Press the bar upward."],
    imageReferences: [],
    attribution: {
      label: "Free Exercise DB",
      url: "https://github.com/yuhonas/free-exercise-db",
    },
    license: "Unlicense",
  });
});

test("missing optional provider fields normalize explicitly", () => {
  const normalized = normalizeFreeExerciseDbRecord(
    providerRecord({
      force: null,
      mechanic: undefined,
      equipment: null,
      primaryMuscles: undefined,
      secondaryMuscles: null,
      instructions: [],
      category: undefined,
      level: undefined,
    }),
  );
  assert.equal(normalized.ok, true);
  assert.equal(normalized.value.force, null);
  assert.equal(normalized.value.mechanic, null);
  assert.equal(normalized.value.category, null);
  assert.equal(normalized.value.difficulty, null);
  assert.deepEqual(normalized.value.equipment, []);
  assert.deepEqual(normalized.value.primaryMuscles, []);
  assert.deepEqual(normalized.value.secondaryMuscles, []);
  assert.deepEqual(normalized.value.instructions, []);
});

test("malformed provider records are skipped and reported", () => {
  const result = adaptFreeExerciseDbRecords([
    providerRecord(),
    providerRecord({ id: "", name: "Missing ID" }),
    null,
  ]);
  assert.equal(result.exercises.length, 1);
  assert.equal(result.malformedRecords.length, 2);
  assert.equal(result.skippedRecordCount, 2);
  assert.match(result.malformedRecords[0].errors.join(" "), /id is required/);
});

test("normalization does not mutate provider input", () => {
  const input = providerRecord();
  const before = structuredClone(input);
  normalizeFreeExerciseDbRecord(input);
  assert.deepEqual(input, before);
});

test("catalog IDs are deterministic and contract validation rejects drift", () => {
  const catalogId = createCatalogId(
    " Free-Exercise-DB ",
    "Barbell_Bench_Press",
  );
  assert.equal(catalogId, "free-exercise-db:Barbell_Bench_Press");
  const invalid = normalizeCatalogExercise({
    ...normalizeFreeExerciseDbRecord(providerRecord()).value,
    catalogId: "provider:random-id",
  });
  assert.equal(invalid.ok, false);
  assert.match(invalid.errors.join(" "), /not deterministic/);
});

test("provider deduplication is case-insensitive and deterministic", () => {
  const result = adaptFreeExerciseDbRecords([
    providerRecord({ id: "second", name: "barbell bench press" }),
    providerRecord({ id: "first", name: "Barbell Bench Press" }),
    providerRecord({ id: "first", name: "Different Name" }),
  ]);
  assert.deepEqual(
    result.exercises.map((exercise) => exercise.sourceId),
    ["first"],
  );
  assert.deepEqual(
    result.exercises.map((exercise) => exercise.name),
    ["Barbell Bench Press"],
  );
  assert.equal(result.duplicateIds.length, 1);
  assert.equal(result.duplicateNames.length, 1);
  assert.equal(result.skippedRecordCount, 2);
});

test("unknown provider fields stay outside the normalized contract", () => {
  const normalized = normalizeFreeExerciseDbRecord(
    providerRecord({ secretProviderFlag: true, nested: { vendor: "value" } }),
  );
  assert.equal(normalized.ok, true);
  assert.equal("secretProviderFlag" in normalized.value, false);
  assert.equal("nested" in normalized.value, false);
  assert.equal("images" in normalized.value, false);
});

test("catalog metadata and strict envelope validation detect mismatches", () => {
  assert.equal(validateCatalogMetadata(metadata()).ok, true);
  assert.equal(
    validateCatalogMetadata(metadata({ sourceRevision: "" })).ok,
    false,
  );
  const exercise = normalizeFreeExerciseDbRecord(providerRecord()).value;
  const mismatch = parseCatalogEnvelope(
    { metadata: metadata({ exerciseCount: 2 }), exercises: [exercise] },
    { strict: true },
  );
  assert.equal(mismatch.ok, false);
  assert.match(mismatch.errors.join(" "), /exerciseCount/);
});

test("runtime envelope parsing keeps usable records when one record is malformed", () => {
  const exercise = normalizeFreeExerciseDbRecord(providerRecord()).value;
  const parsed = parseCatalogEnvelope({
    metadata: metadata({ exerciseCount: 2 }),
    exercises: [exercise, { source: "free-exercise-db" }],
  });
  assert.equal(parsed.ok, true);
  assert.equal(parsed.exercises.length, 1);
  assert.equal(parsed.skippedRecordCount, 1);
  assert.match(parsed.warnings.join(" "), /sourceId is required/);
});

test("catalog records with extreme names or instructions fail closed without mutating usable records", () => {
  const usable = normalizeFreeExerciseDbRecord(providerRecord()).value;
  const extreme = {
    ...structuredClone(usable),
    sourceId: "extreme",
    catalogId: createCatalogId("free-exercise-db", "extreme"),
    name: "x".repeat(INPUT_LIMITS.exerciseNameLength + 1),
    instructions: ["x".repeat(INPUT_LIMITS.catalogInstructionLength + 1)],
  };
  const before = structuredClone(usable);
  const parsed = parseCatalogEnvelope({
    metadata: metadata({ exerciseCount: 2 }),
    exercises: [usable, extreme],
  });
  assert.equal(parsed.ok, true);
  assert.equal(parsed.exercises.length, 1);
  assert.equal(parsed.skippedRecordCount, 1);
  assert.match(parsed.warnings.join(" "), /exceeds/u);
  assert.deepEqual(usable, before);
});

test("catalog load failure exposes a non-blocking unavailable fallback", async () => {
  resetCatalogStateForTests();
  const result = await loadCatalog({
    fetchImpl: async () => {
      throw new Error("simulated catalog failure");
    },
  });
  assert.equal(result.status, "unavailable");
  assert.match(result.error, /simulated catalog failure/);
  assert.deepEqual(searchCatalog("press"), []);
  assert.equal(getCatalogStatus().status, "unavailable");
  resetCatalogStateForTests();
});
