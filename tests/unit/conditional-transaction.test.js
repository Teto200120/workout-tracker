import assert from "node:assert/strict";
import test from "node:test";
import {
  runConditionalStoreMutation,
  sameRecordSnapshot,
} from "../../src/js/storage/transaction.js";

test("snapshot comparison retains types and nested content without depending on key order", () => {
  assert.equal(
    sameRecordSnapshot(
      { id: 0, extra: { a: 1, b: ["x"] } },
      { extra: { b: ["x"], a: 1 }, id: 0 },
    ),
    true,
  );
  for (const [left, right] of [
    [0, "0"],
    [null, {}],
    [[], {}],
    [[], new Array(1)],
    [{ a: 1 }, { a: 2 }],
    [{ a: 1 }, { a: 1, b: 2 }],
    [
      [1, 2],
      [2, 1],
    ],
  ]) {
    assert.equal(sameRecordSnapshot(left, right), false);
  }
});

function setup(current, expected, mutate = () => ({})) {
  const read = { result: current };
  const transaction = {
    objectStore: () => ({ get: () => read }),
    abort() {
      this.onabort();
    },
  };
  const promise = runConditionalStoreMutation(
    { transaction: () => transaction },
    "templates",
    0,
    expected,
    mutate,
    "saved",
  );
  return { read, transaction, promise };
}

test("a matching snapshot only acknowledges success after commit", async () => {
  let writes = 0;
  const { read, transaction, promise } = setup({ id: 0 }, { id: 0 }, () => {
    writes++;
    return {};
  });
  read.onsuccess();
  let settled = false;
  promise.then(() => {
    settled = true;
  });
  await Promise.resolve();
  assert.equal(writes, 1);
  assert.equal(settled, false);
  transaction.oncomplete();
  assert.equal(await promise, "saved");
});

test("a changed or deleted record aborts without writing", async () => {
  for (const current of [{ id: 0, name: "newer" }, undefined]) {
    let writes = 0;
    const { read, promise } = setup(current, { id: 0, name: "older" }, () => {
      writes++;
      return {};
    });
    read.onsuccess();
    await assert.rejects(promise, { code: "stale_record" });
    assert.equal(writes, 0);
  }
});

test("expecting absence prevents colliding creates", async () => {
  const absent = setup(undefined, null);
  absent.read.onsuccess();
  absent.transaction.oncomplete();
  assert.equal(await absent.promise, "saved");
  const present = setup({ id: 0 }, null);
  present.read.onsuccess();
  await assert.rejects(present.promise, { code: "stale_record" });
});

test("a late conditional transaction abort never reports success", async () => {
  const { read, transaction, promise } = setup({ id: 0 }, { id: 0 });
  read.onsuccess();
  transaction.error = new Error("late abort");
  transaction.onabort();
  await assert.rejects(promise, /late abort/);
});
