import {
  DB_NAME,
  DB_VERSION,
  RECOVERY_STORE,
  STORES,
} from "../core/constants.js";
import {
  INPUT_LIMITS,
  nameComparisonKey,
  validateRoutineInput,
  validateWorkoutInput,
} from "../domain/input-guardrails.js";
import { DataSchemaError, createValidationError } from "../schema/errors.js";
import {
  assertValidLegacyWeight,
  assertValidRoutine,
  assertValidWorkout,
} from "../schema/validators.js";

import { planRoutineSeed } from "../domain/routine-seeding.js";
import { runDataOperation } from "./operation.js";
import {
  runConditionalStoreMutation,
  runStoreMutation,
} from "./transaction.js";
import { openManagedDatabase } from "./connection.js";

let database = null;

export function isDatabaseOpen() {
  return Boolean(database);
}

let opening = null;

export function openDatabase() {
  if (database) return Promise.resolve(database);
  if (opening) return opening;
  opening = openManagedDatabase({
    factory: indexedDB,
    name: DB_NAME,
    version: DB_VERSION,
    upgrade(event) {
      const openedDatabase = event.target.result;
      if (!openedDatabase.objectStoreNames.contains(RECOVERY_STORE)) {
        openedDatabase.createObjectStore(RECOVERY_STORE, { keyPath: "id" });
      }
      if (!openedDatabase.objectStoreNames.contains("workouts")) {
        const store = openedDatabase.createObjectStore("workouts", {
          keyPath: "id",
        });
        store.createIndex("date", "date", { unique: false });
        store.createIndex("type", "type", { unique: false });
      }
      if (!openedDatabase.objectStoreNames.contains("weights")) {
        const store = openedDatabase.createObjectStore("weights", {
          keyPath: "id",
        });
        store.createIndex("date", "date", { unique: false });
      }
      if (!openedDatabase.objectStoreNames.contains("templates")) {
        const store = openedDatabase.createObjectStore("templates", {
          keyPath: "id",
        });
        store.createIndex("name", "name", { unique: false });
      }
    },
    onUnavailable(connection, reason) {
      if (database !== connection) return;
      database = null;
      globalThis.dispatchEvent(
        new CustomEvent("tracker-storage-unavailable", { detail: reason }),
      );
    },
  })
    .then((connection) => {
      database = connection;
      return connection;
    })
    .finally(() => {
      opening = null;
    });
  return opening;
}

function objectStore(name, mode = "readonly") {
  if (!database) throw new Error("Database is not open.");
  return database.transaction(name, mode).objectStore(name);
}

function putItem(storeName, item, operation) {
  return runDataOperation(
    () =>
      runStoreMutation(database, storeName, (store) => store.put(item), item),
    operation,
  );
}

function getAllItems(storeName) {
  return new Promise((resolve, reject) => {
    const request = objectStore(storeName).getAll();
    request.onsuccess = () => resolve(request.result || []);
    request.onerror = () => reject(request.error);
  });
}

function deleteStoredItem(storeName, itemId) {
  return runDataOperation(() =>
    runStoreMutation(database, storeName, (store) => store.delete(itemId)),
  );
}

function clearStoredItems(storeName) {
  return runDataOperation(() =>
    runStoreMutation(database, storeName, (store) => store.clear()),
  );
}

export function getWorkouts() {
  return getAllItems("workouts");
}

export function saveWorkoutRecord(workout, options = {}) {
  assertValidWorkout(workout, { path: "workout", source: "application" });
  const guardrails = validateWorkoutInput(workout, {
    allowHistoricalRpeZero: true,
  });
  if (!guardrails.valid) {
    throw createValidationError(guardrails.errors, {
      code: "input_guardrail_failed",
      source: "application",
    });
  }
  if (Object.hasOwn(options, "expected")) {
    const snapshot = structuredClone(workout);
    const expected = structuredClone(options.expected);
    return runDataOperation(
      () =>
        runConditionalStoreMutation(
          database,
          "workouts",
          snapshot.id,
          expected,
          (store) => store.put(snapshot),
          snapshot,
        ),
      options.operation,
    );
  }
  return putItem("workouts", workout, options.operation);
}

export function deleteWorkoutRecord(workoutId, options = {}) {
  if (Object.hasOwn(options, "expected")) {
    const expected = structuredClone(options.expected);
    return runDataOperation(
      () =>
        runConditionalStoreMutation(
          database,
          "workouts",
          workoutId,
          expected,
          (store) => store.delete(workoutId),
        ),
      options.operation,
    );
  }
  return deleteStoredItem("workouts", workoutId);
}

export function getRoutines() {
  return getAllItems("templates").then((templates) =>
    templates.sort((a, b) => a.name.localeCompare(b.name)),
  );
}

export function saveRoutine(routine, options = {}) {
  assertValidRoutine(routine, { path: "routine", source: "application" });
  const guardrails = validateRoutineInput(routine);
  if (!guardrails.valid) {
    throw createValidationError(guardrails.errors, {
      code: "input_guardrail_failed",
      source: "application",
    });
  }
  if (Object.hasOwn(options, "expected")) {
    const snapshot = structuredClone(routine);
    const expected = structuredClone(options.expected);
    return runDataOperation(
      () =>
        runConditionalStoreMutation(
          database,
          "templates",
          snapshot.id,
          expected,
          (store) => store.put(snapshot),
          snapshot,
          {
            validateRecords(records) {
              const name = nameComparisonKey(snapshot.name);
              if (
                records.some(
                  (item) =>
                    item.id !== snapshot.id &&
                    nameComparisonKey(item.name) === name,
                )
              ) {
                throw new DataSchemaError(
                  "Another routine already uses this name.",
                  { code: "routine_name_conflict" },
                );
              }
              if (
                !records.some((item) => item.id === snapshot.id) &&
                records.length >= INPUT_LIMITS.routines
              ) {
                throw new DataSchemaError(
                  `The app is limited to ${INPUT_LIMITS.routines} saved routines.`,
                  { code: "routine_limit" },
                );
              }
            },
          },
        ),
      options.operation,
    );
  }
  return putItem("templates", routine, options.operation);
}

export function deleteRoutine(routineId, options = {}) {
  if (Object.hasOwn(options, "expected")) {
    const expected = structuredClone(options.expected);
    return runDataOperation(
      () =>
        runConditionalStoreMutation(
          database,
          "templates",
          routineId,
          expected,
          (store) => store.delete(routineId),
        ),
      options.operation,
    );
  }
  return deleteStoredItem("templates", routineId);
}

export function clearRoutines() {
  return clearStoredItems("templates");
}

export function getLegacyWeights() {
  return getAllItems("weights");
}

export function saveLegacyWeight(weight) {
  assertValidLegacyWeight(weight, {
    path: "legacyWeight",
    source: "application",
  });
  return putItem("weights", weight);
}

export async function clearApplicationStores() {
  await replaceApplicationRecords({
    workouts: [],
    legacyWeights: [],
    routines: [],
  });
}

export async function getAllApplicationRecords() {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORES, "readonly");
    const records = {};
    let failure = null;
    transaction.oncomplete = () => resolve(records);
    transaction.onerror = (event) => {
      failure ||= event.target?.error || transaction.error;
    };
    transaction.onabort = () =>
      reject(failure || transaction.error || new Error("Snapshot aborted."));
    for (const [store, field] of [
      ["workouts", "workouts"],
      ["weights", "legacyWeights"],
      ["templates", "routines"],
    ]) {
      const request = transaction.objectStore(store).getAll();
      request.onsuccess = () => {
        records[field] = request.result || [];
      };
    }
  });
}

function assertRecordCollections(
  { workouts, legacyWeights, routines },
  options = {},
) {
  workouts.forEach((workout, index) =>
    assertValidWorkout(workout, {
      path: `workouts[${index}]`,
      source: options.source || "application",
      deferIdConstraints: options.deferIdConstraints,
    }),
  );
  legacyWeights.forEach((weight, index) =>
    assertValidLegacyWeight(weight, {
      path: `legacyWeights[${index}]`,
      source: options.source || "application",
      deferIdConstraints: options.deferIdConstraints,
    }),
  );
  routines.forEach((routine, index) =>
    assertValidRoutine(routine, {
      path: `routines[${index}]`,
      source: options.source || "application",
      deferIdConstraints: options.deferIdConstraints,
    }),
  );
}

function runApplicationRecordTransaction(records, options = {}) {
  return new Promise((resolve, reject) => {
    if (!database) {
      reject(new Error("Database is not open."));
      return;
    }
    if (options.validate !== false) assertRecordCollections(records, options);
    const transaction = database.transaction(STORES, "readwrite");
    let firstError = null;
    const abort = (error) => {
      firstError ||= error;
      try {
        transaction.abort();
      } catch {
        // The transaction may already be aborting after a request failure.
      }
    };
    const guardRequest = (request) => {
      request.onerror = (event) => {
        event.preventDefault();
        abort(request.error || new Error("IndexedDB record write failed."));
      };
    };
    transaction.oncomplete = () => resolve();
    transaction.onerror = (event) => {
      firstError ||= event.target?.error || transaction.error;
    };
    transaction.onabort = () =>
      reject(
        firstError ||
          transaction.error ||
          new Error("IndexedDB application transaction was aborted."),
      );
    try {
      if (options.replace)
        STORES.forEach((storeName) =>
          guardRequest(transaction.objectStore(storeName).clear()),
        );
      records.workouts.forEach((workout) =>
        guardRequest(transaction.objectStore("workouts").put(workout)),
      );
      records.legacyWeights.forEach((weight) =>
        guardRequest(transaction.objectStore("weights").put(weight)),
      );
      records.routines.forEach((routine) =>
        guardRequest(transaction.objectStore("templates").put(routine)),
      );
    } catch (error) {
      abort(error);
    }
  });
}

export function importBackupRecords(records, { operation } = {}) {
  return runDataOperation(
    () =>
      runApplicationRecordTransaction(records, {
        source: "backup",
        deferIdConstraints: true,
      }),
    operation,
  );
}

export function replaceApplicationRecords(records, options = {}) {
  return runDataOperation(
    () =>
      runApplicationRecordTransaction(records, {
        ...options,
        replace: true,
      }),
    options.operation,
  );
}

export function commitRoutineLibrary(routines, options = {}) {
  return runDataOperation(
    () => performRoutineLibraryCommit(routines, options),
    options.operation,
  );
}

function performRoutineLibraryCommit(
  routines,
  { seed = false, ready = true } = {},
) {
  const validate = (items) =>
    items.forEach((routine) => {
      assertValidRoutine(routine, { source: "application", path: "routine" });
      const result = validateRoutineInput(routine);
      if (!result.valid) throw createValidationError(result.errors);
    });
  validate(routines);
  return new Promise((resolve, reject) => {
    const transaction = database.transaction("templates", "readwrite");
    const store = transaction.objectStore("templates");
    let failure = null;
    transaction.oncomplete = () => resolve();
    transaction.onerror = (event) => {
      failure ||= event.target?.error || transaction.error;
    };
    transaction.onabort = () =>
      reject(
        failure || transaction.error || new Error("Routine update aborted."),
      );
    const read = store.getAll();
    read.onsuccess = () => {
      try {
        const replacement = seed
          ? planRoutineSeed(read.result, routines, ready)
          : routines;
        if (replacement === null) return;
        validate(replacement);
        // Keep the read/decision/write in the same transaction, including concurrent tabs.
        store.clear();
        replacement.forEach((routine) => store.put(routine));
      } catch (error) {
        failure = error;
        transaction.abort();
      }
    };
  });
}

export function readRecoverySnapshot() {
  return getAllItems(RECOVERY_STORE).then((entries) => {
    if (entries.length > 1) throw new Error("Unexpected recovery records.");
    return entries[0] || null;
  });
}

export function writeRecoverySnapshot(snapshot, { operation } = {}) {
  return putItem(RECOVERY_STORE, snapshot, operation);
}

export function removeRecoverySnapshot({ operation } = {}) {
  return runDataOperation(
    () =>
      runStoreMutation(database, RECOVERY_STORE, (store) =>
        store.delete("application"),
      ),
    operation,
  );
}
