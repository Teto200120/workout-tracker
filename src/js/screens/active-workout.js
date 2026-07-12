import "../core/globals.js";
import {
  iconCheck,
  iconChevron,
  iconChevronRight,
  iconGrip,
  iconInfo,
  iconMinus,
  iconPlayOutline,
  iconPlus
} from "../components/icons.js";
import {
  cleanText,
  clamp,
  dateLabel,
  haptic,
  id,
  motionBehavior,
  replayAnimation,
  scrollInputIntoView,
  timeNow,
  today,
  toast
} from "../core/utils.js";
import {
  buildTargetFromLastSets as calculateTargetFromLastSets,
  getExerciseProfile as resolveExerciseProfile,
  inferMuscleTag,
  workSetsOnly
} from "../domain/training-rules.js";
import {
  buildCompletionSummary,
  durationLabel,
  getBestSet,
  hasSaveableWorkoutContent,
  workoutDurationMinutes
} from "../domain/workout-metrics.js";
import {
  getRoutines,
  getWorkouts,
  isDatabaseOpen,
  saveWorkoutRecord
} from "../storage/indexed-db.js";
import { getAppSettings, removeDraft, setDraft } from "../storage/local.js";

let sessionElapsedInterval = null;
let exerciseDragState = null;
let activeExerciseDetailEl = null;
let exerciseDetailTab = "log";
let exerciseDetailRenderToken = 0;
let exerciseFocusScrollToken = 0;
let editingWorkoutId = null;
let completionWorkout = null;
const completionSelectedTags = new Set();

export function getEditingWorkoutId() {
  return editingWorkoutId;
}

export function setEditingWorkoutId(workoutId) {
  editingWorkoutId = workoutId || null;
}

const DEFAULT_WORKOUT_TAGS = [
  "Good session",
  "Low energy",
  "Bad sleep",
  "Sore",
  "Felt strong",
  "Felt weak",
  "Great pump",
  "Rushed workout",
  "Shoulder discomfort",
  "Elbow discomfort",
  "Knee discomfort",
  "Soccer fatigue"
];

function updateSessionTitle() {
  const title = $("sessionRoutineTitle");
  if (title) title.textContent = $("workoutType")?.value || "Workout";
  updateSessionElapsedTimer();
}

function showSessionView() {
  closeTodayReview();
  stopTodayActiveElapsedTimer();
  $("todayView")?.classList.add("hidden");
  $("sessionView")?.classList.remove("hidden");
  syncTodayFloatingCta();
  updateSessionTitle();
  startSessionElapsedTimer();
  syncSessionUi();
  replayAnimation($("sessionView"), "settle-in", 260);
  window.scrollTo({ top: 0, behavior: motionBehavior() });
}

function formatElapsedClock(totalSeconds) {
  const safeSeconds = Math.max(0, Math.floor(totalSeconds || 0));
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const seconds = safeSeconds % 60;
  if (hours) return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function timeToMinutes(value) {
  if (!value || !value.includes(":")) return null;
  const [hour, minute] = value.split(":").map(Number);
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null;
  return hour * 60 + minute;
}

function elapsedSecondsFromClock(startTime, endTime = "") {
  const startMinutes = timeToMinutes(startTime || "");
  if (startMinutes === null) return 0;
  const endMinutes = timeToMinutes(endTime || "");
  const now = new Date();
  let currentMinutes = endMinutes === null ? now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60 : endMinutes;
  if (currentMinutes < startMinutes) currentMinutes += 24 * 60;
  return Math.round((currentMinutes - startMinutes) * 60);
}

function getSessionElapsedSeconds() {
  return elapsedSecondsFromClock($("startTime")?.value || "", $("endTime")?.value || "");
}

function getDraftElapsedSeconds(draft) {
  return elapsedSecondsFromClock(draft?.startTime || "", draft?.endTime || "");
}

function updateSessionElapsedTimer() {
  const target = $("sessionElapsedTimer");
  if (target) target.textContent = formatElapsedClock(getSessionElapsedSeconds());
}

function startSessionElapsedTimer() {
  updateSessionElapsedTimer();
  if (sessionElapsedInterval) clearInterval(sessionElapsedInterval);
  sessionElapsedInterval = setInterval(updateSessionElapsedTimer, 1000);
}

function stopSessionElapsedTimer() {
  if (sessionElapsedInterval) clearInterval(sessionElapsedInterval);
  sessionElapsedInterval = null;
}

function getWorkSetRows(exerciseEl) {
  return Array.from(exerciseEl?.querySelectorAll(".set-row") || [])
    .filter((row) => !row.querySelector(".set-warmup")?.checked);
}

function getDoneWorkSetRows(exerciseEl) {
  return getWorkSetRows(exerciseEl).filter((row) => row.querySelector(".set-done")?.checked);
}

function isExerciseComplete(exerciseEl) {
  const workSets = getWorkSetRows(exerciseEl);
  return workSets.length > 0 && workSets.every((row) => row.querySelector(".set-done")?.checked);
}

function getActiveExercise() {
  const exercises = all(".exercise");
  return exercises.find((exercise) => !exercise.classList.contains("collapsed"))
    || exercises.find((exercise) => !isExerciseComplete(exercise))
    || exercises[0]
    || null;
}

function getCurrentSetRow(exerciseEl) {
  const workSets = getWorkSetRows(exerciseEl);
  return workSets.find((row) => !row.querySelector(".set-done")?.checked) || null;
}

function getSessionSetStats() {
  const exercises = all(".exercise");
  const workSets = exercises.flatMap((exercise) => getWorkSetRows(exercise));
  const completed = workSets.filter((row) => row.querySelector(".set-done")?.checked).length;
  return { completed, total: workSets.length };
}

function updateSessionProgress() {
  const { completed, total } = getSessionSetStats();
  const count = $("sessionProgressCount");
  const fill = $("sessionProgressFill");
  if (count) count.textContent = `${completed}/${total} sets`;
  if (fill) fill.style.width = `${total ? Math.min(100, Math.round((completed / total) * 100)) : 0}%`;
}

function setControlText(exerciseEl, selector, value) {
  const targets = Array.from(exerciseEl?.querySelectorAll(selector) || []);
  targets.forEach((target) => {
    if (target instanceof HTMLInputElement) {
      if (document.activeElement !== target) target.value = value;
    } else {
      target.textContent = value;
    }
  });
}

function sanitizeControlInputValue(field, value) {
  let next = String(value ?? "").replace(",", ".").trim();
  if (field === "reps") return next.replace(/[^\d]/g, "").slice(0, 3);
  next = next.replace(/[^\d.]/g, "");
  const parts = next.split(".");
  if (parts.length > 1) next = `${parts.shift()}.${parts.join("")}`;
  const decimals = field === "rpe" ? 1 : 2;
  if (next.includes(".")) {
    const [whole, fraction = ""] = next.split(".");
    next = `${whole}.${fraction.slice(0, decimals)}`;
  }
  return next;
}

function formatCommittedControlValue(field, value) {
  const clean = sanitizeControlInputValue(field, value);
  if (clean === "" || clean === ".") return "";
  let number = Number(clean);
  if (!Number.isFinite(number)) return "";
  if (field === "reps") return String(Math.max(0, Math.round(number)));
  if (field === "rpe") {
    number = Math.min(10, Math.max(1, Math.round(number * 2) / 2));
    return Number.isInteger(number) ? String(number) : number.toFixed(1);
  }
  number = Math.max(0, number);
  return Number.isInteger(number) ? String(number) : String(Number(number.toFixed(2)));
}

function getSetFieldInput(row, field) {
  return row?.querySelector(`.set-${field}`) || null;
}

function writeCurrentSetValueFromControl(exerciseEl, field, rawValue, commit = false) {
  const row = getCurrentSetRow(exerciseEl);
  if (!row) return "";
  const value = commit ? formatCommittedControlValue(field, rawValue) : sanitizeControlInputValue(field, rawValue);
  const hiddenInput = getSetFieldInput(row, field);
  if (hiddenInput) hiddenInput.value = value;
  return value;
}

function bindControlValueInput(input, getExerciseEl) {
  input.addEventListener("input", () => {
    const exerciseEl = getExerciseEl();
    if (!exerciseEl) return;
    const value = writeCurrentSetValueFromControl(exerciseEl, input.dataset.field, input.value, false);
    input.value = value;
    updateExerciseSummary(exerciseEl);
    saveDraftSilently();
  });

  input.addEventListener("blur", () => {
    const exerciseEl = getExerciseEl();
    if (!exerciseEl) return;
    const value = writeCurrentSetValueFromControl(exerciseEl, input.dataset.field, input.value, true);
    input.value = value;
    updateExerciseSummary(exerciseEl);
    saveDraftSilently();
    if (isExerciseDetailOpen()) setTimeout(renderExerciseDetailView, 80);
  });
}

function getNumericInputValue(input, fallback = 0) {
  if (!input || input.value.trim() === "") return fallback;
  const value = Number(input?.value);
  return Number.isFinite(value) ? value : fallback;
}

function previousWeightForSet(exerciseEl, currentRow) {
  const workSets = getWorkSetRows(exerciseEl);
  const currentIndex = workSets.indexOf(currentRow);
  const previous = workSets
    .slice(0, Math.max(0, currentIndex))
    .reverse()
    .find((row) => row.querySelector(".set-weight")?.value);
  return previous?.querySelector(".set-weight")?.value || "";
}

function ensureCurrentSetDefaults(exerciseEl, row) {
  if (!row) return;
  const name = exerciseEl.querySelector(".exercise-name")?.value || "";
  const profile = getExerciseProfile(name);
  const weightInput = row.querySelector(".set-weight");
  const repsInput = row.querySelector(".set-reps");
  if (weightInput && !weightInput.value) weightInput.value = previousWeightForSet(exerciseEl, row);
  if (repsInput && !repsInput.value) repsInput.value = String(profile.min || 8);
}

function updateCurrentSetPanel(exerciseEl) {
  if (!exerciseEl) return;
  const workSets = getWorkSetRows(exerciseEl);
  const currentRow = getCurrentSetRow(exerciseEl);
  const currentIndex = currentRow ? workSets.indexOf(currentRow) : workSets.length - 1;
  const safeIndex = Math.max(0, currentIndex);
  const label = exerciseEl.querySelector(".current-set-label");

  if (!workSets.length) {
    if (label) label.innerHTML = `Current Set<strong>No work sets</strong>`;
    setControlText(exerciseEl, ".weight-value", "-");
    setControlText(exerciseEl, ".reps-value", "-");
    setControlText(exerciseEl, ".rpe-value", "-");
    return;
  }

  if (!currentRow) {
    if (label) label.innerHTML = `Current Set<strong>Complete</strong>`;
    return;
  }

  const profile = getExerciseProfile(exerciseEl.querySelector(".exercise-name")?.value || "");
  const weight = currentRow.querySelector(".set-weight")?.value || previousWeightForSet(exerciseEl, currentRow) || "0";
  const reps = currentRow.querySelector(".set-reps")?.value || String(profile.min || 8);
  const rpe = currentRow.querySelector(".set-rpe")?.value || "-";
  if (label) label.innerHTML = `Current Set<strong>Set ${safeIndex + 1} of ${workSets.length}</strong>`;
  setControlText(exerciseEl, ".weight-value", weight);
  setControlText(exerciseEl, ".reps-value", reps);
  setControlText(exerciseEl, ".rpe-value", rpe);
  const completeButton = exerciseEl.querySelector(".complete-set-button");
  if (completeButton) completeButton.innerHTML = `${iconCheck()} Complete Set ${safeIndex + 1}`;
}

function adjustCurrentSetValue(exerciseEl, field, delta) {
  const row = getCurrentSetRow(exerciseEl);
  if (!row) return;
  ensureCurrentSetDefaults(exerciseEl, row);
  const input = row.querySelector(`.set-${field}`);
  if (!input) return;
  const settings = getAppSettings();
  const step = field === "weight" ? Number(settings.defaultWeightJump || 5) : field === "rpe" ? 0.5 : 1;
  const min = field === "rpe" ? 1 : 0;
  const max = field === "rpe" ? 10 : 999;
  const current = getNumericInputValue(input, field === "rpe" ? 8 : 0);
  const next = Math.min(max, Math.max(min, current + delta * step));
  input.value = field === "rpe" && !Number.isInteger(next) ? next.toFixed(1) : String(next);
  updateExerciseSummary(exerciseEl);
  saveDraftSilently();
  haptic(8);
}

function completedSetChips(exerciseEl) {
  const workSets = getWorkSetRows(exerciseEl);
  const chips = workSets
    .map((row, index) => ({ row, index }))
    .filter((item) => item.row.querySelector(".set-done")?.checked)
    .map((item) => {
      const reps = item.row.querySelector(".set-reps")?.value || "-";
      return `<span class="completed-set-chip">Set ${item.index + 1}: ${cleanText(reps)} reps</span>`;
    });
  return chips.join("");
}

function renumberExerciseCards() {
  all(".exercise").forEach((exercise, index) => {
    const status = exercise.querySelector(".exercise-status");
    if (!status) return;
    status.innerHTML = isExerciseComplete(exercise) ? iconCheck() : String(index + 1);
  });
}

function syncSessionUi() {
  renumberExerciseCards();
  updateSessionProgress();
  all(".exercise").forEach(updateCurrentSetPanel);
  updateSessionPrimaryAction();
  if (isExerciseDetailOpen() && !document.activeElement?.closest("#exerciseDetailView .control-value-input")) {
    renderExerciseDetailView();
  }
}

function updateSessionPrimaryAction() {
  const button = $("saveWorkout");
  const undo = $("sessionUndoSet");
  const active = getActiveExercise();
  const { completed, total } = getSessionSetStats();
  if (undo) undo.disabled = !all(".set-done").some((input) => input.checked);
  if (!button) return;
  if (!active || !total) {
    button.textContent = "Log Set";
    button.disabled = !active || !total;
    return;
  }
  button.disabled = false;
  if (total > 0 && completed >= total) {
    button.textContent = "Finish Workout";
    return;
  }
  if (isExerciseComplete(active)) {
    button.textContent = "Next Exercise";
    return;
  }
  const workSets = getWorkSetRows(active);
  const currentRow = getCurrentSetRow(active);
  const currentIndex = currentRow ? workSets.indexOf(currentRow) : 0;
  button.textContent = `Complete Set ${currentIndex + 1}`;
}

function completeCurrentSet(exerciseEl) {
  const row = getCurrentSetRow(exerciseEl);
  if (!row) return false;
  ensureCurrentSetDefaults(exerciseEl, row);
  const done = row.querySelector(".set-done");
  if (done) done.checked = true;
  replayAnimation(row, "set-just-done", 460);
  haptic([18, 28, 18]);
  updateExerciseSummary(exerciseEl);
  saveDraftSilently();
  return true;
}

function undoLastCompletedSet() {
  const exercises = all(".exercise");
  const active = getActiveExercise();
  const ordered = active ? [active, ...exercises.filter((exercise) => exercise !== active).reverse()] : exercises.reverse();
  for (const exercise of ordered) {
    const doneRows = getDoneWorkSetRows(exercise);
    const row = doneRows[doneRows.length - 1];
    if (!row) continue;
    const done = row.querySelector(".set-done");
    if (done) done.checked = false;
    openExercise(exercise, false);
    updateExerciseSummary(exercise);
    saveDraftSilently();
    haptic(16);
    return;
  }
}

function handleSessionPrimaryAction() {
  const active = getActiveExercise();
  const { completed, total } = getSessionSetStats();
  if (!active) return;
  if (total > 0 && completed >= total) {
    saveWorkout();
    return;
  }
  if (isExerciseComplete(active)) {
    finishAndNextExercise(active);
    return;
  }
  completeCurrentSet(active);
}

function isExerciseDetailOpen() {
  return !$("exerciseDetailView")?.classList.contains("hidden");
}

function getExerciseSetProgress(exerciseEl) {
  const workSets = getWorkSetRows(exerciseEl);
  const doneSets = getDoneWorkSetRows(exerciseEl);
  return { done: doneSets.length, total: workSets.length };
}

function getCurrentSetMeta(exerciseEl) {
  const workSets = getWorkSetRows(exerciseEl);
  const row = getCurrentSetRow(exerciseEl);
  const index = row ? workSets.indexOf(row) : Math.max(0, workSets.length - 1);
  return { row, workSets, index: Math.max(0, index), total: workSets.length };
}

function getCurrentSetDisplayValues(exerciseEl) {
  const { row } = getCurrentSetMeta(exerciseEl);
  if (!row) return { weight: "-", reps: "-", rpe: "-" };
  const profile = getExerciseProfile(exerciseEl.querySelector(".exercise-name")?.value || "");
  return {
    weight: row.querySelector(".set-weight")?.value || previousWeightForSet(exerciseEl, row) || "0",
    reps: row.querySelector(".set-reps")?.value || String(profile.min || 8),
    rpe: row.querySelector(".set-rpe")?.value || ""
  };
}

function controlInputHtml(field, value, label) {
  const inputMode = field === "reps" ? "numeric" : "decimal";
  const placeholder = field === "rpe" ? "-" : "0";
  return `
    <div class="live-control-row ${field === "rpe" ? "live-rpe-row" : ""}">
      <span class="live-control-label">${cleanText(label)}</span>
      <button class="control-stepper" type="button" data-field="${field}" data-delta="-1" aria-label="Decrease ${field}">${iconMinus()}</button>
      <input class="control-value control-value-input ${field}-value" data-field="${field}" inputmode="${inputMode}" autocomplete="off" value="${cleanText(value || "")}" placeholder="${placeholder}" aria-label="${cleanText(label)}" />
      <button class="control-stepper" type="button" data-field="${field}" data-delta="1" aria-label="Increase ${field}">${iconPlus()}</button>
    </div>
  `;
}

async function openExerciseDetail(exerciseEl, tab = "log") {
  if (!exerciseEl) return;
  activeExerciseDetailEl = exerciseEl;
  exerciseDetailTab = tab;
  const view = $("exerciseDetailView");
  if (!view) return;
  view.classList.remove("hidden");
  view.setAttribute("aria-hidden", "false");
  await renderExerciseDetailView();
  view.querySelector(".exercise-detail-scroll")?.scrollTo({ top: 0, behavior: "auto" });
  haptic(12);
}

function closeExerciseDetail() {
  const view = $("exerciseDetailView");
  if (!view) return;
  view.classList.add("hidden");
  view.setAttribute("aria-hidden", "true");
  activeExerciseDetailEl = null;
  syncSessionUi();
}

function setExerciseDetailTab(tab) {
  exerciseDetailTab = tab === "guide" ? "guide" : "log";
  haptic(10);
  renderExerciseDetailView();
}

function getExerciseEquipment(name) {
  const lower = String(name || "").toLowerCase();
  if (/dumbbell|db/.test(lower)) return "Dumbbells";
  if (/barbell|bench|squat|deadlift|row/.test(lower)) return /bench/.test(lower) ? "Barbell, Bench" : "Barbell";
  if (/cable|pushdown|pulldown/.test(lower)) return "Cable Machine";
  if (/machine|leg press/.test(lower)) return "Machine";
  if (/bodyweight|pull.?up|chin.?up|dip/.test(lower)) return "Bodyweight";
  return "Gym equipment";
}

function getExerciseGuideData(exerciseName) {
  const name = String(exerciseName || "Exercise");
  const lower = name.toLowerCase();
  const muscle = inferMuscleTag(name) || "Target Muscle";
  const equipment = getExerciseEquipment(name);
  const generic = {
    description: `${name} is a strength movement for building control, range of motion, and consistent progressive overload.`,
    primaryMuscles: muscle,
    equipment,
    steps: [
      "Set up with a stable stance and controlled breathing",
      "Move through the full comfortable range of motion",
      "Pause briefly in the strongest controlled position",
      "Return slowly without losing tension",
      "Repeat for the target number of repetitions"
    ],
    tips: [
      "Use a weight you can control through every rep",
      "Keep the movement smooth and repeatable",
      "Stop the set when form starts to break down"
    ],
    mistakes: [
      "Using momentum to move the weight",
      "Cutting the range of motion short",
      "Rushing the lowering portion"
    ]
  };

  const guides = [
    {
      match: /bench press|flat bench/,
      data: {
        description: "The bench press is a compound exercise that primarily targets the chest while also engaging the shoulders and triceps.",
        primaryMuscles: "Chest (Pectoralis Major)",
        equipment: "Barbell, Flat Bench",
        steps: [
          "Lie flat on the bench with your eyes directly under the bar",
          "Grip the bar with hands slightly wider than shoulder-width apart",
          "Unrack the bar and position it directly over your chest",
          "Lower the bar slowly to your mid-chest, keeping elbows at 45 degrees",
          "Press the bar back up to the starting position, fully extending your arms",
          "Repeat for the desired number of repetitions"
        ],
        tips: [
          "Keep shoulder blades pulled back and down",
          "Drive your feet into the floor",
          "Control the bar path on every rep"
        ],
        mistakes: [
          "Bouncing the bar off the chest",
          "Letting elbows flare too wide",
          "Losing upper-back tightness"
        ]
      }
    },
    {
      match: /incline/,
      data: {
        description: "An upper chest focused pressing movement that emphasizes the clavicular head of the pectoralis major.",
        primaryMuscles: "Upper Chest",
        equipment,
        steps: [
          "Set the bench to a 30-45 degree incline",
          "Sit back with the weight controlled at shoulder level",
          "Press up and slightly inward until arms are extended",
          "Lower slowly back to the starting position",
          "Repeat for the target reps"
        ],
        tips: [
          "Keep dumbbells in line with upper chest",
          "Do not let elbows drop below shoulder level",
          "Control the weight throughout the movement"
        ],
        mistakes: [
          "Setting the incline too steep",
          "Using momentum to lift the weight",
          "Not controlling the negative portion"
        ]
      }
    },
    {
      match: /pulldown|lat/,
      data: {
        description: `${name} trains the lats and upper back through a vertical pulling pattern.`,
        primaryMuscles: "Back (Lats)",
        equipment: "Cable Machine",
        steps: [
          "Set the thigh pad so your body stays anchored",
          "Grip the handle and sit tall with chest lifted",
          "Pull elbows down toward your ribs",
          "Pause briefly without leaning far back",
          "Return the handle with control until lats stretch"
        ],
        tips: [
          "Think elbows down, not hands down",
          "Keep shoulders away from your ears",
          "Use a full controlled stretch at the top"
        ],
        mistakes: [
          "Turning the pull into a row",
          "Using body swing to start the rep",
          "Stopping short of a full stretch"
        ]
      }
    },
    {
      match: /row/,
      data: {
        description: `${name} builds mid-back strength with a horizontal pulling pattern.`,
        primaryMuscles: "Back",
        equipment,
        steps: [
          "Brace your torso and set your shoulders",
          "Pull the handle or bar toward your lower ribs",
          "Squeeze your back without shrugging",
          "Lower with control until arms extend",
          "Repeat without using momentum"
        ],
        tips: ["Keep ribs down", "Lead with elbows", "Control the stretch"],
        mistakes: ["Shrugging each rep", "Jerking the weight", "Letting posture collapse"]
      }
    },
    {
      match: /curl/,
      data: {
        description: `${name} targets the biceps with elbow flexion and controlled arm positioning.`,
        primaryMuscles: "Biceps",
        equipment,
        steps: [
          "Stand tall with elbows close to your sides",
          "Curl the weight up without swinging",
          "Squeeze briefly at the top",
          "Lower slowly until arms are extended",
          "Repeat with the same tempo"
        ],
        tips: ["Keep wrists neutral", "Avoid shoulder movement", "Use a controlled negative"],
        mistakes: ["Swinging the torso", "Letting elbows drift forward", "Dropping the weight quickly"]
      }
    },
    {
      match: /pushdown|tricep/,
      data: {
        description: `${name} isolates the triceps with a cable extension pattern.`,
        primaryMuscles: "Triceps",
        equipment: "Cable Machine",
        steps: [
          "Set the cable high and stand close to the stack",
          "Pin elbows near your sides",
          "Press down until elbows are extended",
          "Squeeze briefly at the bottom",
          "Return with control"
        ],
        tips: ["Keep shoulders relaxed", "Move only at the elbows", "Control the top position"],
        mistakes: ["Leaning over the handle", "Flaring elbows", "Using too much weight"]
      }
    },
    {
      match: /shoulder press|overhead/,
      data: {
        description: `${name} builds shoulder strength with a vertical pressing pattern.`,
        primaryMuscles: "Shoulders",
        equipment,
        steps: [
          "Brace your core and set the weight at shoulder height",
          "Press overhead without leaning back",
          "Finish with biceps near your ears",
          "Lower with control to shoulder height",
          "Repeat with steady tempo"
        ],
        tips: ["Keep ribs down", "Press in a straight path", "Avoid locking out aggressively"],
        mistakes: ["Overarching the lower back", "Pressing forward", "Bouncing from the bottom"]
      }
    },
    {
      match: /lateral raise|rear delt|fly|shrug/,
      data: {
        description: `${name} targets smaller upper-body muscles and rewards controlled reps over heavy loading.`,
        primaryMuscles: muscle,
        equipment,
        steps: [
          "Choose a light weight and set your posture",
          "Move the weight with control",
          "Pause briefly in the target position",
          "Lower slowly without losing tension",
          "Repeat for clean, consistent reps"
        ],
        tips: ["Use strict form", "Keep tension on the target muscle", "Stop before momentum takes over"],
        mistakes: ["Going too heavy", "Rushing the lowering phase", "Turning it into a full-body movement"]
      }
    }
  ];

  return guides.find((guide) => guide.match.test(lower))?.data || generic;
}

function renderGuideList(items, markerClass, numbered = false) {
  return `<ul class="guide-list">${items.map((item, index) => `
    <li><span class="${markerClass}">${numbered ? index + 1 : markerClass === "guide-list-x" ? "x" : "✓"}</span><span>${cleanText(item)}</span></li>
  `).join("")}</ul>`;
}

function renderExerciseGuideContent(exerciseEl) {
  const name = exerciseEl.querySelector(".exercise-name")?.value.trim() || "Exercise";
  const guide = getExerciseGuideData(name);
  return `
    <div class="exercise-guide-content">
      <div class="exercise-guide-card description">${cleanText(guide.description)}</div>
      <div class="guide-info-grid">
        <div class="exercise-guide-card guide-info-card"><span>Primary Muscles</span><strong>${cleanText(guide.primaryMuscles)}</strong></div>
        <div class="exercise-guide-card guide-info-card"><span>Equipment</span><strong>${cleanText(guide.equipment)}</strong></div>
      </div>
      <details class="exercise-guide-card guide-accordion guide-step">
        <summary><span class="guide-heading-icon">${iconPlayOutline()}</span><span>Step-by-Step Guide</span><span class="guide-disclosure">${iconChevronRight()}</span></summary>
        ${renderGuideList(guide.steps, "guide-list-index", true)}
      </details>
      <details class="exercise-guide-card guide-accordion guide-tips">
        <summary><span class="guide-heading-icon">${iconCheck()}</span><span>Pro Tips</span><span class="guide-disclosure">${iconChevronRight()}</span></summary>
        ${renderGuideList(guide.tips, "guide-list-check")}
      </details>
      <details class="exercise-guide-card guide-accordion guide-mistakes">
        <summary><span class="guide-heading-icon">${iconInfo()}</span><span>Common Mistakes</span><span class="guide-disclosure">${iconChevronRight()}</span></summary>
        ${renderGuideList(guide.mistakes, "guide-list-x")}
      </details>
    </div>
  `;
}

async function renderExerciseLogContent(exerciseEl) {
  const name = exerciseEl.querySelector(".exercise-name")?.value.trim() || "Exercise";
  const profile = getExerciseProfile(name);
  const { row, index } = getCurrentSetMeta(exerciseEl);
  const warmupRows = Array.from(exerciseEl.querySelectorAll(".set-row"))
    .filter((setRow) => setRow.querySelector(".set-warmup")?.checked);
  const values = getCurrentSetDisplayValues(exerciseEl);
  const last = await getLastExercisePerformance(name);
  const lastWorkSets = workSetsOnly(last?.sets || []);
  const lastWeight = lastWorkSets.length ? formatPrimaryWeight(lastWorkSets) : values.weight;
  const pr = await getExercisePr(name);
  const lastLabel = lastWeight && lastWeight !== "-" ? `${lastWeight} lb` : "-";
  const lastReps = lastWorkSets.length ? formatRepsList(lastWorkSets) : "No previous work sets";
  const targetPlan = last ? buildTargetFromLastSets(name, last.sets) : {
    weight: "-",
    repsText: `${profile.min}-${profile.max} reps`,
    reason: `${profile.min}-${profile.max} range - build a baseline first`,
    targetSets: []
  };
  const targetWeightLabel = targetPlan.weight || "-";
  const targetRepsLabel = targetRepsBySetText(targetPlan);
  const prLabel = pr ? `${pr.weight} x ${pr.reps}` : "-";
  const prSub = pr ? `est ${pr.estimated1rm.toFixed(1)} lb - ${dateLabel(pr.date)}` : "Not set yet";
  const reasoningLine = last
    ? `${targetPlan.reason} - Last logged ${dateLabel(last.workoutDate)}.`
    : `${profile.min}-${profile.max} range - build a baseline first.`;

  return `
    <div class="exercise-detail-guidance">
      <div class="detail-stat-grid">
        <div class="detail-stat-card"><span class="detail-stat-icon">Last</span><strong>${cleanText(lastLabel)}</strong><span>${cleanText(lastReps)}</span></div>
        <div class="detail-stat-card"><span class="detail-stat-icon">Target</span><strong>${cleanText(targetWeightLabel)}</strong><span>${cleanText(targetRepsLabel)}</span></div>
        <div class="detail-stat-card"><span class="detail-stat-icon">PR</span><strong>${cleanText(prLabel)}</strong><span>${cleanText(prSub)}</span></div>
      </div>
      <div class="guidance-reason">${cleanText(reasoningLine)}</div>
    </div>
    <div class="exercise-detail-card">
      <span class="current-set-label">Current Set<strong>${row ? `Set ${index + 1}` : "Complete"}</strong></span>
      ${controlInputHtml("weight", values.weight === "-" ? "" : values.weight, "Weight (lbs)")}
      ${controlInputHtml("reps", values.reps === "-" ? "" : values.reps, "Reps")}
      ${controlInputHtml("rpe", values.rpe, "RPE")}
      <label class="detail-warmup-toggle">
        <input type="checkbox" data-detail-warmup ${row?.querySelector(".set-warmup")?.checked ? "checked" : ""} ${row ? "" : "disabled"} />
        <span>Warm-up set</span>
      </label>
      <div class="detail-set-actions">
        <button class="detail-add-set" type="button" data-detail-action="add-set">+ Add Set</button>
        <button class="detail-remove-set" type="button" data-detail-action="remove-last-set">Remove Last Set</button>
        ${warmupRows.length ? `<button class="detail-remove-set" type="button" data-detail-action="restore-last-warmup">Undo Last Warm-up</button>` : ""}
      </div>
    </div>
    <div class="exercise-detail-card">
      <label for="exerciseDetailNotes">Exercise Notes</label>
      <textarea id="exerciseDetailNotes" data-detail-notes placeholder="Grip, form cue, pain, rest, machine setting, etc.">${cleanText(exerciseEl.querySelector(".exercise-notes")?.value || "")}</textarea>
    </div>
    <div class="hidden" aria-hidden="true">
      <strong>PR</strong>
      <p class="muted small" style="margin:6px 0 0;">${cleanText(prLabel)}${pr ? ` · est ${pr.estimated1rm.toFixed(1)} lb` : ""}</p>
    </div>
  `;
}

function updateExerciseDetailHeader(exerciseEl) {
  const name = exerciseEl.querySelector(".exercise-name")?.value.trim() || "Exercise";
  const muscle = inferMuscleTag(name) || "General";
  const { done, total } = getExerciseSetProgress(exerciseEl);
  $("exerciseDetailTitle").textContent = name;
  $("exerciseDetailSubtitle").textContent = `${muscle} · Intermediate`;
  $("exerciseDetailProgress").textContent = `${done}/${total} sets`;
  all(".exercise-detail-tab").forEach((tab) => {
    const active = tab.dataset.detailTab === exerciseDetailTab;
    tab.classList.toggle("active", active);
    tab.setAttribute("aria-selected", String(active));
  });
}

function updateExerciseDetailCompleteButton(exerciseEl) {
  const button = $("exerciseDetailCompleteSet");
  if (!button) return;
  const { row, index } = getCurrentSetMeta(exerciseEl);
  if (!row) {
    button.textContent = "Exercise Complete";
    button.disabled = true;
    return;
  }
  button.disabled = false;
  button.textContent = `Complete Set ${index + 1}`;
}

function addSetToExercise(exerciseEl) {
  const setsEl = exerciseEl?.querySelector(".sets");
  if (!setsEl) return false;
  setsEl.appendChild(makeSetRow());
  setRows(exerciseEl);
  updateExerciseSummary(exerciseEl);
  saveDraftSilently();
  haptic(14);
  if (isExerciseDetailOpen()) renderExerciseDetailView();
  return true;
}

function removeLastSetFromExercise(exerciseEl) {
  const workSets = getWorkSetRows(exerciseEl);
  if (!workSets.length) {
    toast("No work sets to remove.");
    return false;
  }

  const row = [...workSets].reverse().find((item) => !item.querySelector(".set-done")?.checked) || workSets[workSets.length - 1];
  const isCompleted = row.querySelector(".set-done")?.checked;
  if (isCompleted && !confirm("Remove the last completed set?")) return false;

  row.remove();
  setRows(exerciseEl);
  updateExerciseSummary(exerciseEl);
  saveDraftSilently();
  haptic(isCompleted ? [20, 30] : 14);
  if (isExerciseDetailOpen()) renderExerciseDetailView();
  return true;
}

function bindExerciseDetailControls() {
  const view = $("exerciseDetailView");
  if (!view) return;
  view.querySelectorAll("[data-detail-action]").forEach((button) => {
    button.addEventListener("click", () => {
      if (!activeExerciseDetailEl) return;
      if (button.dataset.detailAction === "add-set") addSetToExercise(activeExerciseDetailEl);
      if (button.dataset.detailAction === "remove-last-set") removeLastSetFromExercise(activeExerciseDetailEl);
      if (button.dataset.detailAction === "restore-last-warmup") {
        const warmupRows = Array.from(activeExerciseDetailEl.querySelectorAll(".set-row"))
          .filter((row) => row.querySelector(".set-warmup")?.checked);
        const warmup = warmupRows.at(-1)?.querySelector(".set-warmup");
        if (!warmup) return;
        warmup.checked = false;
        setRows(activeExerciseDetailEl);
        updateExerciseSummary(activeExerciseDetailEl);
        saveDraftSilently();
        renderExerciseDetailView();
      }
    });
  });
  view.querySelectorAll(".control-stepper").forEach((button) => {
    button.addEventListener("click", () => {
      if (!activeExerciseDetailEl) return;
      adjustCurrentSetValue(activeExerciseDetailEl, button.dataset.field, Number(button.dataset.delta || 0));
      renderExerciseDetailView();
    });
  });
  view.querySelectorAll(".control-value-input").forEach((input) => {
    bindControlValueInput(input, () => activeExerciseDetailEl);
  });
  view.querySelector("[data-detail-warmup]")?.addEventListener("change", (event) => {
    if (!activeExerciseDetailEl) return;
    const row = getCurrentSetRow(activeExerciseDetailEl);
    const warmup = row?.querySelector(".set-warmup");
    if (!warmup) return;
    warmup.checked = event.target.checked;
    setRows(activeExerciseDetailEl);
    updateExerciseSummary(activeExerciseDetailEl);
    saveDraftSilently();
    renderExerciseDetailView();
  });
  view.querySelector("[data-detail-notes]")?.addEventListener("input", (event) => {
    const notes = activeExerciseDetailEl?.querySelector(".exercise-notes");
    if (!notes) return;
    notes.value = event.target.value;
    saveDraftSilently();
  });
}

async function renderExerciseDetailView() {
  const view = $("exerciseDetailView");
  const content = $("exerciseDetailContent");
  const exerciseEl = activeExerciseDetailEl;
  if (!view || !content || !exerciseEl || !document.body.contains(exerciseEl)) {
    closeExerciseDetail();
    return;
  }
  if (document.activeElement?.closest("#exerciseDetailView .control-value-input")) return;

  const token = ++exerciseDetailRenderToken;
  updateExerciseDetailHeader(exerciseEl);
  content.innerHTML = exerciseDetailTab === "guide"
    ? renderExerciseGuideContent(exerciseEl)
    : await renderExerciseLogContent(exerciseEl);
  if (token !== exerciseDetailRenderToken) return;
  updateExerciseDetailCompleteButton(exerciseEl);
  bindExerciseDetailControls();
}

function toggleExerciseGuide(exerciseEl) {
  openExerciseDetail(exerciseEl, "log");
}

function getDragAfterElement(container, y) {
  const draggableElements = Array.from(container.querySelectorAll(".exercise:not(.dragging)"));
  return draggableElements.reduce((closest, child) => {
    const box = child.getBoundingClientRect();
    const offset = y - box.top - box.height / 2;
    if (offset < 0 && offset > closest.offset) return { offset, element: child };
    return closest;
  }, { offset: Number.NEGATIVE_INFINITY, element: null }).element;
}

function startExerciseDrag(event, exerciseEl) {
  if (event.button !== undefined && event.button !== 0) return;
  const list = $("exerciseList");
  if (!list || !exerciseEl) return;
  event.preventDefault();
  const rect = exerciseEl.getBoundingClientRect();
  const placeholder = document.createElement("div");
  placeholder.className = "exercise-drag-placeholder";
  placeholder.style.height = `${rect.height}px`;
  list.insertBefore(placeholder, exerciseEl.nextSibling);
  exerciseEl.classList.add("dragging");
  exerciseEl.style.position = "fixed";
  exerciseEl.style.left = `${rect.left}px`;
  exerciseEl.style.top = `${rect.top}px`;
  exerciseEl.style.width = `${rect.width}px`;
  exerciseEl.style.zIndex = "80";

  exerciseDragState = {
    exerciseEl,
    list,
    placeholder,
    startY: event.clientY,
    originTop: rect.top,
    pointerId: event.pointerId
  };

  if (event.pointerId !== undefined) event.currentTarget.setPointerCapture?.(event.pointerId);
  haptic(12);
}

function moveExerciseDrag(event) {
  if (!exerciseDragState) return;
  const { exerciseEl, list, placeholder, startY, originTop } = exerciseDragState;
  const deltaY = event.clientY - startY;
  exerciseEl.style.top = `${originTop + deltaY}px`;
  const afterElement = getDragAfterElement(list, event.clientY);
  if (!afterElement) list.appendChild(placeholder);
  else list.insertBefore(placeholder, afterElement);

  const viewportMargin = 74;
  if (event.clientY < viewportMargin) window.scrollBy({ top: -10, behavior: "auto" });
  if (window.innerHeight - event.clientY < viewportMargin) window.scrollBy({ top: 10, behavior: "auto" });
}

function endExerciseDrag() {
  if (!exerciseDragState) return;
  const { exerciseEl, list, placeholder } = exerciseDragState;
  list.insertBefore(exerciseEl, placeholder);
  placeholder.remove();
  exerciseEl.classList.remove("dragging");
  exerciseEl.style.position = "";
  exerciseEl.style.left = "";
  exerciseEl.style.top = "";
  exerciseEl.style.width = "";
  exerciseEl.style.zIndex = "";
  exerciseDragState = null;
  updateExerciseFlowButtons();
  syncSessionUi();
  saveDraftSilently();
  haptic(18);
}

function setRows(exerciseEl) {
  exerciseEl.querySelectorAll(".set-index").forEach((el, index) => { el.textContent = index + 1; });
  updateExerciseSummary(exerciseEl);
}

function compactSetSummary(workSets) {
  const usable = workSets
    .map((row) => ({
      weight: row.querySelector(".set-weight")?.value.trim(),
      reps: row.querySelector(".set-reps")?.value.trim()
    }))
    .filter((set) => set.weight || set.reps);

  if (!usable.length) return "";

  const weights = [...new Set(usable.map((set) => set.weight).filter(Boolean))];
  const reps = usable.map((set) => set.reps || "-").join(" / ");

  if (weights.length === 1) return `${weights[0]} lb • ${reps} reps`;
  return usable.slice(0, 3).map((set) => `${set.weight || "-"}×${set.reps || "-"}`).join(" · ") + (usable.length > 3 ? " · …" : "");
}

function updateExerciseSummary(exerciseEl) {
  if (!exerciseEl) return;
  const name = exerciseEl.querySelector(".exercise-name")?.value.trim() || "Exercise";
  const sets = Array.from(exerciseEl.querySelectorAll(".set-row"));
  const workSets = sets.filter((row) => !row.querySelector(".set-warmup")?.checked);
  const doneSets = workSets.filter((row) => row.querySelector(".set-done")?.checked);
  const title = exerciseEl.querySelector(".exercise-title");
  const line = exerciseEl.querySelector(".exercise-summary-line");
  const setProgress = exerciseEl.querySelector(".exercise-set-progress");
  const repTarget = exerciseEl.querySelector(".exercise-rep-target");
  const muscleChip = exerciseEl.querySelector(".exercise-muscle-chip");
  const completedChips = exerciseEl.querySelector(".completed-set-chips");
  const profile = getExerciseProfile(name);
  const broadTargetText = `${profile.min}-${profile.max} reps`;
  const targetText = exerciseEl.dataset.todayTargetSummary || broadTargetText;
  const muscle = inferMuscleTag(name) || "General";

  if (title) title.textContent = name;
  if (setProgress) setProgress.textContent = workSets.length ? `${doneSets.length}/${workSets.length} sets` : "No sets";
  if (repTarget) repTarget.textContent = targetText;
  if (muscleChip) muscleChip.textContent = muscle;
  if (completedChips) completedChips.innerHTML = completedSetChips(exerciseEl);
  if (line) {
    const status = workSets.length ? `${doneSets.length} / ${workSets.length} done` : "No work sets yet";
    const setSummary = compactSetSummary(workSets);
    line.textContent = setSummary ? `${status} • ${setSummary}` : status;
  }

  if (line) {
    line.innerHTML = `<span class="exercise-set-progress">${workSets.length ? `${doneSets.length}/${workSets.length} sets` : "No sets"}</span><span aria-hidden="true">&bull;</span><span class="exercise-rep-target">${cleanText(targetText)}</span>`;
  }

  exerciseEl.classList.toggle("exercise-complete", workSets.length > 0 && doneSets.length === workSets.length);
  updateExerciseFlowButtons();
  syncSessionUi();
}

function setExerciseCollapsed(exerciseEl, collapsed) {
  exerciseEl.classList.toggle("collapsed", collapsed);
  exerciseEl.classList.toggle("is-active", !collapsed);
  const button = exerciseEl.querySelector(".collapse-exercise");
  if (button) {
    button.setAttribute("aria-label", collapsed ? "Expand exercise" : "Collapse exercise");
    button.setAttribute("aria-expanded", String(!collapsed));
  }
  if (!collapsed) replayAnimation(exerciseEl, "settle-in", 260);
  updateExerciseSummary(exerciseEl);
}

function getActiveExerciseIndex() {
  const exercises = all(".exercise");
  const index = exercises.findIndex((exercise) => !exercise.classList.contains("collapsed"));
  return index >= 0 ? index : 0;
}

function collapseAllButIndex(activeIndex = 0) {
  all(".exercise").forEach((exercise, index) => {
    const isActive = index === activeIndex;
    exercise.classList.toggle("collapsed", !isActive);
    exercise.classList.toggle("is-active", isActive);
    const button = exercise.querySelector(".collapse-exercise");
    if (button) {
      button.setAttribute("aria-label", !isActive ? "Expand exercise" : "Collapse exercise");
      button.setAttribute("aria-expanded", String(isActive));
    }
    if (isActive) replayAnimation(exercise, "settle-in", 260);
    updateExerciseSummary(exercise);
  });
  updateExerciseFlowButtons();
  syncSessionUi();
}

function collapseAllButFirstExercise() {
  collapseAllButIndex(0);
}

function getSessionFocusBand() {
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
  const header = document.querySelector("#sessionView.live-session > .session-toolbar");
  const dock = document.querySelector("#sessionView .save-dock");
  const headerHeight = header ? header.getBoundingClientRect().height : 0;
  const dockHeight = dock ? dock.getBoundingClientRect().height : 0;
  let top = headerHeight + 16;
  let bottom = viewportHeight - dockHeight - 18;

  if (bottom - top < 180) {
    top = Math.max(12, Math.min(top, viewportHeight - 192));
    bottom = Math.min(viewportHeight - 12, top + 180);
  }

  return { top, bottom };
}

function focusExerciseInSessionView(exerciseEl) {
  if (!exerciseEl || !$("sessionView") || $("sessionView").classList.contains("hidden")) return;
  const scroller = document.scrollingElement || document.documentElement;
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
  const rect = exerciseEl.getBoundingClientRect();
  if (!rect.height || !viewportHeight) return;

  const { top, bottom } = getSessionFocusBand();
  const safeHeight = Math.max(160, bottom - top);
  const desiredTop = rect.height <= safeHeight
    ? top + (safeHeight - rect.height) * 0.44
    : top;
  const maxScroll = Math.max(0, scroller.scrollHeight - viewportHeight);
  const targetTop = clamp(scroller.scrollTop + rect.top - desiredTop, 0, maxScroll);

  if (Math.abs(targetTop - scroller.scrollTop) < 2) return;
  window.scrollTo({ top: Math.round(targetTop), behavior: motionBehavior() });
}

function scheduleExerciseFocus(exerciseEl) {
  const token = ++exerciseFocusScrollToken;
  const focus = () => {
    if (token !== exerciseFocusScrollToken) return;
    focusExerciseInSessionView(exerciseEl);
  };
  requestAnimationFrame(() => requestAnimationFrame(focus));
  setTimeout(focus, motionBehavior() === "smooth" ? 320 : 50);
}

function openExercise(exerciseEl, scroll = true) {
  const exercises = all(".exercise");
  const index = exercises.indexOf(exerciseEl);
  if (index < 0) return;
  collapseAllButIndex(index);
  if (scroll) scheduleExerciseFocus(exerciseEl);
  haptic(10);
  saveDraftSilently();
}

function toggleExerciseCollapse(exerciseEl) {
  if (exerciseEl.classList.contains("collapsed")) openExercise(exerciseEl, true);
  else {
    setExerciseCollapsed(exerciseEl, true);
    haptic(10);
  }
}

function goToExerciseOffset(exerciseEl, offset) {
  const exercises = all(".exercise");
  const index = exercises.indexOf(exerciseEl);
  const nextIndex = index + offset;
  if (nextIndex < 0 || nextIndex >= exercises.length) return;
  openExercise(exercises[nextIndex], true);
}

function finishAndNextExercise(exerciseEl) {
  const exercises = all(".exercise");
  const index = exercises.indexOf(exerciseEl);
  if (index < exercises.length - 1) {
    openExercise(exercises[index + 1], true);
    haptic(28);
    return;
  }
  setExerciseCollapsed(exerciseEl, true);
  haptic([35, 40, 35]);
  toast("Last exercise finished. Save when ready.");
  saveDraftSilently();
}

function updateExerciseFlowButtons() {
  const exercises = all(".exercise");
  exercises.forEach((exercise, index) => {
    const prev = exercise.querySelector(".prev-exercise");
    const next = exercise.querySelector(".next-exercise");
    if (prev) prev.disabled = index === 0;
    if (next) next.textContent = index === exercises.length - 1 ? "Finish ✓" : "Next →";
  });
}

function makeSetRow(set = {}) {
  const row = document.createElement("div");
  row.className = "set-row";
  row.innerHTML = `
    <div class="set-index">1</div>
    <div><label>Weight</label><input class="set-weight" type="number" step="0.5" placeholder="lb" value="${cleanText(set.weight || "")}" /></div>
    <div><label>Reps</label><input class="set-reps" type="number" step="1" placeholder="reps" value="${cleanText(set.reps || "")}" /></div>
    <div class="rpe-field"><label>RPE</label><input class="set-rpe" type="number" min="1" max="10" step="0.5" placeholder="1-10" value="${cleanText(set.rpe || "")}" /></div>
    <div class="set-actions">
      <label class="toggle-pill"><input class="set-done" type="checkbox" ${set.done ? "checked" : ""} /> Done</label>
      <label class="toggle-pill warmup"><input class="set-warmup" type="checkbox" ${set.warmup ? "checked" : ""} /> Warm-up</label>
      <button class="danger delete-set" type="button">Delete</button>
    </div>
  `;
  row.querySelector(".delete-set").addEventListener("click", () => {
    const exerciseEl = row.closest(".exercise");
    row.remove();
    setRows(exerciseEl);
    saveDraftSilently();
  });

  row.querySelectorAll("input").forEach((input) => {
    input.addEventListener("focus", () => scrollInputIntoView(input));
  });

  row.querySelector(".set-done")?.addEventListener("change", (event) => {
    if (event.target.checked) {
      replayAnimation(row, "set-just-done", 460);
      haptic([18, 28, 18]);
    }
  });
  return row;
}

function makeExercise(data = {}) {
  const exercise = document.createElement("div");
  exercise.className = "exercise";
  exercise.innerHTML = `
    <div class="exercise-top">
      <div class="exercise-summary">
        <div class="exercise-title">Exercise</div>
        <div class="exercise-summary-line">0 / 0 work sets done</div>
      </div>
      <div class="exercise-top-actions">
        <button class="ghost collapse-exercise" type="button">Minimize</button>
        <button class="danger delete-exercise" type="button">Remove</button>
      </div>
    </div>
    <div class="exercise-body stack">
      <div><label>Exercise Name</label><input class="exercise-name exercise-name-input" type="text" placeholder="Example: Flat Bench Press" value="${cleanText(data.name || "")}" /></div>
      <div class="last-performance muted small"></div>
      <div class="exercise-actions">
        <button class="ghost use-last-sets" type="button">Use Last Sets</button>
        <button class="ghost add-set" type="button">+ Add Set</button>
      </div>
      <div class="sets"></div>
      <div><label>Exercise Notes</label><textarea class="exercise-notes" placeholder="Grip, form cue, pain, rest, machine setting, etc.">${cleanText(data.notes || "")}</textarea></div>
      <div class="exercise-flow-actions">
        <button class="ghost prev-exercise" type="button" aria-label="Previous exercise">←</button>
        <button class="primary next-exercise" type="button">Next →</button>
      </div>
    </div>
  `;

  exercise.innerHTML = `
    <div class="exercise-top">
      <div class="exercise-status">1</div>
      <div class="exercise-summary">
        <div class="exercise-title">Exercise</div>
        <div class="exercise-summary-line"><span class="exercise-set-progress">0/0 sets</span><span aria-hidden="true">&bull;</span><span class="exercise-rep-target">8-12 reps</span></div>
      </div>
      <span class="exercise-muscle-chip">General</span>
      <button class="collapse-exercise" type="button" aria-label="Collapse exercise" aria-expanded="true">${iconChevron()}</button>
      <button class="drag-handle" type="button" aria-label="Reorder exercise">${iconGrip()}</button>
    </div>
    <div class="exercise-body stack">
      <div class="live-hidden-editor">
        <label>Exercise Name</label>
        <input class="exercise-name exercise-name-input" type="text" placeholder="Example: Flat Bench Press" value="${cleanText(data.name || "")}" />
      </div>
      <button class="guide-row" type="button">
        <span class="guide-info-icon">${iconInfo()}</span>
        <span>Exercise Details</span>
      </button>
      <div class="last-performance muted small"></div>
      <div class="current-set-panel">
        <span class="current-set-label">Current Set<strong>Set 1</strong></span>
        <div class="live-control-row">
          <span class="live-control-label">Weight (lbs)</span>
          <button class="control-stepper" type="button" data-field="weight" data-delta="-1" aria-label="Decrease weight">${iconMinus()}</button>
          <input class="control-value control-value-input weight-value" data-field="weight" inputmode="decimal" autocomplete="off" value="0" aria-label="Weight (lbs)" />
          <button class="control-stepper" type="button" data-field="weight" data-delta="1" aria-label="Increase weight">${iconPlus()}</button>
        </div>
        <div class="live-control-row">
          <span class="live-control-label">Reps</span>
          <button class="control-stepper" type="button" data-field="reps" data-delta="-1" aria-label="Decrease reps">${iconMinus()}</button>
          <input class="control-value control-value-input reps-value" data-field="reps" inputmode="numeric" autocomplete="off" value="0" aria-label="Reps" />
          <button class="control-stepper" type="button" data-field="reps" data-delta="1" aria-label="Increase reps">${iconPlus()}</button>
        </div>
        <div class="live-control-row live-rpe-row">
          <span class="live-control-label">RPE</span>
          <button class="control-stepper" type="button" data-field="rpe" data-delta="-1" aria-label="Decrease RPE">${iconMinus()}</button>
          <input class="control-value control-value-input rpe-value" data-field="rpe" inputmode="decimal" autocomplete="off" value="" placeholder="-" aria-label="RPE" />
          <button class="control-stepper" type="button" data-field="rpe" data-delta="1" aria-label="Increase RPE">${iconPlus()}</button>
        </div>
        <button class="complete-set-button" type="button">${iconCheck()} Complete Set 1</button>
      </div>
      <div class="completed-set-chips"></div>
      <div class="exercise-actions">
        <button class="ghost use-last-sets" type="button">Use Last Sets</button>
        <button class="ghost add-set" type="button">Add Set</button>
        <button class="danger delete-exercise" type="button">Remove</button>
      </div>
      <div class="sets"></div>
      <div class="live-hidden-editor">
        <label>Exercise Notes</label>
        <textarea class="exercise-notes" placeholder="Grip, form cue, pain, rest, machine setting, etc.">${cleanText(data.notes || "")}</textarea>
      </div>
      <div class="exercise-flow-actions">
        <button class="ghost prev-exercise" type="button" aria-label="Previous exercise">Previous</button>
        <button class="primary next-exercise" type="button">Next</button>
      </div>
    </div>
  `;

  const setsEl = exercise.querySelector(".sets");
  const sets = data.sets?.length ? data.sets : [{}, {}, {}];
  sets.forEach((set) => setsEl.appendChild(makeSetRow(set)));
  setRows(exercise);

  exercise.querySelector(".add-set").addEventListener("click", () => {
    addSetToExercise(exercise);
  });
  exercise.querySelector(".use-last-sets").addEventListener("click", async () => {
    await useLastSets(exercise);
    updateExerciseSummary(exercise);
    saveDraftSilently();
  });
  exercise.querySelector(".delete-exercise").addEventListener("click", () => {
    exercise.remove();
    updateExerciseFlowButtons();
    syncSessionUi();
    saveDraftSilently();
  });
  exercise.querySelector(".collapse-exercise").addEventListener("click", () => toggleExerciseCollapse(exercise));
  exercise.querySelector(".guide-row").addEventListener("click", () => toggleExerciseGuide(exercise));
  exercise.querySelector(".complete-set-button").addEventListener("click", () => completeCurrentSet(exercise));
  exercise.querySelectorAll(".control-stepper").forEach((button) => {
    button.addEventListener("click", () => adjustCurrentSetValue(exercise, button.dataset.field, Number(button.dataset.delta || 0)));
  });
  exercise.querySelectorAll(".control-value-input").forEach((input) => {
    bindControlValueInput(input, () => exercise);
  });
  const dragHandle = exercise.querySelector(".drag-handle");
  dragHandle.addEventListener("pointerdown", (event) => startExerciseDrag(event, exercise));
  dragHandle.addEventListener("mousedown", (event) => {
    if (!exerciseDragState) startExerciseDrag(event, exercise);
  });
  exercise.querySelector(".exercise-top").addEventListener("click", (event) => {
    if (event.target.closest("button")) return;
    openExercise(exercise, true);
  });
  exercise.querySelector(".prev-exercise").addEventListener("click", () => goToExerciseOffset(exercise, -1));
  exercise.querySelector(".next-exercise").addEventListener("click", () => finishAndNextExercise(exercise));
  exercise.querySelector(".exercise-name").addEventListener("input", () => {
    updateExerciseHint(exercise);
    updateExerciseSummary(exercise);
  });
  exercise.addEventListener("input", () => updateExerciseSummary(exercise));
  exercise.addEventListener("change", () => updateExerciseSummary(exercise));
  updateExerciseHint(exercise);
  updateExerciseSummary(exercise);
  return exercise;
}

async function loadWorkoutTemplate() {
  const type = $("workoutType").value;
  const template = (await getRoutines()).find((item) => item.name === type);
  const names = template?.exercises || [];
  const list = $("exerciseList");
  list.innerHTML = "";
  if (!names.length) list.appendChild(makeExercise());
  else names.forEach((name) => list.appendChild(makeExercise({ name })));
  await autoLoadLastSetsForAllExercises();
  collapseAllButFirstExercise();
  updateSessionTitle();
  await updateAllExerciseHints();
}

async function getLastExercisePerformance(exerciseName) {
  const name = exerciseName.trim().toLowerCase();
  if (!name) return null;

  const workouts = (await getWorkouts())
    .filter((workout) => workout.id !== editingWorkoutId)
    .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));

  for (const workout of workouts) {
    const exercise = workout.exercises.find((item) => item.name.trim().toLowerCase() === name);
    if (!exercise) continue;

    const bestSet = getBestSet(exercise);
    return { workoutDate: workout.date, type: workout.type, sets: exercise.sets, bestSet };
  }

  return null;
}

function getExerciseProfile(exerciseName) {
  return resolveExerciseProfile(exerciseName, getAppSettings());
}

export function completeActiveExerciseDetailSet() {
  if (!activeExerciseDetailEl) return;
  completeCurrentSet(activeExerciseDetailEl);
  renderExerciseDetailView();
}

function formatRepsList(sets = []) {
  const reps = sets.map((set) => set.reps || "-").filter(Boolean);
  return reps.length ? `${reps.join(" / ")} reps` : "No reps logged";
}

function formatPrimaryWeight(sets = []) {
  const weighted = sets.filter((set) => set.weight);
  if (!weighted.length) return "-";
  const counts = new Map();
  weighted.forEach((set) => {
    const key = String(set.weight);
    counts.set(key, (counts.get(key) || 0) + 1);
  });
  return Array.from(counts.entries()).sort((a, b) => b[1] - a[1])[0][0];
}

function buildTargetFromLastSets(exerciseName, lastSets = []) {
  return calculateTargetFromLastSets(exerciseName, lastSets, getAppSettings());
}

function compactTargetReps(targetPlan) {
  const reps = (targetPlan?.targetSets || [])
    .map((set) => set.reps || "")
    .filter(Boolean);
  if (reps.length) return reps.join("/");
  return String(targetPlan?.repsText || "").replace(/\s*reps?\s*$/i, "").replace(/\s*\/\s*/g, "/").trim();
}

function compactTargetSummary(targetPlan) {
  const weight = String(targetPlan?.weight || "").trim();
  const reps = compactTargetReps(targetPlan);
  if (!weight || weight === "-" || !reps || /log clean|hold form/i.test(reps)) return "";
  return `${weight} x ${reps}`;
}

function targetRepsBySetText(targetPlan) {
  const reps = compactTargetReps(targetPlan);
  return reps ? `${reps.replaceAll("/", " / ")} reps` : "Build a baseline";
}

async function getExercisePr(exerciseName) {
  const name = exerciseName.trim().toLowerCase();
  if (!name) return null;
  const workouts = await getWorkouts();
  let best = null;
  for (const workout of workouts) {
    if (workout.id === editingWorkoutId) continue;
    for (const exercise of workout.exercises || []) {
      if ((exercise.name || "").trim().toLowerCase() !== name) continue;
      const bestSet = getBestSet(exercise);
      if (!bestSet) continue;
      const candidate = { ...bestSet, date: workout.date, type: workout.type };
      if (!best || candidate.estimated1rm > best.estimated1rm) best = candidate;
    }
  }
  return best;
}

async function updateExerciseHint(exerciseEl) {
  const name = exerciseEl.querySelector(".exercise-name").value;
  const target = exerciseEl.querySelector(".last-performance");
  if (!target) return;

  const trimmed = name.trim();
  if (!trimmed) {
    exerciseEl.dataset.todayTargetSummary = "";
    target.innerHTML = "";
    return;
  }

  const last = await getLastExercisePerformance(trimmed);
  const pr = await getExercisePr(trimmed);
  const profile = getExerciseProfile(trimmed);

  if (!last) {
    exerciseEl.dataset.todayTargetSummary = "";
    const prText = pr ? `${pr.weight} × ${pr.reps}` : "-";
    target.innerHTML = `<div class="detail guidance-card">
      <div class="guidance-grid">
        <div class="guidance-metric"><span>Last</span><strong>No history</strong><small>First logged session</small></div>
        <div class="guidance-metric"><span>Target</span><strong>${profile.min}-${profile.max} reps</strong><small>Build a baseline</small></div>
        <div class="guidance-metric"><span>PR</span><strong>${prText}</strong><small>${pr ? dateLabel(pr.date) : "Not set yet"}</small></div>
      </div>
      <div class="guidance-reason">Suggested internally as ${profile.type} · ${profile.min}-${profile.max} rep range.</div>
    </div>`;
    updateExerciseSummary(exerciseEl);
    return;
  }

  const sets = workSetsOnly(last.sets).slice(0, 4);
  const lastWeight = formatPrimaryWeight(sets);
  const lastReps = formatRepsList(sets);
  const targetPlan = buildTargetFromLastSets(trimmed, last.sets);
  exerciseEl.dataset.todayTargetSummary = compactTargetSummary(targetPlan);
  const prText = pr ? `${pr.weight} × ${pr.reps}` : "-";
  const prSub = pr ? `est ${pr.estimated1rm.toFixed(1)} lb · ${dateLabel(pr.date)}` : "Not set yet";

  target.innerHTML = `<div class="detail guidance-card">
    <div class="guidance-grid">
      <div class="guidance-metric"><span>Last</span><strong>${cleanText(lastWeight)}${lastWeight !== "-" ? " lb" : ""}</strong><small>${cleanText(lastReps)}</small></div>
      <div class="guidance-metric"><span>Target</span><strong>${cleanText(targetPlan.weight)}</strong><small>${cleanText(targetPlan.repsText)}</small></div>
      <div class="guidance-metric"><span>PR</span><strong>${cleanText(prText)}</strong><small>${cleanText(prSub)}</small></div>
    </div>
    <div class="guidance-reason">${cleanText(targetPlan.reason)} · Last logged ${dateLabel(last.workoutDate)}.</div>
  </div>`;
  updateExerciseSummary(exerciseEl);
}

async function updateAllExerciseHints() {
  for (const exercise of all(".exercise")) {
    await updateExerciseHint(exercise);
    updateExerciseSummary(exercise);
  }
}

async function useLastSets(exerciseEl) {
  const loaded = await loadLastSetsIntoExercise(exerciseEl, true);
  if (!loaded) toast("No previous sets found for this exercise.");
}

function exerciseHasUserInput(exerciseEl) {
  return Array.from(exerciseEl.querySelectorAll(".set-row")).some((row) => {
    return row.querySelector(".set-weight")?.value.trim()
      || row.querySelector(".set-reps")?.value.trim()
      || row.querySelector(".set-rpe")?.value.trim()
      || row.querySelector(".set-done")?.checked
      || row.querySelector(".set-warmup")?.checked;
  });
}

async function loadLastSetsIntoExercise(exerciseEl, showMessage = false) {
  const name = exerciseEl.querySelector(".exercise-name").value;
  const last = await getLastExercisePerformance(name);
  if (!last || !last.sets?.length) return false;

  const setsEl = exerciseEl.querySelector(".sets");
  setsEl.innerHTML = "";
  last.sets.forEach((set) => {
    setsEl.appendChild(makeSetRow({ ...set, done: false }));
  });
  setRows(exerciseEl);
  updateExerciseSummary(exerciseEl);
  if (showMessage) toast("Last sets loaded.");
  return true;
}

async function autoLoadLastSetsForAllExercises() {
  let loadedCount = 0;
  for (const exercise of all(".exercise")) {
    if (exerciseHasUserInput(exercise)) continue;
    const loaded = await loadLastSetsIntoExercise(exercise, false);
    if (loaded) loadedCount += 1;
  }
  if (loadedCount) toast(`Loaded last sets for ${loadedCount} exercise${loadedCount === 1 ? "" : "s"}.`);
}

function renderCompletionTags() {
  const grid = $("completionTagGrid");
  if (!grid) return;
  grid.innerHTML = DEFAULT_WORKOUT_TAGS.map((tag) => `
    <button class="tag-chip ${completionSelectedTags.has(tag) ? "active" : ""}" type="button" data-completion-tag="${cleanText(tag)}">${cleanText(tag)}</button>
  `).join("");
  grid.querySelectorAll("[data-completion-tag]").forEach((button) => {
    button.addEventListener("click", () => {
      const tag = button.dataset.completionTag;
      if (completionSelectedTags.has(tag)) completionSelectedTags.delete(tag);
      else completionSelectedTags.add(tag);
      haptic(14);
      renderCompletionTags();
    });
  });
}

function showCompletionPopup(workout, summary) {
  completionWorkout = { ...workout };
  completionSelectedTags.clear();
  const modal = $("completionModal");
  if (!modal) return;

  $("completionTitle").textContent = `${workout.type} complete`;
  $("completionSubtitle").textContent = "Saved. Add quick tags if they help explain the session.";
  $("completionStats").innerHTML = `
    <div class="completion-stat"><strong>${summary.sets}</strong><span class="muted small">Work Sets</span></div>
    <div class="completion-stat"><strong>${summary.volume.toLocaleString()}</strong><span class="muted small">Volume</span></div>
    <div class="completion-stat"><strong>${durationLabel(summary.duration)}</strong><span class="muted small">Time</span></div>
  `;
  $("completionHighlights").innerHTML = summary.highlights.map((item) => `
    <div class="completion-highlight"><strong>${cleanText(item.title)}</strong><p class="muted small" style="margin:4px 0 0;">${cleanText(item.text)}</p></div>
  `).join("");
  $("completionCustomNote").classList.add("hidden");
  $("completionCustomNote").value = "";
  renderCompletionTags();
  modal.classList.remove("hidden");
  haptic(45);
}

export function saveDraftSilently() {
  if (!isDatabaseOpen()) return;
  const draft = collectWorkout({ includeEmptySets: true });
  draft.editingWorkoutId = editingWorkoutId;
  draft.activeExerciseIndex = getActiveExerciseIndex();
  draft.savedAt = new Date().toISOString();
  setDraft(draft);
}

export function clearDraftStorage(showMessage = true) {
  removeDraft();
  stopTodayActiveElapsedTimer();
  if (showMessage) toast("Draft cleared.");
}

async function finishCompletionPopup() {
  if (!completionWorkout) {
    $("completionModal")?.classList.add("hidden");
    return;
  }

  const customNote = $("completionCustomNote")?.value.trim() || "";
  const tags = Array.from(completionSelectedTags);
  const updated = { ...completionWorkout, tags };
  if (customNote) updated.notes = updated.notes ? `${updated.notes}\n${customNote}` : customNote;
  await saveWorkoutRecord(updated);
  completionWorkout = null;
  completionSelectedTags.clear();
  $("completionModal")?.classList.add("hidden");
  await renderAll();
  toast(tags.length || customNote ? "Workout details updated." : "Workout saved.");
}

function collectWorkout(options = {}) {
  const includeEmptySets = Boolean(options.includeEmptySets);
  const exercises = all(".exercise").map((exercise) => {
    const sets = Array.from(exercise.querySelectorAll(".set-row")).map((row) => ({
      weight: row.querySelector(".set-weight").value.trim(),
      reps: row.querySelector(".set-reps").value.trim(),
      rpe: row.querySelector(".set-rpe").value.trim(),
      done: row.querySelector(".set-done")?.checked || false,
      warmup: row.querySelector(".set-warmup")?.checked || false
    })).filter((set) => includeEmptySets || set.weight || set.reps || set.rpe);

    return {
      name: exercise.querySelector(".exercise-name").value.trim(),
      notes: exercise.querySelector(".exercise-notes").value.trim(),
      sets
    };
  }).filter((exercise) => exercise.name || exercise.notes || exercise.sets.length);

  const startTime = $("startTime").value;
  const endTime = $("endTime").value;
  const tempWorkout = { startTime, endTime };

  return {
    id: editingWorkoutId || id(),
    date: $("workoutDate").value || today(),
    type: $("workoutType").value,
    startTime,
    endTime,
    durationMinutes: workoutDurationMinutes(tempWorkout),
    notes: $("workoutNotes").value.trim(),
    tags: [],
    exercises,
    createdAt: new Date().toISOString()
  };
}

async function saveWorkout() {
  const liveSessionVisible = !$("sessionView")?.classList.contains("hidden");
  const liveStats = getSessionSetStats();
  if (liveSessionVisible && liveStats.total > 0 && liveStats.completed < liveStats.total) {
    const ok = confirm(`Finish workout with ${liveStats.completed}/${liveStats.total} sets completed?`);
    if (!ok) return;
  }
  if (!$('endTime').value && $('startTime').value) $('endTime').value = timeNow();
  const workout = collectWorkout();
  if (!hasSaveableWorkoutContent(workout)) { toast("Add at least one exercise first."); return; }

  const previousWorkouts = (await getWorkouts()).filter((item) => item.id !== workout.id);
  const previousSameWorkout = previousWorkouts
    .filter((item) => item.type === workout.type)
    .sort((a, b) => b.date.localeCompare(a.date) || (b.createdAt || "").localeCompare(a.createdAt || ""))[0] || null;
  const summary = buildCompletionSummary(workout, previousSameWorkout, previousWorkouts);

  await saveWorkoutRecord(workout);
  clearDraftStorage(false);
  editingWorkoutId = null;
  $("saveWorkout").textContent = "Save Workout";
  $("workoutNotes").value = "";
  $("startTime").value = "";
  $("endTime").value = "";
  await loadWorkoutTemplate();
  await renderAll();
  await updateAllExerciseHints();
  await showTodayView();
  haptic([35, 35, 35]);
  showCompletionPopup(workout, summary);
}

async function loadLastSameWorkout() {
  const type = $("workoutType").value;
  const workouts = (await getWorkouts())
    .filter((workout) => workout.type === type)
    .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  if (!workouts.length) { toast("No previous workout found for this day."); return; }
  const last = workouts[0];
  $("workoutNotes").value = `Loaded from ${dateLabel(last.date)}. `;
  const list = $("exerciseList");
  list.innerHTML = "";
  last.exercises.forEach((exercise) => {
    const resetExercise = {
      ...exercise,
      sets: (exercise.sets || []).map((set) => ({ ...set, done: false }))
    };
    list.appendChild(makeExercise(resetExercise));
  });
  collapseAllButFirstExercise();
  updateSessionTitle();
  await updateAllExerciseHints();
  toast("Previous workout loaded unchecked.");
}

export {
  closeExerciseDetail,
  collapseAllButFirstExercise,
  collapseAllButIndex,
  completeCurrentSet,
  endExerciseDrag,
  finishCompletionPopup,
  formatElapsedClock,
  getActiveExerciseIndex,
  getDraftElapsedSeconds,
  handleSessionPrimaryAction,
  isExerciseDetailOpen,
  loadLastSameWorkout,
  loadWorkoutTemplate,
  makeExercise,
  moveExerciseDrag,
  openExercise,
  renderExerciseDetailView,
  saveWorkout,
  setExerciseDetailTab,
  showSessionView,
  stopSessionElapsedTimer,
  undoLastCompletedSet,
  updateAllExerciseHints,
  updateExerciseHint,
  updateSessionTitle
};
