import assert from "node:assert/strict";
import test from "node:test";
import { createExercisePickerSelectionSession } from "../../src/js/components/exercise-picker.js";

for (const [context, mode] of [
  ["active-workout", "add"],
  ["routine", "add"],
  ["routine", "replace"],
]) {
  test(`${context} ${mode} returns a provider-independent name-only selection`, async () => {
    const received = [];
    const session = createExercisePickerSelectionSession({
      context,
      mode,
      onSelect: (selection) => received.push(selection),
    });
    const action = session.select("  Bench Press  ");
    assert.equal(action.accepted, true);
    assert.deepEqual(action.result, { name: "Bench Press" });
    assert.deepEqual(Object.keys(action.result), ["name"]);
    await action.promise;
    assert.deepEqual(received, [{ name: "Bench Press" }]);
  });
}

test("picker cancellation does not select and a double selection settles once", async () => {
  const selected = [];
  let cancellations = 0;
  const cancelled = createExercisePickerSelectionSession({
    context: "routine",
    mode: "replace",
    onSelect: (selection) => selected.push(selection),
    onCancel: () => {
      cancellations += 1;
    },
  });
  await cancelled.cancel().promise;
  assert.equal(cancelled.select("Squat").accepted, false);
  assert.deepEqual(selected, []);
  assert.equal(cancellations, 1);

  const selectedOnce = createExercisePickerSelectionSession({
    onSelect: (selection) => selected.push(selection),
  });
  await selectedOnce.select("Squat").promise;
  assert.equal(selectedOnce.select("Deadlift").accepted, false);
  assert.deepEqual(selected, [{ name: "Squat" }]);
});

test("picker sessions reject invalid context, mode, and non-string selections", () => {
  assert.throws(
    () => createExercisePickerSelectionSession({ context: "unknown" }),
    /Unsupported exercise-picker context/u,
  );
  assert.throws(
    () => createExercisePickerSelectionSession({ mode: "unknown" }),
    /Unsupported exercise-picker mode/u,
  );
  const session = createExercisePickerSelectionSession();
  assert.equal(session.select({ name: "Squat" }).accepted, false);
  assert.equal(session.settled, false);
});
