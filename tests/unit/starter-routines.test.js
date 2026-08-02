import assert from "node:assert/strict";
import test from "node:test";
import { buildCatalogStarterRoutines } from "../../src/js/catalog/starter-routines.js";

function catalogExercise(index, fields) {
  return {
    catalogId: `test-catalog:exercise-${index}`,
    name: `Catalog Exercise ${String(index).padStart(2, "0")}`,
    category: fields.category || "strength",
    mechanic: fields.mechanic || null,
    equipment: [fields.equipment],
    primaryMuscles: [fields.primaryMuscle],
    secondaryMuscles: fields.secondaryMuscles || [],
    instructions: ["Catalog instruction."],
  };
}

function completeCatalog() {
  return [
    catalogExercise(1, {
      primaryMuscle: "chest",
      mechanic: "compound",
      equipment: "barbell",
      secondaryMuscles: ["shoulders", "triceps"],
    }),
    catalogExercise(2, {
      primaryMuscle: "chest",
      mechanic: "compound",
      equipment: "dumbbell",
      secondaryMuscles: ["shoulders", "triceps"],
    }),
    catalogExercise(3, {
      primaryMuscle: "chest",
      mechanic: "isolation",
      equipment: "cable",
    }),
    catalogExercise(4, {
      primaryMuscle: "triceps",
      mechanic: "isolation",
      equipment: "cable",
    }),
    catalogExercise(5, {
      primaryMuscle: "lats",
      mechanic: "compound",
      equipment: "cable",
      secondaryMuscles: ["biceps", "middle back"],
    }),
    catalogExercise(6, {
      primaryMuscle: "middle back",
      mechanic: "compound",
      equipment: "cable",
      secondaryMuscles: ["biceps", "lats"],
    }),
    catalogExercise(7, {
      primaryMuscle: "biceps",
      mechanic: "isolation",
      equipment: "dumbbell",
    }),
    catalogExercise(8, {
      primaryMuscle: "biceps",
      mechanic: "isolation",
      equipment: "cable",
    }),
    catalogExercise(9, {
      primaryMuscle: "quadriceps",
      mechanic: "compound",
      equipment: "barbell",
      secondaryMuscles: ["calves", "glutes", "hamstrings", "lower back"],
    }),
    catalogExercise(10, {
      primaryMuscle: "hamstrings",
      mechanic: "compound",
      equipment: "barbell",
      secondaryMuscles: ["calves", "glutes", "lower back"],
    }),
    catalogExercise(11, {
      primaryMuscle: "quadriceps",
      mechanic: "compound",
      equipment: "machine",
      secondaryMuscles: ["calves", "glutes", "hamstrings"],
    }),
    catalogExercise(12, {
      primaryMuscle: "calves",
      mechanic: "isolation",
      equipment: "machine",
    }),
    catalogExercise(13, {
      primaryMuscle: "shoulders",
      mechanic: "compound",
      equipment: "dumbbell",
      secondaryMuscles: ["triceps"],
    }),
    catalogExercise(14, {
      primaryMuscle: "shoulders",
      mechanic: "isolation",
      equipment: "dumbbell",
    }),
    catalogExercise(15, {
      primaryMuscle: "shoulders",
      mechanic: "isolation",
      equipment: "machine",
    }),
    catalogExercise(16, {
      primaryMuscle: "traps",
      mechanic: "isolation",
      equipment: "barbell",
    }),
    catalogExercise(17, {
      category: "stretching",
      primaryMuscle: "hamstrings",
      equipment: "body only",
    }),
    catalogExercise(18, {
      category: "plyometrics",
      primaryMuscle: "quadriceps",
      equipment: "body only",
    }),
    catalogExercise(19, {
      category: "plyometrics",
      primaryMuscle: "quadriceps",
      equipment: "body only",
    }),
    catalogExercise(20, {
      category: "stretching",
      primaryMuscle: "quadriceps",
      equipment: "body only",
    }),
  ];
}

test("starter routines contain only runtime catalog records", () => {
  const catalog = completeCatalog();
  const result = buildCatalogStarterRoutines([...catalog].reverse());

  assert.equal(result.ok, true);
  assert.deepEqual(result.missingSlots, []);
  assert.deepEqual(
    result.routines.map((routine) => routine.name),
    [
      "Chest / Triceps",
      "Back / Biceps",
      "Legs",
      "Shoulders / Traps",
      "Soccer / Conditioning",
    ],
  );
  assert.ok(result.routines.every((routine) => routine.exercises.length === 4));
  for (const exercise of result.routines.flatMap(
    (routine) => routine.exercises,
  )) {
    assert.ok(catalog.includes(exercise));
    assert.ok(exercise.instructions.length > 0);
  }
});

test("starter routine selection is deterministic for the same catalog", () => {
  const catalog = completeCatalog();
  const forward = buildCatalogStarterRoutines(catalog);
  const reversed = buildCatalogStarterRoutines([...catalog].reverse());

  assert.deepEqual(
    forward.routines.map((routine) =>
      routine.exercises.map((exercise) => exercise.catalogId),
    ),
    reversed.routines.map((routine) =>
      routine.exercises.map((exercise) => exercise.catalogId),
    ),
  );
});

test("an incomplete catalog produces no app-authored fallback routines", () => {
  const catalog = completeCatalog().filter(
    (exercise) => exercise.catalogId !== "test-catalog:exercise-15",
  );
  const result = buildCatalogStarterRoutines(catalog);

  assert.equal(result.ok, false);
  assert.deepEqual(result.routines, []);
  assert.deepEqual(result.missingSlots, [
    { routineName: "Shoulders / Traps", slotIndex: 2 },
  ]);
  assert.deepEqual(buildCatalogStarterRoutines([]).routines, []);
});
