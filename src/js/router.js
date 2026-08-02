import "./core/globals.js";
import { ensureCurrentApplicationSchema } from "./application/data-schema.js";
import { isOnboardingRequired } from "./application/display-name.js";
import {
  consumeEducationReplay,
  getEducationExperience,
  requestEducationReplay,
  resetAllGuidance,
  updateEducationExperience,
} from "./application/education.js";
import { bindExercisePicker } from "./components/exercise-picker.js";
import { refreshTemplateDropdowns } from "./components/routine-selectors.js";
import {
  applyAppSettings,
  renderSettings,
  resetAppSettings,
  saveSettingsFromForm
} from "./core/settings.js";
import { haptic, motionBehavior, replayAnimation, scrollInputIntoView, today, toast } from "./core/utils.js";
import { getWorkouts, openDatabase, seedDefaultTemplates } from "./storage/indexed-db.js";
import { getDraft } from "./storage/local.js";
import {
  clearDraftStorage,
  closeExerciseDetail,
  completeActiveExerciseDetailSet,
  addExerciseToWorkout,
  bindActiveWorkoutGuardrails,
  endExerciseDrag,
  finishCompletionPopup,
  getEditingWorkoutId,
  handleSessionPrimaryAction,
  loadLastSameWorkout,
  loadWorkoutTemplate,
  moveExerciseDrag,
  saveDraftSilently,
  saveWorkout,
  setExerciseDetailTab,
  syncRpePreferenceUi,
  undoLastCompletedSet,
  updateAllExerciseHints,
  getCurrentWorkoutExerciseNames,
  updateSessionTitle
} from "./screens/active-workout.js";
import { clearAllData, exportData, importData, renderBackupStatus } from "./screens/backup.js";
import { bindHistoryActions, renderHistory } from "./screens/history.js";
import { bindProfileActions, renderProfile } from "./screens/profile.js";
import {
  bindOnboarding,
  showApplicationShell,
  showOnboarding,
} from "./screens/onboarding.js";
import { buildExerciseStats, renderDashboard, renderExerciseProgress, saveGoalsToStorage } from "./screens/progress.js";
import {
  addTemplateExercise,
  bindRoutineActions,
  clearTemplateDraft,
  renderTemplates,
  resetTemplates,
  scheduleRoutineEditorEducation,
  saveTemplate
} from "./screens/routines.js";
import {
  closeTodayReview,
  handleTodayCardAction,
  handleTodayPrimaryCta,
  handleTodayWorkoutCardClick,
  bindTodayEducation,
  renderTodayView,
  restoreDraftFromStorage,
  resumeWorkoutFromToday,
  showTodayView,
  startHomeTour,
  updateTodayCtaCompact
} from "./screens/today.js";

const SCREEN_GROUPS = {
  stats: ["dashboard"],
  history: ["history"],
  statsWeekly: ["statsWeekly"],
  statsStrength: ["statsStrength"],
  statsRecords: ["statsRecords"],
  statsWorkoutStats: ["statsWorkoutStats"],
  statsGoals: ["statsGoals"],
  home: ["log"],
  profile: ["profile"],
  templates: ["templates"],
  backup: ["backup"],
  settings: ["settings"]
};

const SCREEN_ALIASES = {
  dashboard: "stats",
  log: "home"
};

const STATS_DETAIL_DESTINATIONS = new Set([
  "history",
  "statsWeekly",
  "statsStrength",
  "statsRecords",
  "statsWorkoutStats",
  "statsGoals"
]);

let applicationStarted = false;
let eventsBound = false;

function settleScreenLayout() {
  return new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(resolve));
  });
}

function educationActionFeedback(result) {
  if (!result?.saved) {
    toast("Guidance progress could not be saved. You can keep using the app.");
  }
}

function setInlineEducationVisibility(experienceId, elementId, visible) {
  const tip = $(elementId);
  if (!tip) return;
  if (!visible) {
    tip.classList.add("hidden");
    return;
  }
  const experience = getEducationExperience(experienceId);
  if (!["unseen", "offered"].includes(experience?.status)) {
    tip.classList.add("hidden");
    return;
  }
  if (experience.status === "unseen") {
    updateEducationExperience(experienceId, "offered", { lastStep: 0 });
  }
  consumeEducationReplay(experienceId);
  tip.classList.remove("hidden");
}

function coordinateScreenEducation(destination) {
  setInlineEducationVisibility(
    "statsBasics",
    "statsEducationTip",
    destination === "stats",
  );
  setInlineEducationVisibility(
    "historyBasics",
    "historyEducationTip",
    destination === "history",
  );
  if (destination === "templates") scheduleRoutineEditorEducation();
}

function getScreenDestination(name) {
  const destination = SCREEN_ALIASES[name] || name || "home";
  return SCREEN_GROUPS[destination] ? destination : "home";
}

function getActiveNavDestination(destination) {
  if (STATS_DETAIL_DESTINATIONS.has(destination)) return "stats";
  return ["templates", "backup", "settings"].includes(destination) ? "profile" : destination;
}

function openStatsDetail(destination) {
  switchScreen(destination);
  setTimeout(() => window.scrollTo({ top: 0, behavior: "auto" }), 0);
}

function openProfileSubpage(destination, focusId = "") {
  switchScreen(destination);
  if (!focusId) return;

  setTimeout(() => {
    const target = $(focusId);
    if (!target) return;
    target.scrollIntoView({ behavior: motionBehavior(), block: "start" });
  }, 80);
}

export function switchScreen(name) {
  const destination = getScreenDestination(name);
  const activeScreens = SCREEN_GROUPS[destination];
  const activeNav = getActiveNavDestination(destination);

  all(".tab").forEach((tab) => {
    const isActive = tab.dataset.screen === activeNav;
    tab.classList.toggle("active", isActive);
    if (isActive) tab.setAttribute("aria-current", "page");
    else tab.removeAttribute("aria-current");
  });
  all(".screen").forEach((screen) => screen.classList.toggle("active", activeScreens.includes(screen.id)));
  document.body.dataset.screenTransitioning = "true";
  updateTodayCtaCompact();
  replayAnimation($(activeScreens[0]), "settle-in", 260);
  return renderAll()
    .then(settleScreenLayout)
    .then(() => {
      document.body.dataset.screenTransitioning = "false";
      coordinateScreenEducation(destination);
    })
    .catch((error) => {
      document.body.dataset.screenTransitioning = "false";
      console.info("Screen render failed.", error);
      toast("This screen could not be refreshed. Try again.");
    });
}

async function handleEducationAction(event) {
  const action = event.currentTarget.dataset.educationAction;
  const launcher = event.currentTarget;
  if (action === "reset-all") {
    if (!confirm("Reset all guidance? Contextual tips will reappear when you reach them.")) return;
    const result = resetAllGuidance();
    educationActionFeedback(result);
    $("statsEducationTip")?.classList.add("hidden");
    $("historyEducationTip")?.classList.add("hidden");
    toast(
      result.saved
        ? "Guidance reset. Tips will reappear in context."
        : "Guidance reset for this session, but it could not be saved.",
    );
    return;
  }

  const replayMap = {
    "replay-home": "homeTour",
    "replay-active": "activeWorkoutBasics",
    "replay-routine": "routineEditorBasics",
    "explain-rpe": "rpeBasics",
    "replay-history": "historyBasics",
    "replay-stats": "statsBasics",
    "replay-guide": "exerciseGuideBasics",
  };
  const experienceId = replayMap[action];
  if (!experienceId) return;
  const replayResult = requestEducationReplay(experienceId);
  educationActionFeedback(replayResult);

  if (action === "replay-home") {
    await switchScreen("home");
    consumeEducationReplay("homeTour");
    startHomeTour({ launcher });
    return;
  }
  if (action === "replay-active") {
    await switchScreen("home");
    toast("Start or resume a workout to replay the Active Workout guide.");
    return;
  }
  if (action === "replay-routine") {
    await switchScreen("templates");
    scheduleRoutineEditorEducation({ launcher });
    return;
  }
  if (action === "explain-rpe") {
    const dialog = $("rpeHelpDialog");
    if (dialog && !dialog.open) dialog.showModal();
    return;
  }
  if (action === "replay-history") {
    await switchScreen("history");
    coordinateScreenEducation("history");
    return;
  }
  if (action === "replay-stats") {
    await switchScreen("stats");
    coordinateScreenEducation("stats");
    return;
  }
  if (action === "replay-guide") {
    toast("The Guide note will appear when you open an Exercise Guide.");
  }
}

function bindEvents() {
  if (eventsBound) return;
  eventsBound = true;
  bindActiveWorkoutGuardrails();
  bindTodayEducation();
  window.addEventListener("scroll", updateTodayCtaCompact, { passive: true });
  window.addEventListener("resize", updateTodayCtaCompact, { passive: true });
  document.addEventListener("focusin", (event) => {
    if (event.target.matches("input, textarea")) scrollInputIntoView(event.target);
  });
  document.addEventListener("pointermove", moveExerciseDrag);
  document.addEventListener("pointerup", endExerciseDrag);
  document.addEventListener("pointercancel", endExerciseDrag);
  document.addEventListener("mousemove", moveExerciseDrag);
  document.addEventListener("mouseup", endExerciseDrag);
  all(".tab").forEach((tab) => tab.addEventListener("click", () => switchScreen(tab.dataset.screen)));
  all("[data-stats-detail]").forEach((button) => {
    button.addEventListener("click", () => openStatsDetail(button.dataset.statsDetail));
    button.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      event.preventDefault();
      openStatsDetail(button.dataset.statsDetail);
    });
  });
  all("[data-stats-back]").forEach((button) => {
    button.addEventListener("click", () => {
      switchScreen("stats");
      setTimeout(() => window.scrollTo({ top: 0, behavior: "auto" }), 0);
    });
  });
  $("todayStartWorkout")?.addEventListener("click", handleTodayPrimaryCta);
  $("todayWorkoutCard")?.addEventListener("click", handleTodayWorkoutCardClick);
  $("todayCardAction")?.addEventListener("click", handleTodayCardAction);
  $("todayReviewBack")?.addEventListener("click", closeTodayReview);
  $("todayResumeWorkout").addEventListener("click", resumeWorkoutFromToday);
  $("todayExportBackup")?.addEventListener("click", exportData);
  $("todayWorkoutSelect").addEventListener("change", renderTodayView);
  all("[data-open-settings]").forEach((button) => button.addEventListener("click", () => switchScreen("settings")));
  all("[data-profile-back]").forEach((button) => button.addEventListener("click", () => switchScreen("profile")));
  all("[data-profile-target]").forEach((button) => {
    button.addEventListener("click", () => openProfileSubpage(button.dataset.profileTarget, button.dataset.profileFocus));
  });
  $("settingsBack").addEventListener("click", () => switchScreen("profile"));
  $("saveSettings").addEventListener("click", async () => {
    await saveSettingsFromForm();
    syncRpePreferenceUi();
    await renderTodayView();
    await updateAllExerciseHints();
  });
  $("resetSettings").addEventListener("click", async () => {
    await resetAppSettings();
    syncRpePreferenceUi();
    await renderTodayView();
    await updateAllExerciseHints();
  });
  $("settingsAnimations").addEventListener("change", applyAppSettings);
  $("settingsHaptics").addEventListener("change", () => haptic(20));
  $("sessionBack").addEventListener("click", showTodayView);
  $("sessionSaveTop").addEventListener("click", saveWorkout);
  $("exerciseDetailBack")?.addEventListener("click", closeExerciseDetail);
  all("[data-detail-tab]").forEach((button) => button.addEventListener("click", () => setExerciseDetailTab(button.dataset.detailTab)));
  $("exerciseDetailCompleteSet")?.addEventListener("click", () => {
    completeActiveExerciseDetailSet();
  });
  $("completionDone").addEventListener("click", finishCompletionPopup);
  $("completionCustomNoteButton").addEventListener("click", () => {
    const note = $("completionCustomNote");
    note.classList.toggle("hidden");
    if (!note.classList.contains("hidden")) note.focus();
  });
  $("workoutType").addEventListener("change", updateSessionTitle);
  $("loadTemplate").addEventListener("click", async () => {
    if (getEditingWorkoutId() && !confirm("You are editing a saved workout. Loading a template will replace the visible exercises. Continue?")) return;
    await loadWorkoutTemplate();
    saveDraftSilently();
  });
  $("loadLastWorkout").addEventListener("click", async () => { await loadLastSameWorkout(); saveDraftSilently(); });
  bindExercisePicker({
    trigger: $("addExercise"),
    context: "active-workout",
    mode: "add",
    getCurrentExerciseNames: getCurrentWorkoutExerciseNames,
    onSelect: ({ name }) => addExerciseToWorkout(name)
  });
  $("saveWorkout").addEventListener("click", handleSessionPrimaryAction);
  $("sessionUndoSet")?.addEventListener("click", undoLastCompletedSet);
  $("historyFilter").addEventListener("change", renderHistory);
  $("exerciseSearch").addEventListener("input", renderHistory);
  $("progressExercise").addEventListener("change", async () => renderExerciseProgress(buildExerciseStats(await getWorkouts())));
  $("progressMetric").addEventListener("change", async () => renderExerciseProgress(buildExerciseStats(await getWorkouts())));
  $("exportData").addEventListener("click", exportData);
  $("importData").addEventListener("click", () => $("importFile").click());
  $("importFile").addEventListener("change", (event) => {
    const file = event.target.files[0];
    if (file) importData(file);
    event.target.value = "";
  });
  $("clearData").addEventListener("click", clearAllData);
  $("addTemplateExercise").addEventListener("click", addTemplateExercise);
  $("templateExerciseInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") { event.preventDefault(); addTemplateExercise(); }
  });
  $("saveTemplate").addEventListener("click", saveTemplate);
  $("clearTemplateDraft").addEventListener("click", clearTemplateDraft);
  $("resetTemplates").addEventListener("click", resetTemplates);
  $("restoreDraft").addEventListener("click", restoreDraftFromStorage);
  $("clearDraft").addEventListener("click", () => clearDraftStorage(true));

  all("[data-inline-education-complete]").forEach((button) => {
    button.addEventListener("click", () => {
      const experienceId = button.dataset.inlineEducationComplete;
      const result = updateEducationExperience(experienceId, "completed", {
        lastStep: 0,
      });
      educationActionFeedback(result);
      button.closest(".education-inline-tip")?.classList.add("hidden");
    });
  });
  all("[data-education-action]").forEach((button) => {
    button.addEventListener("click", handleEducationAction);
  });
  $("rpeHelpClose")?.addEventListener("click", () => {
    $("rpeHelpDialog")?.close();
  });
  $("rpeHelpDialog")?.addEventListener("cancel", (event) => {
    event.preventDefault();
    event.currentTarget.close();
  });

  $("sessionView").addEventListener("input", () => saveDraftSilently());
  $("saveGoals").addEventListener("click", saveGoalsToStorage);
  bindHistoryActions();
  bindRoutineActions();
  bindProfileActions({
    onDisplayNameSaved: async () => {
      await renderTodayView();
      await renderProfile();
    }
  });
}

export async function renderAll() {
  await renderDashboard();
  await renderHistory();
  await renderTemplates();
  await renderProfile();
  await renderTodayView();
  await renderSettings();
  await renderBackupStatus();
}

export async function init() {
  await openDatabase();
  await ensureCurrentApplicationSchema();
  applyAppSettings();
  bindOnboarding({ onComplete: activateApplication });
  if (isOnboardingRequired()) {
    showOnboarding({ resetInput: true });
    return;
  }
  await activateApplication();
}

async function activateApplication() {
  const firstStart = !applicationStarted;
  if (firstStart) {
    $("workoutDate").value = today();
    $("startTime").value = "";
    $("endTime").value = "";
    await seedDefaultTemplates();
    bindEvents();
    applicationStarted = true;
  }
  await refreshTemplateDropdowns();
  if (firstStart) await loadWorkoutTemplate();
  await renderAll();
  await showTodayView();
  showApplicationShell();

  if (firstStart && getDraft()) {
    toast("Unsaved workout draft available. Tap Resume Draft if needed.");
  }
}

Object.assign(globalThis, { renderAll, switchScreen });
