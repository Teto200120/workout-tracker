import { DB_NAME, DB_VERSION, DEFAULT_TEMPLATES, STORES } from "../core/constants.js";
import { id } from "../core/utils.js";
import {
  validateRoutineInput,
  validateWorkoutInput
} from "../domain/input-guardrails.js";
import { createValidationError } from "../schema/errors.js";
import {
  assertValidLegacyWeight,
  assertValidRoutine,
  assertValidWorkout
} from "../schema/validators.js";

let database = null;

export function isDatabaseOpen() {
  return Boolean(database);
}

export function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const openedDatabase = event.target.result;
      if (!openedDatabase.objectStoreNames.contains("workouts")) {
        const store = openedDatabase.createObjectStore("workouts", { keyPath: "id" });
        store.createIndex("date", "date", { unique: false });
        store.createIndex("type", "type", { unique: false });
      }
      if (!openedDatabase.objectStoreNames.contains("weights")) {
        const store = openedDatabase.createObjectStore("weights", { keyPath: "id" });
        store.createIndex("date", "date", { unique: false });
      }
      if (!openedDatabase.objectStoreNames.contains("templates")) {
        const store = openedDatabase.createObjectStore("templates", { keyPath: "id" });
        store.createIndex("name", "name", { unique: false });
      }
    };

    request.onsuccess = () => {
      database = request.result;
      resolve(database);
    };
    request.onerror = () => reject(request.error);
  });
}

function objectStore(name, mode = "readonly") {
  if (!database) throw new Error("Database is not open.");
  return database.transaction(name, mode).objectStore(name);
}

function putItem(storeName, item) {
  return new Promise((resolve, reject) => {
    const request = objectStore(storeName, "readwrite").put(item);
    request.onsuccess = () => resolve(item);
    request.onerror = () => reject(request.error);
  });
}

function getAllItems(storeName) {
  return new Promise((resolve, reject) => {
    const request = objectStore(storeName).getAll();
    request.onsuccess = () => resolve(request.result || []);
    request.onerror = () => reject(request.error);
  });
}

function deleteStoredItem(storeName, itemId) {
  return new Promise((resolve, reject) => {
    const request = objectStore(storeName, "readwrite").delete(itemId);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

function clearStoredItems(storeName) {
  return new Promise((resolve, reject) => {
    const request = objectStore(storeName, "readwrite").clear();
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

export function getWorkouts() {
  return getAllItems("workouts");
}

export function saveWorkoutRecord(workout) {
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
  return putItem("workouts", workout);
}

export function deleteWorkoutRecord(workoutId) {
  return deleteStoredItem("workouts", workoutId);
}

export function getRoutines() {
  return getAllItems("templates").then((templates) => templates.sort((a, b) => a.name.localeCompare(b.name)));
}

export function saveRoutine(routine) {
  assertValidRoutine(routine, { path: "routine", source: "application" });
  const guardrails = validateRoutineInput(routine);
  if (!guardrails.valid) {
    throw createValidationError(guardrails.errors, {
      code: "input_guardrail_failed",
      source: "application",
    });
  }
  return putItem("templates", routine);
}

export function deleteRoutine(routineId) {
  return deleteStoredItem("templates", routineId);
}

export function clearRoutines() {
  return clearStoredItems("templates");
}

export function getLegacyWeights() {
  return getAllItems("weights");
}

export function saveLegacyWeight(weight) {
  assertValidLegacyWeight(weight, { path: "legacyWeight", source: "application" });
  return putItem("weights", weight);
}

export async function clearApplicationStores() {
  await replaceApplicationRecords({ workouts: [], legacyWeights: [], routines: [] });
}

export async function getAllApplicationRecords() {
  const [workouts, legacyWeights, routines] = await Promise.all([
    getAllItems("workouts"),
    getAllItems("weights"),
    getAllItems("templates")
  ]);
  return { workouts, legacyWeights, routines };
}

function assertRecordCollections({ workouts, legacyWeights, routines }, options = {}) {
  workouts.forEach((workout, index) => assertValidWorkout(workout, {
    path: `workouts[${index}]`,
    source: options.source || "application",
    deferIdConstraints: options.deferIdConstraints
  }));
  legacyWeights.forEach((weight, index) => assertValidLegacyWeight(weight, {
    path: `legacyWeights[${index}]`,
    source: options.source || "application",
    deferIdConstraints: options.deferIdConstraints
  }));
  routines.forEach((routine, index) => assertValidRoutine(routine, {
    path: `routines[${index}]`,
    source: options.source || "application",
    deferIdConstraints: options.deferIdConstraints
  }));
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
    transaction.onabort = () => reject(firstError || transaction.error || new Error("IndexedDB application transaction was aborted."));
    try {
      if (options.replace) STORES.forEach((storeName) => guardRequest(transaction.objectStore(storeName).clear()));
      records.workouts.forEach((workout) => guardRequest(transaction.objectStore("workouts").put(workout)));
      records.legacyWeights.forEach((weight) => guardRequest(transaction.objectStore("weights").put(weight)));
      records.routines.forEach((routine) => guardRequest(transaction.objectStore("templates").put(routine)));
    } catch (error) {
      abort(error);
    }
  });
}

export function importBackupRecords(records) {
  return runApplicationRecordTransaction(records, {
    source: "backup",
    deferIdConstraints: true
  });
}

export function replaceApplicationRecords(records, options = {}) {
  return runApplicationRecordTransaction(records, {
    ...options,
    replace: true
  });
}

export async function seedDefaultTemplates() {
  const existing = await getRoutines();
  if (existing.length) return;
  for (const [name, exercises] of Object.entries(DEFAULT_TEMPLATES)) {
    await saveRoutine({
      id: id(),
      name,
      exercises,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    });
  }
}
