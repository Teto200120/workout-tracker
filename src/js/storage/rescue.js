import { DB_NAME } from "../core/constants.js";
import { openManagedDatabase } from "./connection.js";
import {
  captureApplicationLocalStorage,
  getRawEducationRecord,
} from "./local.js";

// No requested version, schema validation, seeding or migration: even data from
// a newer app or malformed local JSON must remain available for recovery.
export async function buildRescueExport() {
  const database = await openManagedDatabase({
    factory: indexedDB,
    name: DB_NAME,
    upgrade(event) {
      event.target.transaction.abort();
    },
    onUnavailable() {},
  });
  try {
    const stores = Array.from(database.objectStoreNames);
    const records = await new Promise((resolve, reject) => {
      if (!stores.length) {
        resolve({});
        return;
      }
      const transaction = database.transaction(stores, "readonly");
      const result = {};
      transaction.oncomplete = () => resolve(result);
      transaction.onabort = () =>
        reject(transaction.error || new Error("Rescue read aborted."));
      for (const name of stores) {
        const store = transaction.objectStore(name);
        const entry = (result[name] = { keys: [], values: [] });
        store.getAllKeys().onsuccess = (event) => {
          entry.keys = event.target.result;
        };
        store.getAll().onsuccess = (event) => {
          entry.values = event.target.result;
        };
      }
    });
    return {
      kind: "workout-tracker-raw-rescue",
      rescueFileVersion: 1,
      exportedAt: new Date().toISOString(),
      database: { name: DB_NAME, version: database.version, stores: records },
      localStorage: captureApplicationLocalStorage(),
      education: getRawEducationRecord(),
    };
  } finally {
    database.close();
  }
}
