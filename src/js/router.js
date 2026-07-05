import "./core/globals.js";

const SCREEN_GROUPS = {
  stats: ["dashboard", "history"],
  home: ["log"],
  profile: ["profile"],
  templates: ["templates"],
  backup: ["backup"],
  settings: ["settings"]
};

const SCREEN_ALIASES = {
  dashboard: "stats",
  history: "stats",
  log: "home"
};

function getScreenDestination(name) {
  const destination = SCREEN_ALIASES[name] || name || "home";
  return SCREEN_GROUPS[destination] ? destination : "home";
}

function getActiveNavDestination(destination) {
  return ["templates", "backup", "settings"].includes(destination) ? "profile" : destination;
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

function switchScreen(name) {
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
  updateTodayCtaCompact();
  replayAnimation($(activeScreens[0]), "settle-in", 260);
  renderAll();
}

function bindEvents() {
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
  $("saveSettings").addEventListener("click", saveSettingsFromForm);
  $("resetSettings").addEventListener("click", resetAppSettings);
  $("settingsAnimations").addEventListener("change", applyAppSettings);
  $("settingsHaptics").addEventListener("change", () => haptic(20));
  $("sessionBack").addEventListener("click", showTodayView);
  $("sessionSaveTop").addEventListener("click", saveWorkout);
  $("exerciseDetailBack")?.addEventListener("click", closeExerciseDetail);
  all("[data-detail-tab]").forEach((button) => button.addEventListener("click", () => setExerciseDetailTab(button.dataset.detailTab)));
  $("exerciseDetailCompleteSet")?.addEventListener("click", () => {
    if (!activeExerciseDetailEl) return;
    completeCurrentSet(activeExerciseDetailEl);
    renderExerciseDetailView();
  });
  $("completionDone").addEventListener("click", finishCompletionPopup);
  $("completionCustomNoteButton").addEventListener("click", () => {
    const note = $("completionCustomNote");
    note.classList.toggle("hidden");
    if (!note.classList.contains("hidden")) note.focus();
  });
  $("workoutType").addEventListener("change", updateSessionTitle);
  all("[data-timer]").forEach((button) => button.addEventListener("click", () => startTimer(Number(button.dataset.timer))));
  $("stopTimer").addEventListener("click", stopTimer);
  $("loadTemplate").addEventListener("click", async () => {
    if (editingWorkoutId && !confirm("You are editing a saved workout. Loading a template will replace the visible exercises. Continue?")) return;
    await loadWorkoutTemplate();
    saveDraftSilently();
  });
  $("loadLastWorkout").addEventListener("click", async () => { await loadLastSameWorkout(); saveDraftSilently(); });
  $("addExercise").addEventListener("click", async () => {
    const exercise = makeExercise();
    $("exerciseList").appendChild(exercise);
    await updateExerciseHint(exercise);
    openExercise(exercise, true);
    saveDraftSilently();
  });
  $("saveWorkout").addEventListener("click", handleSessionPrimaryAction);
  $("sessionUndoSet")?.addEventListener("click", undoLastCompletedSet);
  $("historyFilter").addEventListener("change", renderHistory);
  $("exerciseSearch").addEventListener("input", renderHistory);
  $("progressExercise").addEventListener("change", async () => renderExerciseProgress(buildExerciseStats(await getItems("workouts"))));
  $("progressMetric").addEventListener("change", async () => renderExerciseProgress(buildExerciseStats(await getItems("workouts"))));
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

  $("log").addEventListener("input", () => saveDraftSilently());
  $("saveGoals").addEventListener("click", saveGoalsToStorage);
}

async function renderAll() {
  await renderDashboard();
  await renderHistory();
  await renderTemplates();
  await renderProfile();
  await renderTodayView();
  await renderSettings();
  await renderBackupStatus();
}

async function init() {
  applyAppSettings();
  $("workoutDate").value = today();
  $("startTime").value = "";
  $("endTime").value = "";
  await openDatabase();
  await seedDefaultTemplates();
  bindEvents();
  await refreshTemplateDropdowns();
  await loadWorkoutTemplate();
  await renderAll();
  await showTodayView();

  if (getDraft()) {
    toast("Unsaved workout draft available. Tap Resume Draft if needed.");
  }
}

Object.assign(globalThis, { switchScreen, openProfileSubpage, bindEvents, renderAll, init });
