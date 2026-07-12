import { DB_NAME, DB_VERSION, DEFAULT_TEMPLATES, STORES } from "../core/constants.js";
import { id } from "../core/utils.js";

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
  return putItem("workouts", workout);
}

export function deleteWorkoutRecord(workoutId) {
  return deleteStoredItem("workouts", workoutId);
}

export function getRoutines() {
  return getAllItems("templates").then((templates) => templates.sort((a, b) => a.name.localeCompare(b.name)));
}

export function saveRoutine(routine) {
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
  return putItem("weights", weight);
}

export async function clearApplicationStores() {
  for (const name of STORES) await clearStoredItems(name);
}

export function importBackupRecords({ workouts, legacyWeights, routines }) {
  return new Promise((resolve, reject) => {
    if (!database) {
      reject(new Error("Database is not open."));
      return;
    }
    const transaction = database.transaction(STORES, "readwrite");
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error || new Error("Backup import was aborted."));
    try {
      workouts.forEach((workout) => transaction.objectStore("workouts").put(workout));
      legacyWeights.forEach((weight) => transaction.objectStore("weights").put(weight));
      routines.forEach((routine) => transaction.objectStore("templates").put(routine));
    } catch (error) {
      transaction.abort();
      reject(error);
    }
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
