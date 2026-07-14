import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { gzipSync } from "node:zlib";
import { parseCatalogEnvelope } from "../src/js/catalog/catalog-contract.js";

const catalogPath = resolve("src/data/exercise-catalog.json");

async function main() {
  const source = await readFile(catalogPath, "utf8");
  const parsed = parseCatalogEnvelope(JSON.parse(source), { strict: true });
  if (!parsed.ok) throw new Error(parsed.errors.join(" "));
  console.log(`Catalog exercises: ${parsed.exercises.length}`);
  console.log(`Catalog bytes: ${Buffer.byteLength(source)}`);
  console.log(`Approximate gzip bytes: ${gzipSync(source).byteLength}`);
  console.log(`Source revision: ${parsed.metadata.sourceRevision}`);
  console.log("Catalog validation passed.");
}

main().catch((error) => {
  console.error(`Catalog validation failed: ${error.message}`);
  process.exitCode = 1;
});
