import assert from "node:assert/strict";
import test from "node:test";
import { coordinateApplicationSchemaMigration } from "../../src/js/application/data-schema.js";
import { CURRENT_APPLICATION_SCHEMA_VERSION } from "../../src/js/schema/versions.js";
import {
  canonicalApplicationData,
  getSchemaFixture,
  legacyApplicationData,
} from "../fixtures/schema-data.js";

function createEffects(options = {}) {
  const state = {
    calls: [],
    marker: null,
    localData: null,
    restoredSnapshot: null,
    recordWrites: [],
  };
  return {
    state,
    replaceRecords: async (records, replaceOptions) => {
      state.calls.push(
        replaceOptions?.validate === false
          ? "rollback-records"
          : "replace-records",
      );
      state.recordWrites.push({
        records: structuredClone(records),
        options: replaceOptions,
      });
      if (options.failReplace && !replaceOptions)
        throw new Error("replace failed");
    },
    writeLocalData: (data) => {
      state.calls.push("write-local");
      if (options.failLocal) throw new Error("local write failed");
      state.localData = structuredClone(data);
    },
    writeMarker: (version) => {
      state.calls.push("write-marker");
      if (options.failMarker) throw new Error("marker write failed");
      state.marker = version;
    },
    restoreLocalSnapshot: (snapshot) => {
      state.calls.push("restore-local");
      state.restoredSnapshot = structuredClone(snapshot);
    },
  };
}

function coordinationOptions(sourceData, effects) {
  return {
    sourceVersion: 0,
    sourceData,
    localSnapshot: {
      settings: "legacy-settings",
      applicationSchemaVersion: null,
    },
    replaceRecords: effects.replaceRecords,
    writeLocalData: effects.writeLocalData,
    writeMarker: effects.writeMarker,
    restoreLocalSnapshot: effects.restoreLocalSnapshot,
  };
}

test("startup stages records, localStorage, then the schema marker", async () => {
  const effects = createEffects();
  const result = await coordinateApplicationSchemaMigration(
    coordinationOptions(legacyApplicationData(), effects),
  );
  assert.equal(result.migrated, true);
  assert.deepEqual(effects.state.calls, [
    "replace-records",
    "write-local",
    "write-marker",
  ]);
  assert.equal(effects.state.marker, CURRENT_APPLICATION_SCHEMA_VERSION);
  assert.equal(
    effects.state.recordWrites[0].records.workouts[0].id,
    "legacy-workout",
  );
  assert.equal(effects.state.localData.goals.weeklyGoal, 3);
});

test("marker and source data roll back when a localStorage write fails", async () => {
  const source = legacyApplicationData();
  const effects = createEffects({ failLocal: true });
  await assert.rejects(
    coordinateApplicationSchemaMigration(coordinationOptions(source, effects)),
    (error) => error.code === "migration_persistence_failed",
  );
  assert.equal(effects.state.marker, null);
  assert.deepEqual(effects.state.calls, [
    "replace-records",
    "write-local",
    "rollback-records",
    "restore-local",
  ]);
  assert.deepEqual(
    effects.state.recordWrites[1].records.workouts,
    source.workouts,
  );
  assert.equal(effects.state.recordWrites[1].options.validate, false);
  assert.deepEqual(effects.state.restoredSnapshot, {
    settings: "legacy-settings",
    applicationSchemaVersion: null,
  });
});

test("marker failure triggers the same compensating rollback", async () => {
  const effects = createEffects({ failMarker: true });
  await assert.rejects(
    coordinateApplicationSchemaMigration(
      coordinationOptions(legacyApplicationData(), effects),
    ),
    (error) => error.code === "migration_persistence_failed",
  );
  assert.deepEqual(effects.state.calls, [
    "replace-records",
    "write-local",
    "write-marker",
    "rollback-records",
    "restore-local",
  ]);
  assert.equal(effects.state.marker, null);
});

test("a retry after a failed migration succeeds without duplicating records", async () => {
  const source = getSchemaFixture("retryAfterFailedMigration");
  const failedEffects = createEffects({ failLocal: true });
  await assert.rejects(
    coordinateApplicationSchemaMigration(
      coordinationOptions(source, failedEffects),
    ),
  );

  const retryEffects = createEffects();
  const result = await coordinateApplicationSchemaMigration(
    coordinationOptions(source, retryEffects),
  );
  assert.equal(result.data.workouts.length, 1);
  assert.equal(retryEffects.state.recordWrites[0].records.workouts.length, 1);
  assert.equal(retryEffects.state.marker, CURRENT_APPLICATION_SCHEMA_VERSION);
});

test("current-version startup validates without rewriting or duplicating data", async () => {
  const effects = createEffects();
  const current = canonicalApplicationData();
  const result = await coordinateApplicationSchemaMigration({
    ...coordinationOptions(current, effects),
    sourceVersion: CURRENT_APPLICATION_SCHEMA_VERSION,
  });
  assert.equal(result.migrated, false);
  assert.deepEqual(result.data, current);
  assert.deepEqual(effects.state.calls, []);
});

test("invalid legacy startup data fails before any persistence effect", async () => {
  const effects = createEffects();
  await assert.rejects(
    coordinateApplicationSchemaMigration(
      coordinationOptions(getSchemaFixture("failedStartupMigration"), effects),
    ),
    (error) => error.path === "workouts[0].exercises",
  );
  assert.deepEqual(effects.state.calls, []);
});
