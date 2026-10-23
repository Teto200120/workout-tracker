import { readFileSync, writeFileSync } from "node:fs";
import { DEFAULT_APP_SETTINGS } from "../src/js/core/constants.js";
import {
  getExerciseProfile,
  buildTargetFromLastSets,
} from "../src/js/domain/training-rules.js";
const set = (weight = "", reps = "", extra = {}) => ({
  weight,
  reps,
  rpe: "",
  done: false,
  warmup: false,
  ...extra,
});
const cases = [
  ["no sets", "Bench Press", []],
  ["warmups only", "Bench Press", [set("45", "10", { warmup: true })]],
  ["empty rows", "Unknown", [set()]],
  [
    "compound top",
    "Bench Press",
    [set("100", "10", { done: true }), set("100", "10")],
  ],
  [
    "lowest reps first",
    "Bench Press",
    [set("100", "8"), set("100", "6"), set("100", "7")],
  ],
  [
    "hard RPE",
    "Bench Press",
    [set("100", "10", { rpe: "9" }), set("100", "10")],
  ],
  [
    "very hard RPE",
    "Bench Press",
    [set("100", "10", { rpe: "9.5" }), set("100", "8")],
  ],
  [
    "hard RPE disabled",
    "Bench Press",
    [set("100", "10", { rpe: "10" })],
    { rpeAware: false },
  ],
  [
    "warmups excluded",
    "Bench Press",
    [set("999", "100", { warmup: true }), set("100", "10")],
  ],
  ["isolation precedence", "Leg Curl Press", [set("20", "20")]],
  ["pull classification", "Cable Row", [set("40", "12")]],
  ["pull-up classification", "Pull-up", [set("", "8")]],
  ["general profile", "Custom movement", [set("50", "12")]],
  [
    "custom increment and range",
    "Bench Press",
    [set("100", "8")],
    { defaultWeightJump: 2.5, compoundMin: 5, compoundMax: 8 },
  ],
  [
    "most common load",
    "Bench",
    [set("120", "5"), set("100", "8"), set("100", "9")],
  ],
  ["load count tie retains first", "Bench", [set("120", "5"), set("100", "8")]],
  [
    "distinct textual loads group numerically",
    "Bench",
    [set("100.0", "7"), set("100", "8"), set("80", "9")],
  ],
  [
    "numeric versus string zero",
    "Custom",
    [set(0, 0), set("0", "0"), set("", "5")],
  ],
  ["numeric zero weight with reps", "Custom", [set(0, 8)]],
  ["blank reps stay blank", "Bench", [set("100", "8"), set("100", "")]],
  ["all blank reps", "Bench", [set("100", ""), set("100", "")]],
  [
    "legacy RPE zero and extensions",
    "Bench",
    [set("100", "10", { rpe: 0, extension: { keep: [0, null] } })],
  ],
  ["binary rounding", "Bench", [set("100", "8", { rpe: "8.35" })]],
  ["rounding tie", "Bench", [set("100", "8", { rpe: "8.25" })]],
  [
    "unsafe target remains derived",
    "Bench",
    [set("10000", "10")],
    { defaultWeightJump: 1000 },
  ],
];
const result = {
  cases: cases.map(([label, name, sets, changes]) => {
    const settings = { ...DEFAULT_APP_SETTINGS, ...changes };
    const { min, max, increment, type } = getExerciseProfile(name, settings);
    return {
      label,
      name,
      settings,
      sets,
      profile: { min, max, increment, type },
      target: buildTargetFromLastSets(name, sets, settings),
    };
  }),
};
const path = new URL(
  "../tests/fixtures/android/training-rules.json",
  import.meta.url,
);
const text = JSON.stringify(result, null, 2) + "\n";
if (process.argv.includes("--check")) {
  if (readFileSync(path, "utf8") !== text)
    throw new Error("Native training fixtures differ from the PWA rules.");
} else writeFileSync(path, text);
console.log(
  `${result.cases.length} native training cases match the actual PWA rules.`,
);
