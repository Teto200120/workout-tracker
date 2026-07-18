import "../core/globals.js";
import { backupAgeText, daysSinceBackup } from "../application/backup.js";
import { getDisplayName, saveDisplayName } from "../application/display-name.js";
import { countRecentWorkouts as calculateRecentWorkouts } from "../domain/schedule.js";
import { toast } from "../core/utils.js";
import {
  firstValidationMessage,
  validateDisplayName,
} from "../domain/input-guardrails.js";
import { getRoutines, getWorkouts, isDatabaseOpen } from "../storage/indexed-db.js";
import { getBackupMeta, getGoals } from "../storage/local.js";

let displayNameSavedHandler = null;
let displayNameActionsBound = false;
let displayNameSavePending = false;

function setProfileText(id, value) {
  const target = $(id);
  if (target) target.textContent = value;
}

function sortByDateDesc(items) {
  return [...items].sort((a, b) =>
    String(b.date || "").localeCompare(String(a.date || "")) ||
    String(b.createdAt || "").localeCompare(String(a.createdAt || ""))
  );
}

function countRecentWorkouts(workouts) {
  return calculateRecentWorkouts(workouts, new Date());
}

function getProfileBackupSummary() {
  if (typeof getBackupMeta !== "function" || typeof daysSinceBackup !== "function" || typeof backupAgeText !== "function") {
    return "Export or import local data";
  }

  const age = daysSinceBackup(getBackupMeta().lastExportedAt);
  return backupAgeText(age);
}

function displayNameInitial(displayName) {
  return Array.from(displayName || "")[0]?.toLocaleUpperCase() || "?";
}

function setDisplayNameError(message) {
  const input = $("settingsDisplayName");
  const feedback = $("settingsDisplayNameError");
  if (feedback) feedback.textContent = message;
  if (input) {
    input.setAttribute("aria-invalid", "true");
    input.focus();
  }
}

function clearDisplayNameError() {
  $("settingsDisplayName")?.removeAttribute("aria-invalid");
  if ($("settingsDisplayNameError")) $("settingsDisplayNameError").textContent = "";
}

function setDisplayNameEditorOpen(open) {
  const form = $("displayNameEditForm");
  const editButton = $("editDisplayName");
  if (!form || !editButton) return;
  form.classList.toggle("hidden", !open);
  editButton.setAttribute("aria-expanded", String(open));
  if (open) {
    $("settingsDisplayName").value = getDisplayName() || "";
    clearDisplayNameError();
    requestAnimationFrame(() => $("settingsDisplayName")?.focus());
  } else {
    clearDisplayNameError();
    editButton.focus();
  }
}

async function handleDisplayNameSave(event) {
  event.preventDefault();
  if (displayNameSavePending) return;
  const input = $("settingsDisplayName");
  const saveButton = $("saveDisplayName");
  const validation = validateDisplayName(input?.value);
  if (!validation.valid) {
    setDisplayNameError(firstValidationMessage(validation));
    return;
  }

  displayNameSavePending = true;
  if (saveButton) saveButton.disabled = true;
  clearDisplayNameError();
  try {
    const operation = saveDisplayName(input.value);
    const result = await operation.promise;
    if (!result.saved) {
      setDisplayNameError(result.message || "Enter a valid display name.");
      return;
    }
    setDisplayNameEditorOpen(false);
    await displayNameSavedHandler?.(result.displayName);
    toast("Name updated.");
  } catch (error) {
    console.info("Display name update failed.", error);
    setDisplayNameError("Could not save your name. Your current name is unchanged.");
  } finally {
    displayNameSavePending = false;
    if (saveButton) saveButton.disabled = false;
  }
}

export function bindProfileActions({ onDisplayNameSaved } = {}) {
  displayNameSavedHandler = onDisplayNameSaved || displayNameSavedHandler;
  if (displayNameActionsBound) return;
  const editButton = $("editDisplayName");
  const cancelButton = $("cancelDisplayName");
  const form = $("displayNameEditForm");
  const input = $("settingsDisplayName");
  if (!editButton || !cancelButton || !form || !input) return;
  displayNameActionsBound = true;
  editButton.setAttribute("aria-controls", "displayNameEditForm");
  editButton.setAttribute("aria-expanded", "false");
  editButton.addEventListener("click", () => setDisplayNameEditorOpen(true));
  cancelButton.addEventListener("click", () => setDisplayNameEditorOpen(false));
  form.addEventListener("submit", handleDisplayNameSave);
  input.addEventListener("input", () => {
    if (validateDisplayName(input.value).valid) clearDisplayNameError();
  });
}

export async function renderProfile() {
  if (!$("profile") || !isDatabaseOpen()) return;

  const [workoutsRaw, templates] = await Promise.all([
    getWorkouts(),
    getRoutines()
  ]);
  const workouts = sortByDateDesc(workoutsRaw);
  const goals = getGoals();
  const storedWeeklyGoal = Number(goals.weeklyGoal || 4);
  const weeklyGoal = Number.isFinite(storedWeeklyGoal) && storedWeeklyGoal > 0 ? storedWeeklyGoal : 4;
  const workoutsThisWeek = countRecentWorkouts(workouts);
  const weeklyPercent = Math.min(100, Math.round((workoutsThisWeek / weeklyGoal) * 100));
  const workoutLabel = workouts.length === 1 ? "session" : "sessions";
  const routineLabel = templates.length === 1 ? "saved routine" : "saved routines";
  const displayName = getDisplayName();

  setProfileText("profileDisplayName", displayName || "Workout Tracker");
  setProfileText("profileAvatar", displayNameInitial(displayName));
  setProfileText("settingsDisplayNameCurrent", displayName || "Name required");

  setProfileText("profileWeeklyGoal", `${weeklyGoal} workouts/week`);
  setProfileText("profileGoalSummary", `${workoutsThisWeek} logged in the last 7 days.`);
  setProfileText("profileSessionCount", `${workouts.length}`);
  setProfileText("profileSessionSummary", `${workoutLabel} on this device.`);
  setProfileText("profileGoalMeterLabel", workoutsThisWeek >= weeklyGoal ? "Weekly goal complete" : "Weekly goal progress");
  setProfileText("profileGoalMeterCount", `${workoutsThisWeek}/${weeklyGoal}`);
  setProfileText("profileRoutineSummary", `${templates.length} ${routineLabel}`);
  setProfileText("profileBackupSummary", getProfileBackupSummary());
  setProfileText("profileDataSummary", `${workouts.length} ${workoutLabel} on this device`);

  const fill = $("profileGoalMeterFill");
  if (fill) fill.style.width = `${weeklyPercent}%`;
}

