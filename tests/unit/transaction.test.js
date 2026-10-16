import assert from "node:assert/strict";
import test from "node:test";
import { runStoreMutation } from "../../src/js/storage/transaction.js";

function setup(mutate = () => ({})) {
  const transaction = {
    objectStore: () => ({}),
    abort() {
      this.onabort();
    },
  };
  const database = { transaction: () => transaction };
  return {
    transaction,
    promise: runStoreMutation(database, "workouts", mutate, "saved"),
  };
}

test("mutation remains pending until commit, even after request success", async () => {
  let request;
  const { transaction, promise } = setup(() => (request = {}));
  let settled = false;
  promise.then(() => {
    settled = true;
  });
  request.onsuccess?.();
  await Promise.resolve();
  assert.equal(settled, false);
  transaction.oncomplete();
  assert.equal(await promise, "saved");
});

test("a late transaction abort rejects instead of acknowledging success", async () => {
  const { transaction, promise } = setup();
  const failure = new Error("late storage failure");
  transaction.error = failure;
  transaction.onabort();
  await assert.rejects(promise, (error) => error === failure);
});

test("synchronous mutation failures abort and preserve the original error", async () => {
  const failure = new Error("invalid key");
  const { promise } = setup(() => {
    throw failure;
  });
  await assert.rejects(promise, (error) => error === failure);
});
