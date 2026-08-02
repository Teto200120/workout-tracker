import assert from "node:assert/strict";
import test from "node:test";
import {
  EDUCATION_EXPERIENCES,
  EDUCATION_SCHEMA_VERSION,
  createDefaultEducationState,
  normalizeEducationState,
  replayEducationExperience,
  resetAllEducation,
  transitionEducationExperience,
} from "../../src/js/domain/education.js";
import {
  forgetEducationState,
  getEducationState,
  resetAllGuidance,
  updateEducationExperience,
} from "../../src/js/application/education.js";

function completedHomeState() {
  const state = createDefaultEducationState();
  state.experiences.homeTour = {
    contentVersion: 1,
    status: "completed",
    lastStep: 3,
    updatedAt: "2026-07-18T12:00:00.000Z",
    completedAt: "2026-07-18T12:00:00.000Z",
  };
  return state;
}

function installStorage({
  raw = null,
  failWrites = false,
  failReads = false,
} = {}) {
  const values = new Map();
  if (raw !== null) values.set("hector_workout_education_v1", raw);
  globalThis.localStorage = {
    getItem(key) {
      if (failReads) throw new Error("read unavailable");
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      if (failWrites) throw new Error("write unavailable");
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
  };
  forgetEducationState();
  return values;
}

test("default education state includes every versioned experience", () => {
  const state = createDefaultEducationState();
  assert.equal(state.schemaVersion, EDUCATION_SCHEMA_VERSION);
  assert.deepEqual(
    Object.keys(state.experiences),
    Object.keys(EDUCATION_EXPERIENCES),
  );
  for (const experience of Object.values(state.experiences)) {
    assert.deepEqual(experience, {
      contentVersion: 1,
      status: "unseen",
      lastStep: 0,
      updatedAt: null,
      completedAt: null,
    });
  }
});

test("missing, null, and malformed top-level education data normalize safely", () => {
  for (const value of [
    undefined,
    null,
    [],
    "broken",
    4,
    { experiences: null },
  ]) {
    assert.deepEqual(
      normalizeEducationState(value),
      createDefaultEducationState(),
    );
  }
});

test("partial records preserve valid known state and repair unknown statuses", () => {
  const normalized = normalizeEducationState({
    experiences: {
      homeTour: { status: "skipped", lastStep: 2 },
      rpeBasics: { status: "not-real", lastStep: 400 },
    },
  });
  assert.equal(normalized.experiences.homeTour.status, "skipped");
  assert.equal(normalized.experiences.homeTour.lastStep, 2);
  assert.equal(normalized.experiences.rpeBasics.status, "unseen");
  assert.equal(normalized.experiences.rpeBasics.lastStep, 0);
});

test("known statuses and completed timestamps are preserved", () => {
  const source = completedHomeState();
  const normalized = normalizeEducationState(source);
  assert.deepEqual(
    normalized.experiences.homeTour,
    source.experiences.homeTour,
  );
});

test("future schema and content versions are retained without breaking known fields", () => {
  const normalized = normalizeEducationState({
    schemaVersion: 7,
    experiences: {
      homeTour: {
        contentVersion: 4,
        status: "completed",
        lastStep: 8,
        updatedAt: "2026-07-18T12:00:00.000Z",
        completedAt: "2026-07-18T12:00:00.000Z",
      },
    },
  });
  assert.equal(normalized.schemaVersion, 7);
  assert.equal(normalized.experiences.homeTour.contentVersion, 4);
  assert.equal(normalized.experiences.homeTour.lastStep, 8);
  assert.equal(normalized.experiences.homeTour.status, "completed");
});

test("same-content wording changes do not reset completed experiences", () => {
  const normalized = normalizeEducationState(completedHomeState());
  assert.equal(normalized.experiences.homeTour.contentVersion, 1);
  assert.equal(normalized.experiences.homeTour.status, "completed");
});

test("state transitions clamp known step boundaries and timestamp completion", () => {
  const now = "2026-07-18T15:30:00.000Z";
  const inProgress = transitionEducationExperience(
    createDefaultEducationState(),
    "homeTour",
    "in_progress",
    { lastStep: 99, now },
  );
  assert.equal(inProgress.experiences.homeTour.lastStep, 3);
  assert.equal(inProgress.experiences.homeTour.updatedAt, now);
  assert.equal(inProgress.experiences.homeTour.completedAt, null);

  const completed = transitionEducationExperience(
    inProgress,
    "homeTour",
    "completed",
    { lastStep: 3, now },
  );
  assert.equal(completed.experiences.homeTour.completedAt, now);
});

test("skip, dismiss, and defer remain distinct terminal outcomes", () => {
  for (const status of ["skipped", "dismissed", "deferred"]) {
    const state = transitionEducationExperience(
      createDefaultEducationState(),
      "activeWorkoutBasics",
      status,
      { lastStep: 1, now: "2026-07-18T16:00:00.000Z" },
    );
    assert.equal(state.experiences.activeWorkoutBasics.status, status);
    assert.equal(state.experiences.activeWorkoutBasics.completedAt, null);
  }
});

test("replay resets only the selected experience", () => {
  const source = transitionEducationExperience(
    completedHomeState(),
    "statsBasics",
    "skipped",
    { now: "2026-07-18T16:00:00.000Z" },
  );
  const replayed = replayEducationExperience(source, "homeTour");
  assert.equal(replayed.experiences.homeTour.status, "unseen");
  assert.equal(replayed.experiences.homeTour.lastStep, 0);
  assert.equal(replayed.experiences.statsBasics.status, "skipped");
});

test("reset all guidance leaves unknown future experiences intact", () => {
  const source = completedHomeState();
  source.experiences.futureGuide = {
    contentVersion: 2,
    status: "completed",
    custom: [1, 2, 3],
  };
  const reset = resetAllEducation(source);
  assert.equal(reset.experiences.homeTour.status, "unseen");
  assert.deepEqual(
    reset.experiences.futureGuide,
    source.experiences.futureGuide,
  );
});

test("normalization and transitions never mutate caller-owned objects", () => {
  const source = completedHomeState();
  source.experiences.futureGuide = { nested: { value: 1 } };
  const before = structuredClone(source);
  const normalized = normalizeEducationState(source);
  const transitioned = transitionEducationExperience(
    source,
    "homeTour",
    "dismissed",
    { now: "2026-07-18T16:00:00.000Z" },
  );
  assert.deepEqual(source, before);
  normalized.experiences.futureGuide.nested.value = 2;
  assert.equal(source.experiences.futureGuide.nested.value, 1);
  assert.notEqual(transitioned, source);
});

test("missing and null education storage create safe lazy defaults", () => {
  installStorage();
  assert.deepEqual(getEducationState(), createDefaultEducationState());
  installStorage({ raw: "null" });
  assert.deepEqual(getEducationState(), createDefaultEducationState());
});

test("malformed education JSON and read failures use in-memory defaults", () => {
  installStorage({ raw: "{not json" });
  assert.deepEqual(getEducationState(), createDefaultEducationState());
  installStorage({ failReads: true });
  assert.deepEqual(getEducationState(), createDefaultEducationState());
});

test("education write failure preserves the in-memory transition", () => {
  installStorage({ failWrites: true });
  const result = updateEducationExperience("homeTour", "skipped");
  assert.equal(result.saved, false);
  assert.equal(getEducationState().experiences.homeTour.status, "skipped");
});

test("application reset persists every known experience as unseen", () => {
  const values = installStorage({ raw: JSON.stringify(completedHomeState()) });
  const result = resetAllGuidance();
  assert.equal(result.saved, true);
  const persisted = JSON.parse(values.get("hector_workout_education_v1"));
  assert.equal(persisted.experiences.homeTour.status, "unseen");
  assert.equal(persisted.experiences.statsBasics.status, "unseen");
});
