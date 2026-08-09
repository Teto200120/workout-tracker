import "../core/globals.js";
import { getDisplayName } from "../application/display-name.js";
import { createActionCoordinator } from "../application/action-coordinator.js";
import {
  claimEducationOffer,
  hideEducationOffer,
  isEducationOfferVisible,
  updateEducationExperience,
} from "../application/education.js";
import { getTodayPlan } from "../application/schedule.js";
import {
  canPresentEducation,
  startCoachMark,
} from "../components/coach-mark.js";
import { refreshTemplateDropdowns } from "../components/routine-selectors.js";
import {
  cleanText,
  haptic,
  motionBehavior,
  replayAnimation,
  timeNow,
  today,
  toast
} from "../core/utils.js";
import { dateKeyFromDate, getWorkoutStreak, mondayFirstWeekDates } from "../domain/schedule.js";
import {
  firstValidationMessage,
  validateWorkoutInput
} from "../domain/input-guardrails.js";
import { estimateWorkoutDuration, getWorkoutTags } from "../domain/training-rules.js";
import {
  buildCompletionSummary,
  completedSets,
  durationLabel,
  totalSets,
  workoutDurationMinutes,
  workoutVolume
} from "../domain/workout-metrics.js";
import { getRoutines, getWorkouts, isDatabaseOpen } from "../storage/indexed-db.js";
import { getDraft } from "../storage/local.js";
import {
  closeExerciseDetail,
  collapseAllButIndex,
  formatElapsedClock,
  getDraftElapsedSeconds,
  isExerciseDetailOpen,
  loadWorkoutTemplate,
  makeExercise,
  saveDraftSilently,
  setEditingWorkoutId,
  setOriginRoutineId,
  showSessionView,
  stopSessionElapsedTimer,
  updateAllExerciseHints
} from "./active-workout.js";

const startWorkoutCoordinator = createActionCoordinator();
const resumeWorkoutCoordinator = createActionCoordinator();

function setTodayWorkoutActionPending(pending) {
  for (const id of [
    "todayStartWorkout",
    "todayCardAction",
    "todayReviewStartWorkout",
    "todayReviewStartAnother",
  ]) {
    if ($(id)) $(id).disabled = pending;
  }
}
let todayActiveElapsedInterval = null;
let todayCtaMode = "start";
let todayEducationBound = false;

const HOME_TOUR_STEPS = Object.freeze([
  {
    target: '[data-education-target="home-workout-card"]',
    title: "Today’s workout",
    body: "This is the workout suggested for today. If you leave an unfinished session, you can resume it here.",
  },
  {
    target: '[data-education-target="home-preview-change"]',
    title: "Preview or change",
    body: "Preview the plan or choose a different saved routine before you start.",
  },
  {
    target: '[data-education-target="home-start-resume"]',
    title: "Start or resume",
    body: "This action starts a new workout or resumes the session saved on this device.",
  },
]);

function educationSaveFeedback(result) {
  if (!result?.saved) {
    toast("Guidance progress could not be saved. The rest of the app is still available.");
  }
}

function renderHomeEducationOffer() {
  const offer = $("homeEducationOffer");
  if (!offer) return;
  if (!$("log")?.classList.contains("active")) {
    offer.classList.add("hidden");
    return;
  }
  if (!isEducationOfferVisible("homeTour")) {
    claimEducationOffer("homeTour");
  }
  offer.classList.toggle(
    "hidden",
    !isEducationOfferVisible("homeTour"),
  );
}

export function startHomeTour({ launcher = null } = {}) {
  hideEducationOffer("homeTour");
  $("homeEducationOffer")?.classList.add("hidden");
  if (!canPresentEducation()) {
    const result = updateEducationExperience("homeTour", "deferred");
    educationSaveFeedback(result);
    toast("The Home tour is still available when the screen is clear.");
    return false;
  }

  const startedState = updateEducationExperience(
    "homeTour",
    "in_progress",
    { lastStep: 0 },
  );
  educationSaveFeedback(startedState);
  const started = startCoachMark({
    steps: HOME_TOUR_STEPS,
    launcher,
    onStepChange: (lastStep) => {
      updateEducationExperience("homeTour", "in_progress", { lastStep });
    },
    onClose: ({ reason, lastStep }) => {
      const result = updateEducationExperience("homeTour", reason, {
        lastStep,
      });
      educationSaveFeedback(result);
    },
  });
  if (!started) {
    toast("The Home tour is still available when its screen items return.");
  }
  return started;
}

export function bindTodayEducation() {
  if (todayEducationBound) return;
  todayEducationBound = true;
  $("homeEducationStart")?.addEventListener("click", (event) => {
    startHomeTour({ launcher: event.currentTarget });
  });
  $("homeEducationSkip")?.addEventListener("click", () => {
    hideEducationOffer("homeTour");
    $("homeEducationOffer")?.classList.add("hidden");
    educationSaveFeedback(
      updateEducationExperience("homeTour", "skipped", { lastStep: 0 }),
    );
  });
}

function shouldShowTodayFloatingCta() {
  const logVisible = $("log")?.classList.contains("active");
  const todayVisible = $("todayView") && !$("todayView").classList.contains("hidden");
  const reviewOpen = $("todayReviewView") && !$("todayReviewView").classList.contains("hidden");
  return Boolean(logVisible && todayVisible && !reviewOpen && todayCtaMode !== "hidden");
}

function setTodayCtaLabel(text) {
  const label = $("todayStartWorkout")?.querySelector(".cta-label");
  if (label) label.textContent = String(text || "Start Workout").replace(/^🔥\s*/, "");
}

function syncTodayFloatingCta() {
  const visible = shouldShowTodayFloatingCta();
  $("todayFloatingCta")?.classList.toggle("hidden", !visible);
}

export async function showTodayView() {
  if (isExerciseDetailOpen()) closeExerciseDetail();
  $("sessionView")?.classList.add("hidden");
  $("todayView")?.classList.remove("hidden");
  stopSessionElapsedTimer();
  await renderTodayView();
  syncTodayFloatingCta();
  replayAnimation($("todayView"), "settle-in", 260);
  window.scrollTo({ top: 0, behavior: motionBehavior() });
}

function getGreetingText() {
  const hour = new Date().getHours();
  if (hour < 12) return "Good Morning";
  if (hour < 18) return "Good Afternoon";
  return "Good Evening";
}

function prettyTodayDate() {
  return new Date().toLocaleDateString(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric"
  });
}

async function renderTodayWeekProgress(workouts) {
  const dots = $("todayWeekDots");
  const count = $("todayWeekCount");
  if (!dots || !count) return;

  const dates = mondayFirstWeekDates(new Date());
  const todayKey = today();
  const workoutDates = new Set(workouts.map((workout) => workout.date));
  const gymDays = dates.filter((date) => getTodayPlan(dateKeyFromDate(date)).kind === "gym");
  const completedGymDays = gymDays.filter((date) => workoutDates.has(dateKeyFromDate(date))).length;
  const dayLabels = ["M", "T", "W", "T", "F", "S", "S"];

  count.textContent = `${completedGymDays}/${gymDays.length || 0} workouts`;
  dots.innerHTML = dates.map((date, index) => {
    const key = dateKeyFromDate(date);
    const plan = getTodayPlan(key);
    const isToday = key === todayKey;
    const complete = workoutDates.has(key);
    const label = complete ? "✓" : dayLabels[index];
    const cls = ["week-dot", complete ? "complete" : "", isToday ? "today" : "", plan.kind === "rest" ? "rest" : "", plan.kind === "soccer" ? "soccer" : ""].filter(Boolean).join(" ");
    return `<span class="${cls}" title="${cleanText(plan.title)}">${label}</span>`;
  }).join("");
}

function updateTodayGreeting(workouts) {
  const greeting = $("todayGreeting");
  const dateLine = $("todayDateLine");
  const streakBadge = $("todayStreakBadge");
  if (greeting) {
    const displayName = getDisplayName();
    greeting.replaceChildren();
    if (displayName) {
      const accent = document.createElement("span");
      accent.className = "accent";
      accent.textContent = displayName;
      greeting.append(`${getGreetingText()}, `, accent, "!");
    } else {
      greeting.textContent = getGreetingText();
    }
  }
  if (dateLine) dateLine.textContent = prettyTodayDate();
  if (streakBadge) streakBadge.textContent = `🔥 ${getWorkoutStreak(workouts, new Date())}`;
}

export async function renderTodayView() {
  if (!isDatabaseOpen() || !$("todayView")) return;

  const plan = getTodayPlan(today());
  const templates = await getRoutines();
  const workouts = await getWorkouts();
  const todaysWorkouts = workouts
    .filter((workout) => workout.date === today())
    .sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
  const draft = getDraft();
  const hasActiveDraft = Boolean(draft && Array.isArray(draft.exercises) && draft.exercises.length && !draft.endTime);
  const completed = todaysWorkouts.length > 0 && !hasActiveDraft;
  const lastToday = todaysWorkouts[0] || null;

  updateTodayGreeting(workouts);
  await renderTodayWeekProgress(workouts);

  const select = $("todayWorkoutSelect");
  if (select) {
    const current = select.value || plan.routine || "Custom";
    select.innerHTML = templates.map((template) => `<option value="${cleanText(template.name)}">${cleanText(template.name)}</option>`).join("");
    const plannedExists = Array.from(select.options).some((option) => option.value === current);
    const fallback = Array.from(select.options).some((option) => option.value === plan.routine) ? plan.routine : (templates[0]?.name || "Custom");
    select.value = plannedExists ? current : fallback;
  }

  const selectedWorkout = $("todayWorkoutSelect")?.value || plan.routine || "Custom";
  const selectedTemplate = templates.find((template) => template.name === selectedWorkout);
  const selectedWorkoutLabel = $("todaySelectedWorkoutLabel");
  if (selectedWorkoutLabel) selectedWorkoutLabel.textContent = selectedWorkout;
  const exerciseCount = selectedTemplate?.exercises?.length || 0;
  const tags = getWorkoutTags(selectedWorkout, selectedTemplate);
  const estimatedDuration = estimateWorkoutDuration(selectedTemplate);
  const activeWorkoutName = draft?.type || selectedWorkout;
  const totalVolume = lastToday ? Math.round(workoutVolume(lastToday)).toLocaleString() : "-";
  const totalWorkSets = lastToday ? totalSets(lastToday) : "-";
  const duration = lastToday ? durationLabel(workoutDurationMinutes(lastToday)) : "-";
  const draftTotalSets = hasActiveDraft ? totalSets(draft) : 0;
  const draftDoneSets = hasActiveDraft ? completedSets(draft) : 0;
  const draftExerciseCount = hasActiveDraft ? draft.exercises.length : 0;

  const card = $("todayWorkoutCard");
  if (card) {
    card.classList.toggle("is-active-workout", hasActiveDraft);
    card.classList.toggle("is-completed", completed);
  }

  const pill = $("todayStatusPill");
  if (pill) {
    pill.className = `today-status-pill ${hasActiveDraft ? "active" : completed ? "complete" : plan.kind === "rest" ? "rest" : ""}`;
    pill.textContent = completed ? "Workout Completed" : plan.kind === "gym" ? "Today’s Workout" : plan.kind === "soccer" ? "Soccer Day" : "Rest Day";
  }

  if (pill && hasActiveDraft) pill.textContent = "Workout Active";

  $("todayTitle").textContent = hasActiveDraft ? activeWorkoutName : completed ? "Completed" : selectedWorkout;
  $("todaySubtitle").textContent = completed
    ? "Today’s workout is saved. You can review progress or start another workout if plans changed."
    : hasActiveDraft
      ? "Session in progress. Resume when you are ready for the next set."
      : plan.kind === "gym"
        ? `${exerciseCount || "Your"} exercises ready from your schedule.`
        : plan.note;

  $("todayMeta").innerHTML = hasActiveDraft
    ? `
      <div class="today-mini-stat"><strong id="todayActiveTimer">${formatElapsedClock(getDraftElapsedSeconds(draft))}</strong><span>active</span></div>
      <div class="today-mini-stat"><strong>${draftDoneSets}/${draftTotalSets || 0}</strong><span>sets</span></div>
      <div class="today-mini-stat"><strong>${draftExerciseCount || "-"}</strong><span>exercises</span></div>
    `
    : completed
    ? `
      <div class="today-mini-stat"><strong>${cleanText(lastToday.type)}</strong><span>Saved</span></div>
      <div class="today-mini-stat"><strong>${totalWorkSets}</strong><span>sets</span></div>
      <div class="today-mini-stat"><strong>${duration}</strong><span>time</span></div>
    `
    : `
      <div class="today-mini-stat"><strong>${exerciseCount || "-"}</strong><span>exercises</span></div>
      <div class="today-mini-stat"><strong>${tags.length || "-"}</strong><span>groups</span></div>
      <div class="today-mini-stat"><strong>${estimatedDuration}</strong><span>estimate</span></div>
    `;

  const pills = $("todayWorkoutPills");
  if (pills) {
    pills.innerHTML = tags.length
      ? tags.map((tag) => `<span class="pill">${cleanText(tag)}</span>`).join("")
      : `<span class="pill">${cleanText(plan.kind === "gym" ? "Gym" : plan.kind === "soccer" ? "Soccer" : "Recovery")}</span>`;
  }

  const durationChip = $("todayDurationChip");
  if (durationChip) durationChip.textContent = hasActiveDraft ? formatElapsedClock(getDraftElapsedSeconds(draft)) : completed ? duration : estimatedDuration;

  const previewHint = $("todayPreviewHint");
  if (previewHint) previewHint.textContent = hasActiveDraft ? `${draftDoneSets}/${draftTotalSets || 0} sets logged` : completed ? `${totalVolume} lb volume saved` : "Tap to preview";

  todayCtaMode = hasActiveDraft ? "resume" : completed ? "hidden" : "start";
  setTodayCtaLabel(hasActiveDraft ? "Resume Workout" : "Start Workout");
  $("todayResumeWorkout").classList.add("hidden");
  const cardAction = $("todayCardAction");
  if (cardAction) {
    cardAction.textContent = completed ? "Start Another Workout" : "";
    cardAction.classList.toggle("hidden", !completed);
  }
  $("todayPlanNote").innerHTML = completed
    ? `Saved today: <strong>${cleanText(lastToday?.type || "Workout")}</strong> · ${totalWorkSets} work sets · ${totalVolume} volume.`
    : `Suggested by schedule: <strong>${cleanText(plan.title)}</strong>. Current choice: <strong>${cleanText(selectedWorkout)}</strong>.`;

  if (hasActiveDraft) {
    $("todayPlanNote").innerHTML = `Active session: <strong>${cleanText(activeWorkoutName)}</strong> - ${draftDoneSets}/${draftTotalSets || 0} sets logged.`;
  }

  if (hasActiveDraft) startTodayActiveElapsedTimer(draft);
  else stopTodayActiveElapsedTimer();
  syncTodayFloatingCta();
  renderHomeEducationOffer();
}

export function closeTodayReview() {
  const view = $("todayReviewView");
  if (!view) return;
  view.classList.add("hidden");
  view.setAttribute("aria-hidden", "true");
  syncTodayFloatingCta();
}

function showTodayReview(title, subtitle, html) {
  const view = $("todayReviewView");
  if (!view) return;
  $("todayReviewTitle").textContent = title;
  $("todayReviewSubtitle").textContent = subtitle;
  $("todayReviewContent").innerHTML = html;
  view.classList.remove("hidden");
  view.setAttribute("aria-hidden", "false");
  syncTodayFloatingCta();
  haptic(12);
}

function todayReviewExerciseList(exercises = []) {
  if (!exercises.length) {
    return `<div class="today-review-exercise"><span>0</span><strong>No exercises planned</strong></div>`;
  }
  return exercises.map((exercise, index) => `
    <div class="today-review-exercise">
      <span>${index + 1}</span>
      <strong>${cleanText(typeof exercise === "string" ? exercise : exercise.name || "Exercise")}</strong>
    </div>
  `).join("");
}

async function openTodayWorkoutReview() {
  const draft = getDraft();
  const hasActiveDraft = Boolean(draft && Array.isArray(draft.exercises) && draft.exercises.length && !draft.endTime);
  if (hasActiveDraft) {
    await resumeWorkoutFromToday();
    return;
  }

  const templates = await getRoutines();
  const workouts = await getWorkouts();
  const todaysWorkouts = workouts
    .filter((workout) => workout.date === today())
    .sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
  const completedWorkout = todaysWorkouts[0] || null;

  if (completedWorkout) {
    const previousWorkouts = workouts.filter((workout) => workout.id !== completedWorkout.id);
    const previousSameWorkout = previousWorkouts
      .filter((workout) => workout.type === completedWorkout.type)
      .sort((a, b) => b.date.localeCompare(a.date) || (b.createdAt || "").localeCompare(a.createdAt || ""))[0] || null;
    const summary = buildCompletionSummary(completedWorkout, previousSameWorkout, previousWorkouts);
    showTodayReview("Workout Review", `${completedWorkout.type} saved`, `
      <div class="today-review-card success">
        <div class="today-review-stat-grid">
          <div class="today-review-stat"><strong>${summary.sets}</strong><span>Work sets</span></div>
          <div class="today-review-stat"><strong>${summary.volume.toLocaleString()}</strong><span>Volume</span></div>
          <div class="today-review-stat"><strong>${durationLabel(summary.duration)}</strong><span>Time</span></div>
        </div>
        <div class="completion-highlights">${summary.highlights.map((item) => `
          <div class="completion-highlight"><strong>${cleanText(item.title)}</strong><p class="muted small" style="margin:4px 0 0;">${cleanText(item.text)}</p></div>
        `).join("")}</div>
        <div class="today-review-exercise-list">${todayReviewExerciseList(completedWorkout.exercises || [])}</div>
        <button class="today-review-secondary" id="todayReviewStartAnother" type="button">Start Another Workout</button>
      </div>
    `);
    $("todayReviewStartAnother")?.addEventListener("click", () => startTodayWorkout({ forceNew: true }));
    return;
  }

  const selected = $("todayWorkoutSelect")?.value || getTodayPlan(today()).routine || "Custom";
  const template = templates.find((item) => item.name === selected);
  const exercises = template?.exercises || [];
  const tags = getWorkoutTags(selected, template);
  showTodayReview("Routine Preview", selected, `
    <div class="today-review-card">
      <div class="today-review-stat-grid">
        <div class="today-review-stat"><strong>${exercises.length || "-"}</strong><span>Exercises</span></div>
        <div class="today-review-stat"><strong>${tags.length || "-"}</strong><span>Groups</span></div>
        <div class="today-review-stat"><strong>${estimateWorkoutDuration(template)}</strong><span>Estimate</span></div>
      </div>
      <div class="today-review-exercise-list">${todayReviewExerciseList(exercises)}</div>
      <button class="today-review-primary" id="todayReviewStartWorkout" type="button">Start Workout</button>
    </div>
  `);
  $("todayReviewStartWorkout")?.addEventListener("click", () => startTodayWorkout());
}

export async function handleTodayPrimaryCta() {
  if (todayCtaMode === "resume") {
    await resumeWorkoutFromToday();
    return;
  }
  if (todayCtaMode === "hidden") return;
  await startTodayWorkout();
}

export async function handleTodayCardAction() {
  haptic(14);
  await startTodayWorkout({ forceNew: true });
}

export async function handleTodayWorkoutCardClick(event) {
  if (event?.target?.closest("button, select, option, details, summary, label, input, textarea, a")) return;
  await openTodayWorkoutReview();
}

async function performStartTodayWorkout(options = {}) {
  const forceNew = Boolean(options.forceNew);
  const draft = getDraft();
  if (!forceNew && draft && Array.isArray(draft.exercises) && draft.exercises.length && !draft.endTime) {
    await resumeWorkoutFromToday();
    return;
  }

  closeTodayReview();
  stopTodayActiveElapsedTimer();
  haptic(18);
  const selected = $("todayWorkoutSelect")?.value || getTodayPlan(today()).routine || "Custom";
  setEditingWorkoutId(null);
  $("workoutDate").value = today();
  await refreshTemplateDropdowns(selected);
  $("workoutType").value = selected;
  $("startTime").value = timeNow();
  $("endTime").value = "";
  $("workoutNotes").value = "";
  $("saveWorkout").textContent = "Save Workout";
  await loadWorkoutTemplate();
  showSessionView();
  saveDraftSilently();
}

async function startTodayWorkout(options = {}) {
  return startWorkoutCoordinator.run(async () => {
    setTodayWorkoutActionPending(true);
    try {
      return await performStartTodayWorkout(options);
    } catch (error) {
      console.info("Workout start failed.", error);
      toast("Could not start the workout. Your existing draft is unchanged.");
      return false;
    } finally {
      setTodayWorkoutActionPending(false);
    }
  }).promise;
}

export async function resumeWorkoutFromToday() {
  return resumeWorkoutCoordinator.run(async () => {
    setTodayWorkoutActionPending(true);
    try {
      return await restoreDraftFromStorage();
    } catch (error) {
      console.info("Workout draft restore failed.", error);
      toast("Could not restore the workout draft. Stored data was left unchanged.");
      return false;
    } finally {
      setTodayWorkoutActionPending(false);
    }
  }).promise;
}

export async function restoreDraftFromStorage() {
  const draft = getDraft();
  if (!draft || !Array.isArray(draft.exercises)) {
    toast("No workout draft found.");
    return false;
  }
  const validation = validateWorkoutInput(draft, {
    allowHistoricalRpeZero: true,
    allowIncompleteExerciseNames: true,
    todayValue: today(),
  });
  const unsafeCollection = validation.errors.find(
    (error) => error.code === "collection_too_large",
  );
  if (unsafeCollection) {
    toast(firstValidationMessage({ errors: [unsafeCollection], warnings: [] }));
    return false;
  }

  setEditingWorkoutId(draft.editingWorkoutId || null);
  setOriginRoutineId(draft.originRoutineId);
  $("workoutDate").value = draft.date || today();
  await refreshTemplateDropdowns(draft.type);
  $("workoutType").value = draft.type || $("workoutType").value;
  $("startTime").value = draft.startTime || "";
  $("endTime").value = draft.endTime || "";
  $("workoutNotes").value = draft.notes || "";
  $("saveWorkout").textContent = draft.editingWorkoutId ? "Update Workout" : "Save Workout";

  const list = $("exerciseList");
  list.innerHTML = "";
  draft.exercises.forEach((exercise) => list.appendChild(makeExercise(exercise)));
  collapseAllButIndex(Number.isInteger(draft.activeExerciseIndex) ? draft.activeExerciseIndex : 0);
  await updateAllExerciseHints();
  switchScreen("log");
  showSessionView();
  toast("Draft restored.");
  return true;
}

function updateTodayActiveElapsedTimer(draft = getDraft()) {
  const target = $("todayActiveTimer");
  const chip = $("todayDurationChip");
  if (!draft || !target) return;
  const label = formatElapsedClock(getDraftElapsedSeconds(draft));
  target.textContent = label;
  if (chip) chip.textContent = label;
}

function startTodayActiveElapsedTimer(draft) {
  if (todayActiveElapsedInterval) clearInterval(todayActiveElapsedInterval);
  todayActiveElapsedInterval = null;
  if (!draft?.startTime) return;
  updateTodayActiveElapsedTimer(draft);
  todayActiveElapsedInterval = setInterval(() => updateTodayActiveElapsedTimer(draft), 1000);
}

export function stopTodayActiveElapsedTimer() {
  if (todayActiveElapsedInterval) clearInterval(todayActiveElapsedInterval);
  todayActiveElapsedInterval = null;
}

Object.assign(globalThis, { closeTodayReview, showTodayView, stopTodayActiveElapsedTimer, syncTodayFloatingCta });
