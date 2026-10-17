import assert from "node:assert/strict";
import test from "node:test";
import { openManagedDatabase } from "../../src/js/storage/connection.js";
import { startupFailureMessage } from "../../src/js/schema/errors.js";

function setup() {
  let closes = 0;
  let aborts = 0;
  let upgrades = 0;
  const unavailable = [];
  const connection = {
    close() {
      closes++;
    },
  };
  const request = {
    result: connection,
    transaction: {
      abort() {
        aborts++;
      },
    },
  };
  const promise = openManagedDatabase({
    factory: { open: () => request },
    name: "test",
    version: 2,
    upgrade: () => upgrades++,
    onUnavailable: (db, reason) => unavailable.push([db, reason]),
  });
  return {
    request,
    promise,
    connection,
    unavailable,
    counts: () => ({ closes, aborts, upgrades }),
  };
}

test("blocked opens reject with actionable storage-preserving guidance", async () => {
  const { request, promise } = setup();
  request.onblocked();
  await assert.rejects(promise, (error) => {
    assert.equal(error.code, "database_upgrade_blocked");
    assert.match(startupFailureMessage(error), /Close other tracker tabs/);
    assert.match(startupFailureMessage(error), /Do not clear/);
    return true;
  });
});

test("an abandoned blocked request aborts its eventual upgrade", async () => {
  const { request, promise, counts } = setup();
  request.onblocked();
  await assert.rejects(promise);
  request.onupgradeneeded({});
  assert.deepEqual(counts(), { closes: 0, aborts: 1, upgrades: 0 });
});

test("a late connection from an abandoned request is immediately released", async () => {
  const { request, promise, counts } = setup();
  request.onblocked();
  await assert.rejects(promise);
  request.onsuccess();
  assert.equal(counts().closes, 1);
});

test("normal upgrade runs and a version change releases the connection", async () => {
  const { request, promise, connection, counts, unavailable } = setup();
  request.onupgradeneeded({});
  request.onsuccess();
  assert.equal(await promise, connection);
  connection.onversionchange();
  assert.deepEqual(counts(), { closes: 1, aborts: 0, upgrades: 1 });
  assert.deepEqual(unavailable, [[connection, "versionchange"]]);
});

test("unexpected closure invalidates the handle", async () => {
  const { request, promise, connection, unavailable } = setup();
  request.onsuccess();
  await promise;
  connection.onclose();
  assert.deepEqual(unavailable, [[connection, "closed"]]);
});

test("open failures preserve the browser error", async () => {
  const { request, promise } = setup();
  request.error = new Error("unavailable");
  request.onerror();
  await assert.rejects(promise, (error) => error === request.error);
});
