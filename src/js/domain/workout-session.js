import { workoutDurationMinutes } from "./workout-metrics.js";

export function buildWorkoutSessionRecord({
  fields,
  exercises,
  original = null,
  recordId,
  originRoutineId = null,
  createdAt,
  includeEmptySets = false,
}) {
  const startTime = fields.startTime;
  const endTime = fields.endTime;
  const preserveDuration =
    original &&
    original.startTime === startTime &&
    original.endTime === endTime;
  const duration = preserveDuration
    ? original.durationMinutes === undefined
      ? {}
      : { durationMinutes: original.durationMinutes }
    : { durationMinutes: workoutDurationMinutes({ startTime, endTime }) };
  const result = {
    ...structuredClone(original || {}),
    id: recordId,
    date: fields.date,
    type: fields.type,
    startTime,
    endTime,
    ...duration,
    notes: fields.notes.trim(),
    tags: [...(original?.tags || [])],
    exercises: exercises
      .map((exercise) => ({
        ...structuredClone(exercise),
        name: exercise.name.trim(),
        notes: exercise.notes.trim(),
        sets: exercise.sets
          .map((set) => ({
            ...structuredClone(set),
            weight: set.weight.trim(),
            reps: set.reps.trim(),
            rpe: set.rpe.trim(),
          }))
          .filter(
            (set) => includeEmptySets || set.weight || set.reps || set.rpe,
          ),
      }))
      .filter(
        (exercise) => exercise.name || exercise.notes || exercise.sets.length,
      ),
    createdAt: original?.createdAt ?? createdAt,
    ...(originRoutineId === null ? {} : { originRoutineId }),
  };
  return result;
}
