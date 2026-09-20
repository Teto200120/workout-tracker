import { mkdir, rename, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";
import {
  CATALOG_FORMAT_VERSION,
  parseCatalogEnvelope,
} from "../src/js/catalog/catalog-contract.js";
import { adaptFreeExerciseDbRecords } from "../src/js/catalog/free-exercise-db-adapter.js";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const outputPath = resolve(root, "src/data/exercise-catalog.json");
const repositoryUrl = "https://github.com/yuhonas/free-exercise-db";
const commitApiUrl =
  "https://api.github.com/repos/yuhonas/free-exercise-db/commits/main";
const userAgent = "workout-tracker-exercise-catalog-refresh";

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: { Accept: "application/json", "User-Agent": userAgent },
  });
  if (!response.ok) throw new Error(`${url} returned HTTP ${response.status}`);
  return response.json();
}

async function main() {
  const requestedRevision = String(
    process.env.EXERCISE_CATALOG_SOURCE_REVISION || "",
  ).trim();
  const commit = await fetchJson(
    requestedRevision
      ? `https://api.github.com/repos/yuhonas/free-exercise-db/commits/${requestedRevision}`
      : commitApiUrl,
  );
  const revision = String(commit?.sha || "").trim();
  const sourceCommitDate = String(commit?.commit?.committer?.date || "").trim();
  if (!revision || !sourceCommitDate) {
    throw new Error("GitHub did not return a usable source revision.");
  }

  const sourceDataUrl = `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${revision}/dist/exercises.json`;
  const sourceSchemaUrl = `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${revision}/schema.json`;
  const providerRecords = await fetchJson(sourceDataUrl);
  const adapted = adaptFreeExerciseDbRecords(providerRecords, {
    label: "Free Exercise DB",
    url: repositoryUrl,
    license: "Unlicense",
    imageBaseUrl: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${revision}/exercises`,
  });
  if (!adapted.exercises.length)
    throw new Error("No usable provider records were found.");

  const envelope = {
    metadata: {
      catalogFormatVersion: CATALOG_FORMAT_VERSION,
      normalizerVersion: 1,
      source: "free-exercise-db",
      sourceUrl: repositoryUrl,
      sourceDataUrl,
      sourceSchemaUrl,
      sourceRevision: revision,
      sourceCommitDate,
      generatedAt: sourceCommitDate,
      license: "Unlicense",
      attribution: "Free Exercise DB by yuhonas and contributors",
      exerciseCount: adapted.exercises.length,
      imagesIncluded: true,
      imageDecision:
        "Source-provided remote image paths included at user direction; upstream photograph reuse rights are unverified.",
    },
    exercises: adapted.exercises,
  };

  const validation = parseCatalogEnvelope(envelope, { strict: true });
  if (!validation.ok) throw new Error(validation.errors.join(" "));

  const output = `${JSON.stringify(envelope, null, 2)}\n`;
  const temporaryPath = `${outputPath}.tmp-${process.pid}`;
  await mkdir(dirname(outputPath), { recursive: true });
  try {
    await writeFile(temporaryPath, output, "utf8");
    await rename(temporaryPath, outputPath);
  } finally {
    await rm(temporaryPath, { force: true });
  }

  console.log(`Source revision: ${revision}`);
  console.log(`Provider records: ${providerRecords.length}`);
  console.log(`Catalog exercises: ${adapted.exercises.length}`);
  console.log(`Skipped malformed records: ${adapted.malformedRecords.length}`);
  console.log(`Skipped duplicate IDs: ${adapted.duplicateIds.length}`);
  console.log(`Skipped duplicate names: ${adapted.duplicateNames.length}`);
  console.log(`Output bytes: ${Buffer.byteLength(output)}`);
  console.log(`Approximate gzip bytes: ${gzipSync(output).byteLength}`);
}

main().catch((error) => {
  console.error(`Catalog refresh failed: ${error.message}`);
  process.exitCode = 1;
});
