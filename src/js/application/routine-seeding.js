import { loadCatalog } from "../catalog/catalog-loader.js";
import { buildCatalogStarterRoutines } from "../catalog/starter-routines.js";
import { id } from "../core/utils.js";
import {
  getRoutines,
  saveRoutine,
} from "../storage/indexed-db.js";

const CUSTOM_ROUTINE_NAME = "Custom";

export async function seedDefaultTemplates() {
  const existing = await getRoutines();
  if (existing.length) return { status: "existing", routines: existing };

  const catalog = await loadCatalog();
  const starterRoutines = buildCatalogStarterRoutines(catalog.exercises);
  const templates = starterRoutines.ok
    ? [
        ...starterRoutines.routines.map((routine) => ({
          name: routine.name,
          exercises: routine.exercises.map((exercise) => exercise.name),
        })),
        { name: CUSTOM_ROUTINE_NAME, exercises: [] },
      ]
    : [{ name: CUSTOM_ROUTINE_NAME, exercises: [] }];
  const timestamp = new Date().toISOString();

  for (const template of templates) {
    await saveRoutine({
      id: id(),
      name: template.name,
      exercises: template.exercises,
      createdAt: timestamp,
      updatedAt: timestamp,
    });
  }

  return {
    status: starterRoutines.ok ? "catalog" : "catalog-unavailable",
    routines: await getRoutines(),
    missingSlots: starterRoutines.missingSlots,
  };
}
