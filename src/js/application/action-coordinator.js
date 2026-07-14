export function createActionCoordinator() {
  let currentPromise = null;

  return Object.freeze({
    get active() {
      return Boolean(currentPromise);
    },
    run(action) {
      if (currentPromise) {
        return { started: false, promise: currentPromise };
      }
      let trackedPromise;
      const operation = Promise.resolve().then(action);
      trackedPromise = operation.finally(() => {
        if (currentPromise === trackedPromise) currentPromise = null;
      });
      currentPromise = trackedPromise;
      return { started: true, promise: trackedPromise };
    },
  });
}
