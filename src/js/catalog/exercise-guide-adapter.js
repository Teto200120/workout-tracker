const APP_REMINDERS = Object.freeze([
  "Use a load you can control through a comfortable range of motion.",
  "Keep each repetition smooth and stop the set if form breaks down.",
  "Stop if the movement causes sharp pain.",
]);

function normalizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeList(value, { deduplicate = true } = {}) {
  if (!Array.isArray(value)) return [];
  const seen = new Set();
  const result = [];
  for (const entry of value) {
    const text = normalizeText(entry).replace(/\s+/g, " ");
    const key = text.toLocaleLowerCase("en-US");
    if (!text || (deduplicate && seen.has(key))) continue;
    if (deduplicate) seen.add(key);
    result.push(text);
  }
  return result;
}

export function createCatalogAttribution(exercise) {
  const sourceLabel =
    normalizeText(exercise?.attribution?.label) ||
    normalizeText(exercise?.source) ||
    "the exercise catalog";
  return {
    label: `Exercise information from ${sourceLabel}`,
    url: normalizeText(exercise?.attribution?.url),
    license: normalizeText(exercise?.license),
  };
}

export function adaptCatalogExerciseToGuide(exercise) {
  if (!exercise || typeof exercise !== "object" || Array.isArray(exercise)) {
    return null;
  }
  const name = normalizeText(exercise.name);
  const steps = normalizeList(exercise.instructions, { deduplicate: false });
  if (!name || !steps.length) return null;

  return {
    kind: "catalog",
    name,
    equipment: normalizeList(exercise.equipment),
    primaryMuscles: normalizeList(exercise.primaryMuscles),
    secondaryMuscles: normalizeList(exercise.secondaryMuscles),
    difficulty: normalizeText(exercise.difficulty),
    category: normalizeText(exercise.category),
    steps,
    reminders: [...APP_REMINDERS],
    attribution: createCatalogAttribution(exercise),
  };
}

export function buildCatalogPreviewSummary(
  exercise,
  { maximumSteps = 2 } = {},
) {
  const instructions = normalizeList(exercise?.instructions, {
    deduplicate: false,
  });
  const requestedMaximum = Number.isInteger(maximumSteps)
    ? Math.max(0, maximumSteps)
    : 2;
  const visibleCount =
    instructions.length > 1
      ? Math.min(requestedMaximum, instructions.length - 1)
      : 0;
  return {
    equipment: normalizeList(exercise?.equipment),
    primaryMuscles: normalizeList(exercise?.primaryMuscles),
    difficulty: normalizeText(exercise?.difficulty),
    category: normalizeText(exercise?.category),
    instructionPreview: instructions.slice(0, visibleCount),
    remainingInstructionCount: instructions.length - visibleCount,
    attribution: createCatalogAttribution(exercise),
  };
}
