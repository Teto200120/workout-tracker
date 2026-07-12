import js from "@eslint/js";
import globals from "globals";

const transitionalAppGlobals = Object.fromEntries(
  `$ all closeTodayReview renderAll showTodayView stopTodayActiveElapsedTimer
  switchScreen syncTodayFloatingCta`
    .split(/\s+/)
    .map((name) => [name, "writable"]),
);

export default [
  {
    ignores: [
      "node_modules/**",
      "playwright-report/**",
      "test-results/**",
      "coverage/**",
      "legacy/**",
    ],
  },
  js.configs.recommended,
  {
    files: ["src/js/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: { ...globals.browser, ...transitionalAppGlobals },
    },
    rules: {
      "no-constant-condition": ["error", { checkLoops: false }],
      "no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
    },
  },
  {
    files: ["service-worker.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "script",
      globals: globals.serviceworker,
    },
  },
  {
    files: ["*.config.js", "tests/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: globals.nodeBuiltin,
    },
  },
];
