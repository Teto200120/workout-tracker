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
