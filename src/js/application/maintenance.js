// Keep export, restore, clear and reset in FIFO order rather than coalescing
// different requests. Web Locks extend this boundary across cooperating tabs.
export function createMaintenanceCoordinator(lockManager = globalThis.navigator?.locks) {
  let tail = Promise.resolve();
  return (action) => {
    const operation = tail.then(() => lockManager?.request
      ? lockManager.request("hector-workout-maintenance-v1", action)
      : action());
    tail = operation.catch(() => {});
    return operation;
  };
}

export const runMaintenanceOperation = createMaintenanceCoordinator();
