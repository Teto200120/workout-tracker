import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { format } from "prettier";
import { renderHistory } from "../src/js/screens/history.js";
import { decodeRecordId } from "../src/js/domain/record-id.js";

// Exercise the actual renderer with only its three DOM destinations stubbed.
// No duplicate JavaScript implementation of the native matching rules.
const baseline = JSON.parse(
  readFileSync(
    new URL("../tests/fixtures/android/baseline.json", import.meta.url),
    "utf8",
  ),
).backup.workouts[0];
const workout = (
  id,
  type,
  names,
  date,
  createdAt = `${date}T12:00:00.000Z`,
) => ({
  ...structuredClone(baseline),
  id,
  type,
  date,
  createdAt,
  notes: "Only in notes: triceps",
  tags: ["calves"],
  exercises: names.map((name) => ({
    ...structuredClone(baseline.exercises[0]),
    name,
  })),
});
const workouts = [
  workout(0, "Upper", ["Bench Press", "Row"], "2026-10-20"),
  workout(
    "0",
    "Lower",
    ["Squat", "bench press"],
    "2026-10-20",
    "2026-10-20T13:00:00.000Z",
  ),
  workout(12, "upper", ["Bench Press (Barbell)"], "2026-10-21"),
  workout("12", "Upper", ["Deadlift"], "2026-10-19"),
  workout(
    "unicode",
    "Imported / deleted routine",
    ["İSTANBUL Press", "ΟΣ Row", "INCLINE Press"],
    "2026-10-22",
  ),
  workout("empty", "Custom", [], "2026-10-23"),
  workout("tie-a", "Upper", ["Row", "Row"], "2026-10-20"),
  workout("tie-b", "Upper", ["Deadlift"], "2026-10-20"),
];
const selections = [
  ["all records, newest first and stable ties", null, ""],
  ["exact routine", "Upper", ""],
  ["routine case stays distinct", "upper", ""],
  ["missing routine", "Missing", ""],
  ["historical routine name", "Imported / deleted routine", ""],
  ["case-insensitive substring", null, "BENCH"],
  ["trim search", null, "  bEnCh  "],
  ["empty search", null, " \t\n "],
  [
    "ECMAScript nonbreaking and BOM whitespace",
    null,
    "\uFEFF\u00a0BENCH\u3000",
  ],
  ["combined matches", "Upper", "bench"],
  ["combined no match", "Lower", "deadlift"],
  ["match any exercise", "Upper", "row"],
  ["do not duplicate repeated exercise rows", null, "row"],
  ["notes are not exercise names", null, "triceps"],
  ["tags are not exercise names", null, "calves"],
  ["routine names are not exercise names", null, "lower"],
  ["do not resolve catalog aliases", null, "Barbell Bench Press"],
  ["punctuation is literal", null, "(BARBELL)"],
  ["locale-independent Latin I", null, "incline"],
  ["Unicode expanding lowercase", null, "i\u0307stanbul"],
  ["Unicode contextual lowercase", null, "ος"],
  ["no matches", null, "not an exercise"],
];
const previousDocument = globalThis.document;
const cases = [];
try {
  for (const [name, routine, search] of selections) {
    const list = { innerHTML: "" };
    const elements = {
      historyFilter: { value: routine ?? "All" },
      exerciseSearch: { value: search },
      historyList: list,
    };
    globalThis.document = { getElementById: (id) => elements[id] };
    await renderHistory({ workouts });
    const ids = Array.from(
      list.innerHTML.matchAll(
        /data-history-action="edit" data-workout-id="([^"]*)" data-workout-id-type="([^"]*)"/g,
      ),
      (match) => decodeRecordId(match[1], match[2]),
    );
    cases.push({ name, routine, search, ids });
  }
} finally {
  if (previousDocument === undefined) delete globalThis.document;
  else globalThis.document = previousDocument;
}
const path = new URL(
  "../tests/fixtures/android/history-filters.json",
  import.meta.url,
);
const text = await format(JSON.stringify({ workouts, cases }), {
  parser: "json",
});
if (process.argv.includes("--check"))
  assert.equal(readFileSync(path, "utf8").replaceAll("\r\n", "\n"), text);
else writeFileSync(path, text);
console.log(
  `${cases.length} native History cases match the actual PWA renderer.`,
);
