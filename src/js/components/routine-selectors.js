import "../core/globals.js";
import { cleanText } from "../core/utils.js";
import { getRoutines } from "../storage/indexed-db.js";

export async function refreshTemplateDropdowns(selected = null) {
  const templates = await getRoutines();
  const currentWorkout = selected || $("workoutType").value || "Chest / Triceps";
  const currentHistory = $("historyFilter").value || "All";

  $("workoutType").innerHTML = templates.map((template) => `<option value="${cleanText(template.name)}">${cleanText(template.name)}</option>`).join("");
  if (Array.from($("workoutType").options).some((option) => option.value === currentWorkout)) $("workoutType").value = currentWorkout;

  $("historyFilter").innerHTML = `<option value="All">All</option>` + templates.map((template) => `<option value="${cleanText(template.name)}">${cleanText(template.name)}</option>`).join("");
  if (Array.from($("historyFilter").options).some((option) => option.value === currentHistory)) $("historyFilter").value = currentHistory;
}
