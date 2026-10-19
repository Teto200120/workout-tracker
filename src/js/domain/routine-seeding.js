const FALLBACK_ID = "starter:v1:Custom";

export function isPendingStarterRoutine(routine) {
  return (
    routine.id === FALLBACK_ID &&
    routine.name === "Custom" &&
    routine.starterSeedPending === 1 &&
    routine.exercises.length === 0
  );
}

// A pending bootstrap may add missing defaults, but never replace user records.
export function planRoutineSeed(existing, prepared, ready) {
  if (existing.length && !existing.some(isPendingStarterRoutine)) return null;
  if (!ready) return existing.length ? null : prepared;
  const ids = new Set(existing.map((routine) => routine.id));
  const names = new Set(
    existing.map((routine) => routine.name.trim().toLowerCase()),
  );
  return [
    ...existing.map((routine) => {
      if (!isPendingStarterRoutine(routine)) return routine;
      const copy = { ...routine };
      delete copy.starterSeedPending;
      return copy;
    }),
    ...prepared.filter(
      (routine) =>
        !ids.has(routine.id) && !names.has(routine.name.trim().toLowerCase()),
    ),
  ];
}
