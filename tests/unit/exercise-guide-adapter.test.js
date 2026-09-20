import assert from "node:assert/strict";
import test from "node:test";
import {
  adaptCatalogExerciseToGuide,
  buildCatalogPreviewSummary,
  createCatalogAttribution,
} from "../../src/js/catalog/exercise-guide-adapter.js";

function exercise(overrides = {}) {
  return {
    catalogId: "test:standing-calf-raise",
    source: "free-exercise-db",
    name: "Standing Calf Raises",
    equipment: [" machine ", "machine"],
    primaryMuscles: [" calves "],
    secondaryMuscles: ["gastrocnemius", ""],
    difficulty: " beginner ",
    category: " strength ",
    instructions: [" Step one. ", "", "Step two.", "   ", "Step three."],
    attribution: {
      label: " Free Exercise DB ",
      url: " https://github.com/yuhonas/free-exercise-db ",
    },
    license: " Unlicense ",
    ...overrides,
  };
}

test("guide adaptation preserves instruction order and removes empty steps", () => {
  const guide = adaptCatalogExerciseToGuide(
    exercise({
      instructions: [
        " Step one. ",
        "",
        "Step two.",
        "Step two.",
        "   ",
        "Step three.",
      ],
    }),
  );
  assert.deepEqual(guide.steps, [
    "Step one.",
    "Step two.",
    "Step two.",
    "Step three.",
  ]);
});

test("guide adaptation normalizes equipment and muscle values", () => {
  const guide = adaptCatalogExerciseToGuide(exercise());
  assert.deepEqual(guide.equipment, ["machine"]);
  assert.deepEqual(guide.primaryMuscles, ["calves"]);
  assert.deepEqual(guide.secondaryMuscles, ["gastrocnemius"]);
  assert.equal(guide.difficulty, "beginner");
  assert.equal(guide.category, "strength");
});

test("guide adaptation only exposes pinned Free Exercise DB image URLs", () => {
  const guide = adaptCatalogExerciseToGuide(
    exercise({
      imageReferences: [
        "https://raw.githubusercontent.com/yuhonas/free-exercise-db/b0eed061e1c832b3ed815fbaa4b45b3cdc14df49/exercises/Standing_Calf_Raises/0.jpg",
        "https://example.com/untrusted.jpg",
        "not a URL",
      ],
    }),
  );
  assert.deepEqual(guide.imageReferences, [
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/b0eed061e1c832b3ed815fbaa4b45b3cdc14df49/exercises/Standing_Calf_Raises/0.jpg",
  ]);
});

test("missing instructions use the generic-guide fallback signal", () => {
  assert.equal(
    adaptCatalogExerciseToGuide(exercise({ instructions: [] })),
    null,
  );
  assert.equal(
    adaptCatalogExerciseToGuide(exercise({ instructions: null })),
    null,
  );
});

test("attribution is generated consistently", () => {
  assert.deepEqual(createCatalogAttribution(exercise()), {
    label: "Exercise information from Free Exercise DB",
    url: "https://github.com/yuhonas/free-exercise-db",
    license: "Unlicense",
  });
});

test("preview shows a strict summary instead of the full instruction set", () => {
  const summary = buildCatalogPreviewSummary(exercise());
  assert.deepEqual(summary.instructionPreview, ["Step one.", "Step two."]);
  assert.equal(summary.remainingInstructionCount, 1);
  assert.equal(summary.instructionPreview.includes("Step three."), false);
});

test("guide and preview adaptation do not mutate source data", () => {
  const source = exercise();
  const before = structuredClone(source);
  adaptCatalogExerciseToGuide(source);
  buildCatalogPreviewSummary(source);
  assert.deepEqual(source, before);
});
