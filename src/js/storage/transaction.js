// A successful request is provisional until its transaction commits.
export function runStoreMutation(database, storeName, mutate, result) {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(storeName, "readwrite");
    let failure = null;
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = (event) => {
      failure ||= event.target?.error || transaction.error;
    };
    transaction.onabort = () => reject(
      failure || transaction.error || new Error("Database transaction aborted."),
    );
    try {
      const request = mutate(transaction.objectStore(storeName));
      request.onerror = () => { failure ||= request.error; };
    } catch (error) {
      failure = error;
      try { transaction.abort(); } catch { reject(error); }
    }
  });
}

export class DataConflictError extends Error {
  constructor() {
    super("The saved record changed after it was loaded.");
    this.name = "DataConflictError";
    this.code = "stale_record";
  }
}

// Snapshot comparison is independent of property order and keeps identifier types.
export function sameRecordSnapshot(left, right) {
  if (Object.is(left, right)) return true;
  if (!left || !right || typeof left !== "object" || typeof right !== "object") return false;
  if (Array.isArray(left) !== Array.isArray(right)) return false;
  if (Array.isArray(left) && left.length !== right.length) return false;
  if (!Array.isArray(left)) {
    const plain = (value) => [Object.prototype, null].includes(Object.getPrototypeOf(value));
    if (!plain(left) || !plain(right)) return false;
  }
  const keys = Object.keys(left);
  return keys.length === Object.keys(right).length && keys.every((key) =>
    Object.hasOwn(right, key) && sameRecordSnapshot(left[key], right[key]));
}

// The comparison and mutation share one readwrite transaction, including across tabs.
export function runConditionalStoreMutation(database, storeName, key, expected, mutate, result, { validateRecords } = {}) {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(storeName, "readwrite");
    let failure;
    const abort = (error) => {
      failure ||= error;
      try { transaction.abort(); } catch { reject(failure); }
    };
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = (event) => { failure ||= event.target?.error || transaction.error; };
    transaction.onabort = () => reject(failure || transaction.error || new Error("Database transaction aborted."));
    try {
      const store = transaction.objectStore(storeName);
      const request = validateRecords ? store.getAll() : store.get(key);
      request.onerror = () => { failure ||= request.error; };
      request.onsuccess = () => {
        try {
          const current = validateRecords ? request.result.find((item) => item.id === key) : request.result;
          if (!sameRecordSnapshot(current ?? null, expected)) throw new DataConflictError();
          validateRecords?.(request.result);
          const write = mutate(store);
          write.onerror = () => { failure ||= write.error; };
        } catch (error) { abort(error); }
      };
    } catch (error) { abort(error); }
  });
}
