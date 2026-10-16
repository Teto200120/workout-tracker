import assert from "node:assert/strict";
import test from "node:test";
import { decodeRecordId } from "../../src/js/domain/record-id.js";

test("DOM IDs preserve numeric zero and distinguish numeric/string keys", () => {
  const keys = new Map([
    [0, "zero"],
    [123, "number"],
    ["123", "string"],
  ]);
  assert.equal(keys.get(decodeRecordId("0", "number")), "zero");
  assert.equal(keys.get(decodeRecordId("123", "number")), "number");
  assert.equal(keys.get(decodeRecordId("123", "string")), "string");
  for (const value of ["", " ", "NaN", "Infinity"]) {
    assert.throws(() => decodeRecordId(value, "number"), TypeError);
  }
});
