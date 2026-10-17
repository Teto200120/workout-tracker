import assert from "node:assert/strict";
import test from "node:test";
import { createMaintenanceCoordinator } from "../../src/js/application/maintenance.js";

test("distinct maintenance requests wait in FIFO order and continue after failure", async () => {
  const run = createMaintenanceCoordinator(null);
  const calls = [];
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const first = run(async () => {
    calls.push("import");
    await gate;
    throw new Error("failure");
  });
  const rejected = assert.rejects(first, /failure/);
  const second = run(() => {
    calls.push("export");
    return "own result";
  });
  await Promise.resolve();
  assert.deepEqual(calls, ["import"]);
  release();
  await rejected;
  assert.equal(await second, "own result");
  assert.deepEqual(calls, ["import", "export"]);
});

test("maintenance uses the same browser lock across operations", async () => {
  const names = [];
  const run = createMaintenanceCoordinator({
    request: async (name, action) => {
      names.push(name);
      return action();
    },
  });
  await run(() => 1);
  await run(() => 2);
  assert.deepEqual(names, [
    "hector-workout-maintenance-v1",
    "hector-workout-maintenance-v1",
  ]);
});

test("nested writes use an explicit active token while unrelated writes remain queued", async () => {
  const run = createMaintenanceCoordinator(null);
  const calls = [];
  let release;
  let acquired;
  const started = new Promise((resolve) => {
    acquired = resolve;
  });
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  let expired;
  const maintenance = run(async (operation) => {
    expired = operation;
    acquired();
    await run(() => calls.push("nested commit"), operation);
    await gate;
    await run(() => calls.push("rollback"), operation);
  });
  await started;
  const writer = run(() => calls.push("ordinary save"));
  await Promise.resolve();
  assert.deepEqual(calls, ["nested commit"]);
  release();
  await Promise.all([maintenance, writer]);
  assert.deepEqual(calls, ["nested commit", "rollback", "ordinary save"]);
  await assert.rejects(
    run(() => calls.push("stale"), expired),
    /no longer active/,
  );
  await assert.rejects(
    run(() => calls.push("forged"), {}),
    /no longer active/,
  );
});

test("failed maintenance releases queued ordinary writers", async () => {
  const run = createMaintenanceCoordinator(null);
  const failure = run(async (operation) => {
    await run(() => "committed", operation);
    throw new Error("compensation finished");
  });
  const writer = run(() => "saved afterwards");
  await assert.rejects(failure, /compensation finished/);
  assert.equal(await writer, "saved afterwards");
});
