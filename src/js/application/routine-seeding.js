import { runMaintenanceOperation } from "./maintenance.js";
import { loadCatalog } from "../catalog/catalog-loader.js";
import { buildCatalogStarterRoutines } from "../catalog/starter-routines.js";
import { isPendingStarterRoutine } from "../domain/routine-seeding.js";
import { getRoutines, commitRoutineLibrary } from "../storage/indexed-db.js";

export async function prepareDefaultTemplates({ requireCatalog = false } = {}) {
  const catalog = await loadCatalog();
  const starter = buildCatalogStarterRoutines(catalog.exercises);
  if (requireCatalog && !starter.ok) {
    throw new Error(
      "Default routines are unavailable. Existing routines were left unchanged.",
    );
  }
  const timestamp = new Date().toISOString();
  const templates = starter.ok
    ? [
        ...starter.routines.map((routine) => ({
          name: routine.name,
          exercises: routine.exercises.map((exercise) => exercise.name),
        })),
        { name: "Custom", exercises: [] },
      ]
    : [{ name: "Custom", exercises: [], starterSeedPending: 1 }];
  return {
    ready: starter.ok,
    routines: templates.map((template) => ({
      ...template,
      id: `starter:v1:${template.name}`,
      createdAt: timestamp,
      updatedAt: timestamp,
    })),
    missingSlots: starter.missingSlots,
  };
}

export async function seedDefaultTemplates({ operation } = {}) {
  const existing = await getRoutines();
  if (existing.length && !existing.some(isPendingStarterRoutine)) {
    return { status: "existing", routines: existing };
  }
  const prepared = await prepareDefaultTemplates();
  await commitRoutineLibrary(prepared.routines, {
    seed: true,
    ready: prepared.ready,
    operation,
  });
  return {
    status: prepared.ready ? "catalog" : "catalog-unavailable",
    routines: await getRoutines(),
    missingSlots: prepared.missingSlots,
  };
}

export function resetDefaultTemplates() {
  return runMaintenanceOperation(async (operation) => {
    const prepared = await prepareDefaultTemplates({ requireCatalog: true });
    await commitRoutineLibrary(prepared.routines, { operation });
  });
}
