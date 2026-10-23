import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import {
  daysSinceBackup,
  backupAgeText,
} from "../src/js/application/backup.js";
process.env.TZ = "America/New_York";
const now = "2026-03-09T12:00:00Z";
const stamps = [
  null,
  "",
  "invalid",
  now,
  "2026-03-09T13:00:00Z",
  "2026-03-08T12:00:01Z",
  "2026-03-08T12:00:00Z",
  "2026-03-07T12:00:00Z",
  "2026-02-23T12:00:01Z",
  "2026-02-23T12:00:00Z",
  "2026-02-07T12:00:01Z",
  "2026-02-07T12:00:00Z",
  "2026-01-01T00:00:00Z",
  "2026-03-08",
  "2026-03-07T08:00:00",
  "2026-03-08T07:00:00-05:00",
  "2026-03-08T12:00:00+00:00",
];
const fixture = {
  zone: process.env.TZ,
  now,
  cases: stamps.map((lastExportedAt) => {
    const days = daysSinceBackup(lastExportedAt, new Date(now));
    return { lastExportedAt, days, text: backupAgeText(days) };
  }),
};
const path = new URL(
  "../tests/fixtures/android/backup-status.json",
  import.meta.url,
);
const text = JSON.stringify(fixture, null, 2) + "\n";
if (process.argv.includes("--check"))
  assert.equal(readFileSync(path, "utf8"), text);
else writeFileSync(path, text);
console.log("17 native backup-age fixtures match the actual PWA helpers.");
