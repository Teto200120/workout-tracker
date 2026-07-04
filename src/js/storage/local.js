import "../core/globals.js";

function getGoals() {
  try {
    return JSON.parse(localStorage.getItem("hector_workout_goals_v1")) || { weeklyGoal: 4, targetWeight: "" };
  } catch {
    return { weeklyGoal: 4, targetWeight: "" };
  }
}

function getDraft() {
  try {
    return JSON.parse(localStorage.getItem("hector_workout_draft_v1"));
  } catch {
    return null;
  }
}

function saveDraftSilently() {
  if (!db) return;
  const draft = collectWorkout({ includeEmptySets: true });
  draft.editingWorkoutId = editingWorkoutId;
  draft.activeExerciseIndex = getActiveExerciseIndex();
  draft.savedAt = new Date().toISOString();
  localStorage.setItem("hector_workout_draft_v1", JSON.stringify(draft));
}

function clearDraftStorage(showMessage = true) {
  localStorage.removeItem("hector_workout_draft_v1");
  stopTodayActiveElapsedTimer();
  if (showMessage) toast("Draft cleared.");
}

function saveGoalsToStorage() {
  const weeklyGoal = Number($("weeklyGoal").value || 4);
  const targetWeight = $("targetWeight").value ? Number($("targetWeight").value) : "";
  localStorage.setItem("hector_workout_goals_v1", JSON.stringify({ weeklyGoal, targetWeight }));
  toast("Goals saved.");
  renderAll();
}

Object.assign(globalThis, { getGoals, getDraft, saveDraftSilently, clearDraftStorage, saveGoalsToStorage });
