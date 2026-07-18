import assert from "node:assert/strict";
import test from "node:test";
import {
  completeOnboarding,
  getDisplayName,
  isOnboardingRequired,
  saveDisplayName,
} from "../../src/js/application/display-name.js";
import { DEFAULT_APP_SETTINGS } from "../../src/js/core/constants.js";
import {
  INPUT_LIMITS,
  validateDisplayName,
} from "../../src/js/domain/input-guardrails.js";

function settings(displayName) {
  return structuredClone({ ...DEFAULT_APP_SETTINGS, displayName });
}

function installLocalStorage(
  initialSettings,
  { failKey = null, failWrites = false, initialMarker } = {},
) {
  const values = new Map();
  if (initialSettings !== undefined) {
    values.set("hector_workout_settings_v1", JSON.stringify(initialSettings));
  }
  if (initialMarker !== undefined) {
    values.set("hector_workout_data_schema_version", String(initialMarker));
  }
  const storage = {
    getItem(key) {
      return values.has(key) ? values.get(key) : null;
    },
    setItem(key, value) {
      if (failWrites || key === failKey) throw new Error("storage unavailable");
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
  };
  globalThis.localStorage = storage;
  return values;
}

test("display-name validation trims boundaries and preserves international names and punctuation", () => {
  const accepted = [
    ["Hector", "Hector"],
    [" Mar\u00eda ", "Mar\u00eda"],
    ["\u674e\u96f7", "\u674e\u96f7"],
    ["\u0645\u062d\u0645\u062f", "\u0645\u062d\u0645\u062f"],
    ["O'Connor", "O'Connor"],
    ["Anne-Marie", "Anne-Marie"],
    ["Alex \ud83d\udcaa", "Alex \ud83d\udcaa"],
    ["<b>Alex</b>", "<b>Alex</b>"],
    ["<script>alert(1)</script>", "<script>alert(1)</script>"],
  ];
  for (const [value, normalized] of accepted) {
    const result = validateDisplayName(value);
    assert.equal(result.valid, true, value);
    assert.equal(result.normalized, normalized);
  }
});

test("display-name validation rejects blank, invisible-only, controls, and one code point above the boundary", () => {
  for (const value of ["", "   ", "\u200b\u200d", "bad\u0000name"]) {
    assert.equal(
      validateDisplayName(value).valid,
      false,
      JSON.stringify(value),
    );
  }
  assert.equal(
    validateDisplayName("x".repeat(INPUT_LIMITS.displayNameLength)).valid,
    true,
  );
  assert.equal(
    validateDisplayName("x".repeat(INPUT_LIMITS.displayNameLength + 1)).valid,
    false,
  );
  assert.equal(
    validateDisplayName("\ud83d\udcaa".repeat(INPUT_LIMITS.displayNameLength))
      .valid,
    true,
  );
});

test("display-name validation and onboarding-state checks do not mutate settings", () => {
  const value = settings("  Mar\u00eda  ");
  const before = structuredClone(value);
  assert.equal(getDisplayName(value), "Mar\u00eda");
  assert.equal(isOnboardingRequired(value), false);
  assert.deepEqual(value, before);
  assert.equal(isOnboardingRequired({}), true);
  assert.equal(isOnboardingRequired(settings(null)), true);
  assert.equal(isOnboardingRequired(settings("   ")), true);
  assert.equal(isOnboardingRequired(settings("\u200b")), true);
});

test("a successful display-name save preserves all other settings", async () => {
  const original = settings(null);
  original.animations = false;
  const values = installLocalStorage(original);
  const operation = saveDisplayName("  Mar\u00eda  ");
  assert.equal(operation.started, true);
  const result = await operation.promise;
  assert.equal(result.saved, true);
  assert.equal(result.displayName, "Mar\u00eda");
  const persisted = JSON.parse(values.get("hector_workout_settings_v1"));
  assert.equal(persisted.displayName, "Mar\u00eda");
  assert.equal(persisted.animations, false);
  assert.equal(isOnboardingRequired(persisted), false);
});

test("a failed display-name write leaves onboarding required and releases for retry", async () => {
  const original = settings(null);
  installLocalStorage(original, { failWrites: true });
  await assert.rejects(saveDisplayName("Alex").promise, /storage unavailable/u);
  assert.equal(isOnboardingRequired(original), true);

  installLocalStorage(original);
  const retry = saveDisplayName("Alex");
  assert.equal(retry.started, true);
  assert.equal((await retry.promise).saved, true);
});

test("onboarding persists settings before the current schema marker", async () => {
  const original = settings(null);
  const values = installLocalStorage(original, { initialMarker: 2 });
  const result = await completeOnboarding("  Alex  ").promise;
  assert.equal(result.saved, true);
  assert.equal(result.displayName, "Alex");
  assert.equal(
    JSON.parse(values.get("hector_workout_settings_v1")).displayName,
    "Alex",
  );
  assert.equal(values.get("hector_workout_data_schema_version"), "2");
});

test("onboarding marker failure rolls back the display name and allows retry", async () => {
  const original = settings(null);
  const values = installLocalStorage(original, {
    failKey: "hector_workout_data_schema_version",
    initialMarker: 2,
  });
  await assert.rejects(
    completeOnboarding("Retry Me").promise,
    (error) => error.code === "onboarding_persistence_failed",
  );
  assert.deepEqual(
    JSON.parse(values.get("hector_workout_settings_v1")),
    original,
  );
  assert.equal(values.get("hector_workout_data_schema_version"), "2");

  installLocalStorage(original, { initialMarker: 2 });
  const retry = completeOnboarding("Retry Me");
  assert.equal(retry.started, true);
  assert.equal((await retry.promise).saved, true);
});
