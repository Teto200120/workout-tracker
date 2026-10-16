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
