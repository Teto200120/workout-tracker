// All cooperating database writers share this boundary with maintenance. An
// explicit, short-lived token permits nested writes without granting unrelated
// work permission to bypass the queue while an async operation is suspended.
export function createDataCoordinator(lockManager = globalThis.navigator?.locks, beforeOperation = () => {}) {
  let tail = Promise.resolve();
  const active = new WeakSet();
  return (action, operation) => {
    if (operation !== undefined) {
      if (!operation || !active.has(operation)) {
        return Promise.reject(new Error("Storage operation token is no longer active."));
      }
      return Promise.resolve().then(() => {
        if (!active.has(operation)) throw new Error("Storage operation token is no longer active.");
        return action(operation);
      });
    }
    const execute = async () => {
      const token = Object.freeze({});
      active.add(token);
      try {
        const recovery = beforeOperation(token);
        if (recovery) await recovery;
        return await action(token);
      }
      finally { active.delete(token); }
    };
    const result = tail.then(() => lockManager?.request
      ? lockManager.request("hector-workout-maintenance-v1", execute)
      : execute());
    tail = result.catch(() => {});
    return result;
  };
}

let recoveryHandler = null;

export function setDataRecoveryHandler(handler) {
  recoveryHandler = handler;
}

export const runDataOperation = createDataCoordinator(undefined, (operation) => recoveryHandler?.(operation));
