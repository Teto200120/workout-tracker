import assert from "node:assert/strict";
import test from "node:test";
import {
  APPLICATION_MIGRATIONS,
  migrateApplicationData,
} from "../../src/js/schema/migrations.js";
import { validateApplicationData } from "../../src/js/schema/validators.js";
import { CURRENT_APPLICATION_SCHEMA_VERSION } from "../../src/js/schema/versions.js";
import {
  canonicalApplicationData,
  getSchemaFixture,
  legacyApplicationData,
} from "../fixtures/schema-data.js";

test("the registry contains ordered legacy-to-v1 and display-name v1-to-v2 migrations", () => {
  assert.deepEqual(Object.keys(APPLICATION_MIGRATIONS), ["0", "1"]);
  assert.equal(typeof APPLICATION_MIGRATIONS[0], "function");
  assert.equal(typeof APPLICATION_MIGRATIONS[1], "function");
});

test("legacy version zero migrates through canonical schema two", () => {
  const legacy = legacyApplicationData();
  const migrated = migrateApplicationData(legacy, 0);
  assert.equal(validateApplicationData(migrated).valid, true);
  assert.equal(migrated.workouts[0].id, legacy.workouts[0].id);
  assert.equal(migrated.workouts[0].date, legacy.workouts[0].date);
  assert.equal(migrated.workouts[0].exercises[0].sets[0].weight, "0");
  assert.equal(migrated.settings.animations, false);
  assert.equal(migrated.settings.displayName, null);
  assert.equal(migrated.goals.weeklyGoal, 3);
});

test("schema one gains a null display name without changing existing settings", () => {
  const schemaOne = canonicalApplicationData();
  delete schemaOne.settings.displayName;
  const before = structuredClone(schemaOne);
  const migrated = migrateApplicationData(schemaOne, 1);
  assert.equal(migrated.settings.displayName, null);
  assert.deepEqual(
    { ...migrated.settings, displayName: undefined },
    { ...before.settings, displayName: undefined },
  );
  assert.deepEqual(schemaOne, before);
});

test("migration is deterministic and does not mutate its input", () => {
  const legacy = legacyApplicationData();
  const before = structuredClone(legacy);
  const first = migrateApplicationData(legacy, 0);
  const second = migrateApplicationData(legacy, 0);
  assert.deepEqual(first, second);
  assert.deepEqual(legacy, before);
});

test("repeated migration of current data does not corrupt or reorder it", () => {
  const current = canonicalApplicationData();
  const migrated = migrateApplicationData(
    current,
    CURRENT_APPLICATION_SCHEMA_VERSION,
  );
  assert.deepEqual(migrated, current);
  assert.notStrictEqual(migrated, current);
  assert.deepEqual(
    migrated.routines[0].exercises,
    current.routines[0].exercises,
  );
});

test("invalid migration input reports a structured nested path", () => {
  const invalid = getSchemaFixture("failedStartupMigration");
  assert.throws(
    () => migrateApplicationData(invalid, 0),
    (error) =>
      error.code === "schema_validation_failed" &&
      error.path === "workouts[0].exercises",
  );
});

test("invalid migration output is rejected before it can be persisted", () => {
  assert.throws(
    () =>
      migrateApplicationData(legacyApplicationData(), 0, {
        migrations: {
          0: (value) => ({ ...value, workouts: "invalid-output" }),
        },
      }),
    (error) =>
      error.code === "invalid_migration_output" && error.path === "workouts",
  );
});

test("unsupported future application versions are rejected", () => {
  assert.throws(
    () =>
      migrateApplicationData(
        canonicalApplicationData(),
        CURRENT_APPLICATION_SCHEMA_VERSION + 1,
      ),
    (error) => error.code === "future_application_schema_version",
  );
});
