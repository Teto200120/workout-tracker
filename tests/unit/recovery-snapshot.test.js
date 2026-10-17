import assert from "node:assert/strict";
import test from "node:test";
import { validateRecoverySnapshot } from "../../src/js/application/recovery.js";

function snapshot() {
  return {
    id: "application",
    version: 1,
    records: { workouts: [], legacyWeights: [], routines: [] },
    localSnapshot: {
      settings: "{invalid JSON",
      goals: null,
      draft: null,
      backupMeta: null,
      applicationSchemaVersion: "0",
    },
    education: null,
  };
}

test("recovery retains exact original strings without validating or normalizing legacy data", () => {
  const value = snapshot();
  value.records.workouts.push({
    id: "legacy",
    futureField: { preserved: true },
  });
  const before = structuredClone(value);
  assert.equal(validateRecoverySnapshot(value), value);
  assert.deepEqual(value, before);
});

test("incomplete or future recovery records fail closed instead of becoming empty replacements", () => {
  for (const invalid of [
    null,
    {},
    { ...snapshot(), version: 2 },
    { ...snapshot(), records: {} },
    { ...snapshot(), localSnapshot: {} },
    { ...snapshot(), education: {} },
  ]) {
    assert.throws(
      () => validateRecoverySnapshot(invalid),
      /unsupported or incomplete/,
    );
  }
});
