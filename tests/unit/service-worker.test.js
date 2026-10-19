import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";

function worker({
  failInstall = false,
  cached = new Response("installed generation"),
} = {}) {
  const handlers = {};
  const deleted = [];
  const added = [];
  const matches = [];
  let claimed = false;
  const source = readFileSync(
    new URL("../../service-worker.js", import.meta.url),
    "utf8",
  );
  const name = source.match(/const CACHE_NAME = "([^"]+)"/u)[1];
  runInNewContext(source, {
    URL,
    Request,
    Response,
    self: {
      registration: { scope: "https://example.test/tracker/" },
      location: { origin: "https://example.test" },
      addEventListener: (type, handler) => {
        handlers[type] = handler;
      },
      clients: {
        claim: async () => {
          claimed = true;
        },
      },
      skipWaiting: () => assert.fail("must not replace active workout clients"),
    },
    caches: {
      open: async () => ({
        addAll: async (requests) => {
          added.push(...requests);
          if (failInstall) throw new Error("asset integrity failure");
        },
        match: async (key) => {
          matches.push(key);
          return cached;
        },
      }),
      keys: async () => [name, "hector-workout-tracker-pwa-old", "another-app"],
      delete: async (key) => {
        deleted.push(key);
      },
    },
    fetch: () =>
      assert.fail("installed assets must not mix in network versions"),
  });
  const event = async (type) => {
    let pending;
    handlers[type]({
      waitUntil: (value) => {
        pending = value;
      },
    });
    await pending;
  };
  const request = async (url, mode = "cors", method = "GET") => {
    let pending;
    handlers.fetch({
      request: { url, mode, method },
      respondWith: (value) => {
        pending = value;
      },
    });
    return pending ? await pending : null;
  };
  return {
    event,
    request,
    name,
    deleted,
    added,
    matches,
    claimed: () => claimed,
  };
}

test("installation checks every asset digest; activation preserves other apps' caches", async () => {
  const sw = worker();
  await sw.event("install");
  assert.ok(sw.added.length > 60);
  assert.ok(
    sw.added.every(
      (request) =>
        request.integrity.startsWith("sha256-") && request.cache === "reload",
    ),
  );
  await sw.event("activate");
  assert.deepEqual(sw.deleted, ["hector-workout-tracker-pwa-old"]);
  assert.equal(sw.claimed(), true);
});
test("a failed installation discards only its incomplete generation", async () => {
  const sw = worker({ failInstall: true });
  await assert.rejects(sw.event("install"), /integrity/u);
  assert.deepEqual(sw.deleted, [sw.name]);
});
test("navigation and modules stay on the installed generation; external and unknown requests bypass it", async () => {
  const sw = worker();
  assert.equal(
    await (
      await sw.request("https://example.test/tracker/", "navigate")
    ).text(),
    "installed generation",
  );
  await sw.request("https://example.test/tracker/src/js/main.js");
  assert.deepEqual(sw.matches, [
    "https://example.test/tracker/index.html",
    "https://example.test/tracker/src/js/main.js",
  ]);
  assert.equal(await sw.request("https://external.test/token"), null);
  assert.equal(
    await sw.request("https://example.test/tracker/not-in-shell.json"),
    null,
  );
  assert.equal(
    await sw.request("https://example.test/tracker/", "cors", "POST"),
    null,
  );
});
test("a damaged installed shell fails visibly rather than mixing generations", async () => {
  const sw = worker({ cached: null });
  assert.equal(
    (await sw.request("https://example.test/tracker/src/js/main.js")).status,
    503,
  );
});
