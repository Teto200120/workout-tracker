import assert from "node:assert/strict";
import test from "node:test";
import { createActionCoordinator } from "../../src/js/application/action-coordinator.js";

test("an action coordinator shares one in-flight promise and runs the action once", async () => {
  const coordinator = createActionCoordinator();
  let release;
  let calls = 0;
  const first = coordinator.run(async () => {
    calls += 1;
    await new Promise((resolve) => {
      release = resolve;
    });
    return "saved";
  });
  const second = coordinator.run(() => {
    calls += 1;
    return "duplicate";
  });

  assert.equal(first.started, true);
  assert.equal(second.started, false);
  assert.equal(first.promise, second.promise);
  assert.equal(coordinator.active, true);
  await Promise.resolve();
  release();
  assert.equal(await second.promise, "saved");
  assert.equal(calls, 1);
  assert.equal(coordinator.active, false);
});

test("an action coordinator releases after success and failure", async () => {
  const coordinator = createActionCoordinator();
  assert.equal(await coordinator.run(() => 1).promise, 1);
  assert.equal(coordinator.active, false);

  await assert.rejects(
    coordinator.run(() => {
      throw new Error("expected failure");
    }).promise,
    /expected failure/u,
  );
  assert.equal(coordinator.active, false);
  assert.equal(await coordinator.run(() => 2).promise, 2);
});
