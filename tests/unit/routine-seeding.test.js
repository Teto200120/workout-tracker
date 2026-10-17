import assert from "node:assert/strict";
import test from "node:test";
import { planRoutineSeed } from "../../src/js/domain/routine-seeding.js";

const pending = {
  id: "starter:v1:Custom",
  name: "Custom",
  exercises: [],
  starterSeedPending: 1,
};
const defaults = [
  { id: "starter:v1:Push", name: "Push", exercises: ["Bench"] },
  { id: pending.id, name: "Custom", exercises: [] },
];

test("catalog retry fills defaults without losing a custom routine or changing its key", () => {
  const custom = { id: 0, name: "Own plan", exercises: ["Custom move"] };
  const existing = [pending, custom];
  const result = planRoutineSeed(existing, defaults, true);
  assert.deepEqual(result, [
    { id: pending.id, name: "Custom", exercises: [] },
    custom,
    defaults[0],
  ]);
  assert.equal(pending.starterSeedPending, 1);
  assert.equal(planRoutineSeed(result, defaults, true), null);
});

test("failed catalog retries do not replace existing user data", () => {
  assert.equal(planRoutineSeed([pending], [pending], false), null);
  assert.deepEqual(planRoutineSeed([], [pending], false), [pending]);
  assert.equal(planRoutineSeed([defaults[0]], defaults, true), null);
});

test("seeding preserves conflicting IDs and case-insensitive user routine names", () => {
  const own = { id: "own", name: "PUSH", exercises: ["Different"] };
  const result = planRoutineSeed([pending, own], defaults, true);
  assert.equal(result.length, 2);
  assert.deepEqual(result[1], own);
});
