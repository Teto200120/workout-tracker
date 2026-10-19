import { createHash } from "node:crypto";
import { readFile, writeFile, readdir } from "node:fs/promises";
import { resolve } from "node:path";
import { format } from "prettier";

const check = process.argv.includes("--check");
const root = new URL("../", import.meta.url);
const read = (name) => readFile(new URL(name, root), "utf8");
const { version } = JSON.parse(await read("package.json"));
const lock = JSON.parse(await read("package-lock.json"));
if (lock.version !== version || lock.packages[""].version !== version)
  throw new Error("Package release versions differ.");
if (
  !(await read("src/js/core/release.js")).includes(`APP_RELEASE = "${version}"`)
)
  throw new Error("UI release differs from package version.");
let source = await read("service-worker.js");
const paths = [
  ...source
    .match(/const APP_SHELL = \[([\s\S]*?)\];/u)[1]
    .matchAll(/"([^"]+)"/gu),
].map((match) => match[1]);
const shell = new Set(paths);
async function walk(name) {
  for (const entry of await readdir(new URL(name, root), {
    withFileTypes: true,
  })) {
    const path = `${name}${entry.name}`;
    if (entry.isDirectory()) await walk(`${path}/`);
    else if (/\.(js|css)$/u.test(path) && !shell.has(`./${path}`))
      throw new Error(`Production asset missing from shell: ${path}`);
  }
}
await walk("src/js/");
await walk("src/styles/");
const integrity = {};
for (const path of paths) {
  const file = path === "./" ? "index.html" : path;
  const bytes = await readFile(new URL(file, root));
  integrity[path] =
    `sha256-${createHash("sha256").update(bytes).digest("base64")}`;
}
// Include worker policy as well as assets: even a policy-only update must not
// install into (or delete on failure) the active generation's cache.
const policy = await format(
  source
    .replace(/const CACHE_NAME = "[^"]+";/u, 'const CACHE_NAME = "";')
    .replace(/const SHELL_INTEGRITY = \{[\s\S]*?\};\n*/u, ""),
  { filepath: resolve("service-worker.js") },
);
const generation = createHash("sha256")
  .update(policy)
  .update(JSON.stringify(integrity))
  .digest("hex")
  .slice(0, 16);
source = source.replace(
  /const CACHE_NAME = "[^"]+";/u,
  `const CACHE_NAME = "hector-workout-tracker-pwa-${version}-${generation}";`,
);
source = source.replace(/const SHELL_INTEGRITY = \{[\s\S]*?\};\n*/u, "");
source = source.replace(
  "const CACHE_PREFIX =",
  `const SHELL_INTEGRITY = ${JSON.stringify(integrity, null, 2)};\n\nconst CACHE_PREFIX =`,
);
source = await format(source, { filepath: resolve("service-worker.js") });
if (check) {
  if (source !== (await read("service-worker.js")))
    throw new Error(
      "Shell integrity is stale. Run npm run release:prepare after formatting source.",
    );
  console.log(
    `Release ${version}: versions, shell coverage and asset integrity verified.`,
  );
} else {
  await writeFile(new URL("service-worker.js", root), source);
  console.log(`Prepared immutable shell for release ${version}.`);
}
