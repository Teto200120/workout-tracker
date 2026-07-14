// Reviewed mappings from saved, provider-neutral app names to one deterministic
// catalog record. Additions should be backed by an existing app exercise name
// and a manual review of the target exercise's movement and equipment.
export const EXERCISE_CATALOG_ALIASES = Object.freeze([
  Object.freeze({
    localName: "Flat Bench Press",
    catalogId: "free-exercise-db:Barbell_Bench_Press_-_Medium_Grip",
  }),
  Object.freeze({
    localName: "Tricep Pushdown",
    catalogId: "free-exercise-db:Triceps_Pushdown",
  }),
  Object.freeze({
    localName: "V-Bar Lat Pulldown",
    catalogId: "free-exercise-db:V-Bar_Pulldown",
  }),
  // The reviewed provider record explicitly uses a low cable row with a V-bar.
  Object.freeze({
    localName: "V-Bar Cable Row",
    catalogId: "free-exercise-db:Seated_Cable_Rows",
  }),
  Object.freeze({
    localName: "Incline Hammer Curl",
    catalogId: "free-exercise-db:Incline_Hammer_Curls",
  }),
  Object.freeze({
    localName: "Cable Bicep Curl",
    catalogId: "free-exercise-db:Standing_Biceps_Cable_Curl",
  }),
  Object.freeze({
    localName: "Standing Calf Raise",
    catalogId: "free-exercise-db:Standing_Calf_Raises",
  }),
]);
