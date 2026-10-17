import { expect, test } from "@playwright/test";
import { loadApp } from "../helpers/app.js";

test("progress rows pair each metric with its own winning set", async ({
  page,
}) => {
  await loadApp(page);
  const render = async (metric) =>
    page.evaluate(async (selectedMetric) => {
      const { buildExerciseStats } =
        await import("/src/js/domain/workout-metrics.js");
      const { renderExerciseProgress } =
        await import("/src/js/screens/progress.js");
      const stats = buildExerciseStats([
        {
          date: "2026-07-01",
          type: "Push",
          exercises: [
            {
              name: "Bench",
              sets: [
                { weight: 200, reps: 1 },
                { weight: 180, reps: 5 },
                { weight: 100, reps: 20 },
              ],
            },
          ],
        },
      ]);
      const exercise = globalThis.document.getElementById("progressExercise");
      exercise.replaceChildren(new globalThis.Option("Bench", "Bench"));
      exercise.value = "Bench";
      globalThis.document.getElementById("progressMetric").value =
        selectedMetric;
      renderExerciseProgress(stats);
      return globalThis.document.getElementById("exerciseProgress").textContent;
    }, metric);
  expect(await render("bestWeight")).toContain("Best Load: 200.0 lb · 200 × 1");
  expect(await render("bestVolume")).toContain(
    "Best Set Volume: 2000 · 100 × 20",
  );
  expect(await render("estimated1rm")).toContain(
    "Estimated 1RM: 210.0 lb · 180 × 5",
  );
});
