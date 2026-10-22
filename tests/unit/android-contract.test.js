import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  workoutVolume,
  totalSets,
  completedSets,
  workoutDurationMinutes,
} from "../../src/js/domain/workout-metrics.js";
import { validateImportedApplicationData } from "../../src/js/domain/input-guardrails.js";
import { prepareBackupImport } from "../../src/js/schema/migrations.js";
import { buildCatalogStarterRoutines } from "../../src/js/catalog/starter-routines.js";
import { resolveWorkoutDay } from "../../src/js/domain/schedule.js";
import {
  DEFAULT_APP_SETTINGS,
  DEFAULT_SCHEDULE,
  DAY_LABELS,
} from "../../src/js/core/constants.js";

test("Native local-date schedule fixtures match the actual PWA resolver", () => {
  const fixture = JSON.parse(
    readFileSync(
      new URL("../fixtures/android/schedule.json", import.meta.url),
      "utf8",
    ),
  );
  for (const item of fixture.cases)
    assert.deepEqual(
      resolveWorkoutDay(
        item.date,
        item.settings,
        DEFAULT_SCHEDULE,
        DEFAULT_APP_SETTINGS,
        DAY_LABELS,
      ),
      item.expected,
      item.name,
    );
});

const fixture = JSON.parse(
  readFileSync(
    new URL("../fixtures/android/baseline.json", import.meta.url),
    "utf8",
  ),
);
test("Android golden fixture matches actual PWA calculations and retains its data contract", () => {
  const prepared = prepareBackupImport(fixture.backup);
  assert.deepEqual(prepared.data.workouts, fixture.backup.workouts);
  fixture.backup.workouts.forEach((workout, index) => {
    const expected = fixture.expected[index];
    assert.equal(workoutVolume(workout), expected.volume);
    assert.equal(totalSets(workout), expected.workingSets);
    assert.equal(completedSets(workout), expected.completedSets);
    assert.equal(workoutDurationMinutes(workout), expected.duration);
  });
  assert.notEqual(
    typeof prepared.data.workouts[0].id,
    typeof prepared.data.workouts[1].id,
  );
});

const migrations = JSON.parse(
  readFileSync(
    new URL("../fixtures/android/backup-migration.json", import.meta.url),
    "utf8",
  ),
);
test("Android migration fixtures are generated from the PWA migrations without data loss", () => {
  for (const fixture of migrations.cases) {
    assert.equal(
      validateImportedApplicationData(prepareBackupImport(fixture.backup).data)
        .valid,
      true,
    );
    assert.deepEqual(
      prepareBackupImport(fixture.backup).data,
      fixture.expected,
      fixture.name,
    );
  }
});

const catalogContract = JSON.parse(
  readFileSync(
    new URL("../fixtures/android/catalog.json", import.meta.url),
    "utf8",
  ),
);
const catalog = JSON.parse(
  readFileSync(
    new URL("../../src/data/exercise-catalog.json", import.meta.url),
    "utf8",
  ),
);
test("Android starter manifest matches the real PWA builder and stable seed IDs", () => {
  const manifest = JSON.parse(
    readFileSync(
      new URL(
        "../../android/app/src/main/assets/starter-routines.json",
        import.meta.url,
      ),
      "utf8",
    ),
  );
  const starter = buildCatalogStarterRoutines(catalog.exercises);
  assert.equal(starter.ok, true);
  assert.equal(manifest.formatVersion, 1);
  assert.equal(manifest.sourceRevision, catalog.metadata.sourceRevision);
  assert.deepEqual(manifest.routines, [
    ...starter.routines.map(({ name, exercises }) => ({
      id: `starter:v1:${name}`,
      name,
      exercises: exercises.map(({ catalogId, name }) => ({ catalogId, name })),
    })),
    { id: "starter:v1:Custom", name: "Custom", exercises: [] },
  ]);
});
const { mergeLocalAndCatalogResults, resolveCatalogCanonicalName } =
  await import("../../src/js/catalog/catalog-search.js");
const { createExerciseCatalogResolver } =
  await import("../../src/js/catalog/exercise-catalog-resolver.js");
const { EXERCISE_CATALOG_ALIASES } =
  await import("../../src/js/catalog/exercise-aliases.js");
test("Native catalog fixtures match PWA search, local precedence and reviewed guides", () => {
  assert.equal(catalog.metadata.sourceRevision, catalogContract.revision);
  assert.equal(catalog.exercises.length, catalogContract.count);
  assert.deepEqual(EXERCISE_CATALOG_ALIASES, catalogContract.aliases);
  for (const item of catalogContract.searches) {
    const result = mergeLocalAndCatalogResults({
      localNames: catalogContract.local,
      catalogExercises: catalog.exercises,
      query: item.query,
      filters: item.filters,
    });
    assert.deepEqual(result.local, item.local);
    assert.deepEqual(
      result.catalog.map((x) => x.catalogId),
      item.catalog,
    );
  }
  const resolver = createExerciseCatalogResolver(catalog.exercises);
  for (const item of catalogContract.guides)
    assert.equal(
      resolver.resolve(item.name).exercise?.catalogId ?? null,
      item.id,
    );
  for (const item of catalogContract.canonical)
    assert.equal(
      resolveCatalogCanonicalName(
        item.value,
        catalogContract.local,
        catalog.exercises,
      ),
      item.name,
    );
  const backup = JSON.parse(
    readFileSync(
      new URL("../fixtures/android/training-backup.json", import.meta.url),
      "utf8",
    ),
  );
  const prepared = prepareBackupImport(backup);
  assert.deepEqual(prepared.data.routines, backup.templates);
  assert.equal(validateImportedApplicationData(prepared.data).valid, true);
});
