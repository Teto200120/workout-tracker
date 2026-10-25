import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { format } from "prettier";
import {
  renderSettings,
  resetAppSettings,
} from "../src/js/screens/settings.js";
import {
  SETTINGS_KEY,
  GOALS_KEY,
  DEFAULT_APP_SETTINGS,
} from "../src/js/core/constants.js";

const previous = {
  document: globalThis.document,
  localStorage: globalThis.localStorage,
  confirm: globalThis.confirm,
  setTimeout: globalThis.setTimeout,
};
const cases = [];
try {
  globalThis.setTimeout = () => 0;
  for (const name of ["Reset user", "Joël & <Name>", null]) {
    for (const mode of ["confirm", "cancel", "stale", "failure"]) {
      const initial = {
        ...structuredClone(DEFAULT_APP_SETTINGS),
        displayName: name,
        defaultWeightJump: 2.5,
        rpeAware: false,
        haptics: false,
        animations: false,
        extension: { reset: true },
      };
      const values = new Map([
        [SETTINGS_KEY, JSON.stringify(initial)],
        [GOALS_KEY, JSON.stringify({ weeklyGoal: 6, extension: "keep" })],
        ["unrelated", "keep"],
      ]);
      let fail = false;
      globalThis.localStorage = {
        getItem: (key) => values.get(key) ?? null,
        setItem: (key, value) => {
          if (fail) throw new Error("Synthetic storage failure");
          values.set(key, value);
        },
        removeItem: (key) => values.delete(key),
      };
      const elements = new Map();
      let rendered = false;
      globalThis.document = {
        body: { classList: { toggle() {} } },
        querySelectorAll: () => [],
        getElementById: (id) => {
          if (rendered && id === "settingsSchedule") return null;
          if (!elements.has(id))
            elements.set(id, {
              innerHTML: "",
              classList: { add() {}, remove() {} },
              dataset: {},
            });
          return elements.get(id);
        },
      };
      await renderSettings({ routines: [] });
      rendered = true;
      globalThis.confirm = () => mode !== "cancel";
      if (mode === "stale")
        values.set(
          SETTINGS_KEY,
          JSON.stringify({ ...initial, defaultWeightJump: 10 }),
        );
      fail = mode === "failure";
      // Expected failures are deliberately exercised; suppress their console.info only here.
      const info = console.info;
      console.info = () => {};
      let committed;
      try {
        committed = await resetAppSettings();
      } finally {
        console.info = info;
      }
      cases.push({
        name,
        mode,
        initial,
        committed,
        settings: JSON.parse(values.get(SETTINGS_KEY)),
        goals: JSON.parse(values.get(GOALS_KEY)),
      });
      assert.equal(values.get("unrelated"), "keep");
    }
  }
} finally {
  for (const [key, value] of Object.entries(previous)) {
    if (value === undefined) delete globalThis[key];
    else globalThis[key] = value;
  }
}
const path = new URL(
  "../tests/fixtures/android/data-actions.json",
  import.meta.url,
);
const text = await format(JSON.stringify({ cases }), { parser: "json" });
if (process.argv.includes("--check"))
  assert.equal(readFileSync(path, "utf8").replaceAll("\r\n", "\n"), text);
else writeFileSync(path, text);
console.log(`${cases.length} native reset cases match the actual PWA action.`);
