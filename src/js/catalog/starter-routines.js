import {
  compareCatalogText,
  normalizeCatalogKey,
} from "./catalog-contract.js";

const STARTER_PROGRAMS = Object.freeze([
  {
    name: "Chest / Triceps",
    slots: [
      {
        category: "strength",
        primaryMuscle: "chest",
        mechanic: "compound",
        equipment: "barbell",
        preferredSecondaryMuscles: ["shoulders", "triceps"],
      },
      {
        category: "strength",
        primaryMuscle: "chest",
        mechanic: "compound",
        equipment: "dumbbell",
        preferredSecondaryMuscles: ["shoulders", "triceps"],
      },
      {
        category: "strength",
        primaryMuscle: "chest",
        mechanic: "isolation",
        equipment: "cable",
      },
      {
        category: "strength",
        primaryMuscle: "triceps",
        mechanic: "isolation",
        equipment: "cable",
      },
    ],
  },
  {
    name: "Back / Biceps",
    slots: [
      {
        category: "strength",
        primaryMuscle: "lats",
        mechanic: "compound",
        equipment: "cable",
        preferredSecondaryMuscles: ["biceps", "middle back"],
      },
      {
        category: "strength",
        primaryMuscle: "middle back",
        mechanic: "compound",
        equipment: "cable",
        preferredSecondaryMuscles: ["biceps", "lats"],
      },
      {
        category: "strength",
        primaryMuscle: "biceps",
        mechanic: "isolation",
        equipment: "dumbbell",
      },
      {
        category: "strength",
        primaryMuscle: "biceps",
        mechanic: "isolation",
        equipment: "cable",
      },
    ],
  },
  {
    name: "Legs",
    slots: [
      {
        category: "strength",
        primaryMuscle: "quadriceps",
        mechanic: "compound",
        equipment: "barbell",
        preferredSecondaryMuscles: [
          "calves",
          "glutes",
          "hamstrings",
          "lower back",
        ],
      },
      {
        category: "strength",
        primaryMuscle: "hamstrings",
        mechanic: "compound",
        equipment: "barbell",
        preferredSecondaryMuscles: ["calves", "glutes", "lower back"],
      },
      {
        category: "strength",
        primaryMuscle: "quadriceps",
        mechanic: "compound",
        equipment: "machine",
        preferredSecondaryMuscles: ["calves", "glutes", "hamstrings"],
      },
      {
        category: "strength",
        primaryMuscle: "calves",
        mechanic: "isolation",
        equipment: "machine",
      },
    ],
  },
  {
    name: "Shoulders / Traps",
    slots: [
      {
        category: "strength",
        primaryMuscle: "shoulders",
        mechanic: "compound",
        equipment: "dumbbell",
        preferredSecondaryMuscles: ["triceps"],
      },
      {
        category: "strength",
        primaryMuscle: "shoulders",
        mechanic: "isolation",
        equipment: "dumbbell",
      },
      {
        category: "strength",
        primaryMuscle: "shoulders",
        mechanic: "isolation",
        equipment: "machine",
      },
      {
        category: "strength",
        primaryMuscle: "traps",
        mechanic: "isolation",
        equipment: "barbell",
      },
    ],
  },
  {
    name: "Soccer / Conditioning",
    slots: [
      {
        category: "stretching",
        primaryMuscle: "hamstrings",
        equipment: "body only",
      },
      {
        category: "plyometrics",
        primaryMuscle: "quadriceps",
        equipment: "body only",
      },
      {
        category: "plyometrics",
        primaryMuscle: "quadriceps",
        equipment: "body only",
      },
      {
        category: "stretching",
        primaryMuscle: "quadriceps",
        equipment: "body only",
      },
    ],
  },
]);

function listIncludes(values, expected) {
  const expectedKey = normalizeCatalogKey(expected);
  return (
    Array.isArray(values) &&
    values.some((value) => normalizeCatalogKey(value) === expectedKey)
  );
}

function matchesSlot(exercise, slot) {
  return Boolean(
    exercise?.catalogId &&
      exercise?.name &&
      Array.isArray(exercise.instructions) &&
      exercise.instructions.length &&
      normalizeCatalogKey(exercise.category) ===
        normalizeCatalogKey(slot.category) &&
      listIncludes(exercise.primaryMuscles, slot.primaryMuscle) &&
      (!slot.mechanic ||
        normalizeCatalogKey(exercise.mechanic) ===
          normalizeCatalogKey(slot.mechanic)) &&
      (!slot.equipment || listIncludes(exercise.equipment, slot.equipment))
  );
}

function secondaryMuscleScore(exercise, slot) {
  const preferred = slot.preferredSecondaryMuscles || [];
  if (!preferred.length) return 0;
  const matches = preferred.filter((muscle) =>
    listIncludes(exercise.secondaryMuscles, muscle),
  ).length;
  const unrelated = (exercise.secondaryMuscles || []).filter(
    (muscle) => !listIncludes(preferred, muscle),
  ).length;
  return matches * 100 - unrelated;
}

function compareCandidates(left, right, slot) {
  return (
    secondaryMuscleScore(right, slot) -
      secondaryMuscleScore(left, slot) ||
    compareCatalogText(left.name, right.name) ||
    compareCatalogText(left.catalogId, right.catalogId)
  );
}

export function buildCatalogStarterRoutines(catalogExercises = []) {
  const exercises = Array.isArray(catalogExercises) ? catalogExercises : [];
  const routines = [];
  const missingSlots = [];

  for (const program of STARTER_PROGRAMS) {
    const usedCatalogIds = new Set();
    const selected = [];
    program.slots.forEach((slot, slotIndex) => {
      const exercise = exercises
        .filter(
          (candidate) =>
            !usedCatalogIds.has(candidate?.catalogId) &&
            matchesSlot(candidate, slot),
        )
        .sort((left, right) => compareCandidates(left, right, slot))[0];
      if (!exercise) {
        missingSlots.push({ routineName: program.name, slotIndex });
        return;
      }
      usedCatalogIds.add(exercise.catalogId);
      selected.push(exercise);
    });
    routines.push({ name: program.name, exercises: selected });
  }

  return {
    ok: missingSlots.length === 0,
    routines: missingSlots.length ? [] : routines,
    missingSlots,
  };
}
