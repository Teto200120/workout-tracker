import assert from "node:assert/strict";
import test from "node:test";
import { runMaintenanceOperation } from "../../src/js/application/maintenance.js";
import { getGoals, setGoals } from "../../src/js/storage/local.js";

function storage() {
  const values = new Map();
  globalThis.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
  return values;
}

test("queued local writes capture submitted values and await maintenance release", async () => {
  const values = storage();
  let acquired;
  let release;
  const ready = new Promise((resolve) => {
    acquired = resolve;
  });
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const maintenance = runMaintenanceOperation(async () => {
    acquired();
    await gate;
  });
  await ready;
  const goals = { weeklyGoal: 6 };
  const saved = setGoals(goals);
  goals.weeklyGoal = 99;
  assert.equal(values.size, 0);
  release();
  await maintenance;
  await saved;
  assert.equal(getGoals().weeklyGoal, 6);
});

test("a failed local write rejects and does not block the next save", async () => {
  storage();
  const original = globalThis.localStorage.setItem;
  globalThis.localStorage.setItem = () => {
    throw new Error("quota exhausted");
  };
  await assert.rejects(setGoals({ weeklyGoal: 4 }), /quota exhausted/);
  globalThis.localStorage.setItem = original;
  await setGoals({ weeklyGoal: 5 });
  assert.equal(getGoals().weeklyGoal, 5);
});
