import assert from "node:assert/strict";
import test from "node:test";
import { runMaintenanceOperation } from "../../src/js/application/maintenance.js";
import {
  cloneDefaultSettings,
  getAppSettingsSnapshot,
  getGoals,
  getGoalsSnapshot,
  setAppSettings,
  setGoals,
} from "../../src/js/storage/local.js";

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

for (const kind of ["settings", "goals"]) {
  const read = kind === "settings" ? getAppSettingsSnapshot : getGoalsSnapshot;
  const write = kind === "settings" ? setAppSettings : setGoals;
  const value = (number) =>
    kind === "settings"
      ? { ...cloneDefaultSettings(), defaultWeightJump: number }
      : { weeklyGoal: number };

  test(`${kind} conditional writes reject a stale snapshot and return the committed raw value`, async () => {
    storage();
    const initial = read();
    const raw = await write(value(3), { expectedRaw: initial.raw });
    assert.equal(read().raw, raw);
    await assert.rejects(write(value(4), { expectedRaw: initial.raw }), {
      code: "stale_record",
    });
    assert.deepEqual(read().value, value(3));
  });

  test(`${kind} compare after waiting for maintenance and capture expected options before queuing`, async () => {
    storage();
    const initial = read();
    let acquired, release;
    const ready = new Promise((resolve) => {
      acquired = resolve;
    });
    const gate = new Promise((resolve) => {
      release = resolve;
    });
    let overwrite;
    const maintenance = runMaintenanceOperation(async (operation) => {
      acquired(operation);
      await gate;
    });
    const operation = await ready;
    const options = { expectedRaw: initial.raw };
    const pending = write(value(4), options);
    const rejected = assert.rejects(pending, { code: "stale_record" });
    overwrite = await write(value(5), { operation });
    options.expectedRaw = overwrite;
    release();
    await maintenance;
    await rejected;
    assert.deepEqual(read().value, value(5));
  });

  test(`${kind} competing writes from the same snapshot have one winner`, async () => {
    storage();
    const base = read();
    const results = await Promise.allSettled([
      write(value(4), { expectedRaw: base.raw }),
      write(value(5), { expectedRaw: base.raw }),
    ]);
    assert.deepEqual(
      results.map((item) => item.status),
      ["fulfilled", "rejected"],
    );
    assert.equal(results[1].reason.code, "stale_record");
    assert.deepEqual(read().value, value(4));
  });
}
