import assert from "node:assert/strict";
import test from "node:test";
import { APP_RELEASE } from "../../src/js/core/release.js";

test("release marker uses an initial pre-1.0 semantic version", () => {
  assert.match(APP_RELEASE, /^0\.\d+\.\d+$/u);
});
