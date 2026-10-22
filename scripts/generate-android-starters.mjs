import assert from "node:assert/strict";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { parseCatalogEnvelope } from "../src/js/catalog/catalog-contract.js";
import { buildCatalogStarterRoutines } from "../src/js/catalog/starter-routines.js";

const catalog = parseCatalogEnvelope(
  JSON.parse(
    readFileSync(
      new URL("../src/data/exercise-catalog.json", import.meta.url),
      "utf8",
    ),
  ),
  { strict: true },
);
assert.equal(catalog.ok, true, catalog.errors.join("; "));
const starter = buildCatalogStarterRoutines(catalog.exercises);
assert.equal(starter.ok, true, "The catalog cannot supply every starter slot.");
const routines = starter.routines.map((routine) => ({
  id: `starter:v1:${routine.name}`,
  name: routine.name,
  exercises: routine.exercises.map(({ catalogId, name }) => ({
    catalogId,
    name,
  })),
}));
routines.push({ id: "starter:v1:Custom", name: "Custom", exercises: [] });
const output =
  JSON.stringify(
    {
      formatVersion: 1,
      sourceRevision: catalog.metadata.sourceRevision,
      routines,
    },
    null,
    2,
  ) + "\n";
const directory = new URL("../android/app/src/main/assets/", import.meta.url);
const destination = new URL("starter-routines.json", directory);
if (process.argv.includes("--check")) {
  assert.equal(
    readFileSync(destination, "utf8"),
    output,
    "Regenerate Android starters with node scripts/generate-android-starters.mjs.",
  );
} else {
  mkdirSync(directory, { recursive: true });
  writeFileSync(destination, output);
}
console.log(
  "Android starter manifest matches the PWA catalog builder (six routines).",
);
