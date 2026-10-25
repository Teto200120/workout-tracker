import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { format } from "prettier";
import { renderDashboard } from "../src/js/screens/progress.js";

process.env.TZ = "America/New_York";
const NativeDate = Date;
let today;
class FixedDate extends NativeDate {
  constructor(...args) {
    super(...(args.length ? args : [`${today}T12:00:00-04:00`]));
  }
  toLocaleDateString(_locale, options) {
    return super.toLocaleDateString("en-US", options);
  }
}
const exercise = (name, weight, reps = 5, extras = {}) => ({
  name,
  notes: "",
  sets: [
    {
      weight: String(weight),
      reps: String(reps),
      warmup: false,
      done: false,
      rpe: "",
      ...extras,
    },
  ],
});
const workout = (id, date, rows = [], options = {}) => ({
  id,
  date,
  type: "Push",
  exercises: rows,
  startTime: "23:30",
  endTime: "00:45",
  createdAt: `${date}T12:00:00Z`,
  ...options,
});
const cases = [
  { name: "empty", workouts: [] },
  {
    name: "first entry does not create a PR insight",
    workouts: [workout(0, "2026-10-23", [exercise("Bench", 100)])],
  },
  {
    name: "recent PR before trend and same-type volume",
    workouts: [
      workout(0, "2026-10-01", [exercise("Bench", 100)]),
      workout("0", "2026-10-23", [exercise("Bench", 150)], {
        durationMinutes: 0,
      }),
    ],
  },
  {
    name: "PR boundary includes sixty calendar days",
    workouts: [
      workout(1, "2026-01-01", [exercise("Bench", 100)]),
      workout(2, "2026-08-24", [exercise("Bench", 110)]),
      workout(3, "2026-08-23", [exercise("Squat", 150)]),
      workout(4, "2026-01-01", [exercise("Squat", 100)]),
    ],
  },
  {
    name: "old PR omitted, upward trend retained",
    workouts: [
      workout(1, "2026-01-01", [exercise("Bench", 100)]),
      workout(2, "2026-08-23", [exercise("Bench", 120)]),
    ],
  },
  {
    name: "latest seven entries exclude old trend endpoint",
    workouts: Array.from({ length: 9 }, (_, i) =>
      workout(i, `2026-10-${String(i + 10).padStart(2, "0")}`, [
        exercise("Bench", i === 0 ? 1000 : 100 + i),
      ]),
    ),
  },
  {
    name: "PR dedup after two candidate slots and six detail insights",
    workouts: [
      workout(1, "2026-10-01", [
        exercise("Bench", 100),
        exercise("Squat", 150),
      ]),
      workout(2, "2026-10-21", [
        exercise("Bench", 110),
        exercise("Squat", 175),
      ]),
      workout(3, "2026-10-23", [
        exercise("Bench", 120),
        exercise("Squat", 200),
      ]),
    ],
  },
  {
    name: "same exercise PRs deduplicate without refilling",
    workouts: [
      workout(1, "2026-10-01", [exercise("Bench", 100)]),
      workout(2, "2026-10-22", [exercise("Bench", 120)]),
      workout(3, "2026-10-23", [exercise("Bench", 130)]),
    ],
  },
  {
    name: "same date PR ordering by score",
    workouts: [
      workout(1, "2026-10-01", [exercise("A", 100), exercise("B", 100)]),
      workout(2, "2026-10-23", [exercise("A", 110), exercise("B", 200)]),
    ],
  },
  {
    name: "declining load but estimated gain retains zero or negative trend",
    workouts: [
      workout(1, "2026-10-01", [exercise("Bench", 100, 1)]),
      workout(2, "2026-10-23", [exercise("Bench", 95, 12)]),
    ],
  },
  {
    name: "flat trend and volume below threshold omitted",
    workouts: [
      workout(1, "2026-10-01", [exercise("Bench", 100)]),
      workout(2, "2026-10-23", [exercise("Bench", 100)], {
        durationMinutes: 60,
      }),
    ],
  },
  ...[249, 250, -249, -250].map((delta) => ({
    name: `volume threshold ${delta}`,
    workouts: [
      workout(1, "2026-10-01", [exercise("Zero reps don't set PRs", 1000, 1)]),
      workout(2, "2026-10-23", [exercise("Other exercise", 1000 + delta, 1)]),
    ],
  })),
  {
    name: "volume compares exact type and skips other routines",
    workouts: [
      workout(1, "2026-10-01", [exercise("Bench", 100)]),
      workout(2, "2026-10-22", [exercise("Bench", 500)], { type: "push" }),
      workout(3, "2026-10-23", [exercise("Bench", 50)]),
    ],
  },
  {
    name: "warmups bodyweight incomplete sets repeated names and escaping",
    workouts: [
      workout(1, "2026-10-01", [
        exercise("A & <B>", 100),
        exercise("Bodyweight", 0),
      ]),
      workout(2, "2026-10-23", [
        exercise("A & <B>", 120),
        exercise("A & <B>", 150),
        exercise("Warmup", 1000, 10, { warmup: true }),
        exercise("Bodyweight", 0),
      ]),
    ],
  },
  {
    name: "three newest preserve stable timestamp ties and typed IDs",
    workouts: [
      workout(0, "2026-10-23", [], { durationMinutes: 0 }),
      workout(2, "2026-10-23", [], { durationMinutes: 30 }),
      workout("0", "2026-10-23", [], { durationMinutes: 60 }),
      workout("old", "2026-10-01", [], { durationMinutes: 90 }),
    ],
  },
  {
    name: "future entries follow the existing PWA cutoff without a new upper bound",
    workouts: [
      workout(1, "2026-10-01", [exercise("Bench", 100)]),
      workout(2, "2026-10-24", [exercise("Bench", 120)]),
    ],
  },
];
cases.push(
  {
    name: "binary decimal rounding for PR and signed load trend",
    workouts: [
      workout(1, "2026-10-01", [
        exercise("Small lift", 1, 15),
        exercise("Load delta", 1, 1),
      ]),
      workout(2, "2026-10-23", [
        exercise("Small lift", 1.7, 15),
        exercise("Load delta", 3.55, 1),
      ]),
    ],
  },
  {
    name: "case and accent distinct names keep PWA English tie order",
    workouts: [
      workout(
        1,
        "2026-10-01",
        ["Bench", "bench", "Á Press", "Z Press"].map((name) =>
          exercise(name, 100),
        ),
      ),
      workout(
        2,
        "2026-10-23",
        ["Bench", "bench", "Á Press", "Z Press"].map((name) =>
          exercise(name, 110),
        ),
      ),
    ],
  },
  ...[57, 60].map((weight) => ({
    name: `estimated gain trend boundary at load ${weight}`,
    workouts: [
      workout(1, "2026-01-01", [exercise("Bench", weight, 5)]),
      workout(2, "2026-01-02", [exercise("Bench", weight, 6)]),
    ],
  })),
  {
    name: "calendar cutoff crosses spring DST",
    today: "2026-03-09",
    workouts: [
      workout(1, "2026-01-01", [exercise("Bench", 100)]),
      workout(2, "2026-01-08", [exercise("Bench", 110)]),
    ],
  },
);
const decode = (text) =>
  text
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", '"')
    .replaceAll("&#039;", "'")
    .replaceAll("&amp;", "&");
const insights = (html) =>
  Array.from(
    html.matchAll(
      /class="insight-item (pr|trend|volume)">\s*<strong>(.*?)<\/strong>\s*<p[^>]*>(.*?)<\/p>/gs,
    ),
    (m) => ({ type: m[1], title: decode(m[2]), meta: decode(m[3]) }),
  );
const previousDocument = globalThis.document;
const previousStorage = globalThis.localStorage;
try {
  globalThis.Date = FixedDate;
  globalThis.localStorage = { getItem: () => null };
  for (const item of cases) {
    today = item.today ?? "2026-10-23";
    item.today = today;
    const elements = new Map();
    globalThis.document = {
      getElementById: (id) => {
        if (!elements.has(id))
          elements.set(id, {
            innerHTML: "",
            textContent: "",
            value: "",
            dataset: {},
            options: [],
          });
        return elements.get(id);
      },
    };
    await renderDashboard(
      { workouts: item.workouts },
      { destination: "stats" },
    );
    item.preview = insights(elements.get("strengthSnapshot").innerHTML);
    item.recent = Array.from(
      elements
        .get("recentSessionsPreview")
        .innerHTML.matchAll(
          /class="stats-preview-row">\s*<div>\s*<strong>(.*?)<\/strong>\s*<p[^>]*>(.*?)<\/p>\s*<\/div>\s*<span>(.*?)<\/span>/gs,
        ),
      (m) => ({ type: decode(m[1]), summary: decode(m[2]), duration: m[3] }),
    );
    await renderDashboard(
      { workouts: item.workouts },
      { destination: "statsStrength" },
    );
    item.detail = insights(elements.get("strengthSnapshotDetail").innerHTML);
  }
} finally {
  globalThis.Date = NativeDate;
  if (previousDocument === undefined) delete globalThis.document;
  else globalThis.document = previousDocument;
  if (previousStorage === undefined) delete globalThis.localStorage;
  else globalThis.localStorage = previousStorage;
}
const path = new URL(
  "../tests/fixtures/android/insights.json",
  import.meta.url,
);
const text = await format(JSON.stringify({ cases }), { parser: "json" });
if (process.argv.includes("--check"))
  assert.equal(readFileSync(path, "utf8").replaceAll("\r\n", "\n"), text);
else writeFileSync(path, text);
console.log(
  `${cases.length} native insight cases match the actual PWA renderer.`,
);
