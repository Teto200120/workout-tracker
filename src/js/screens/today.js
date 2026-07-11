import "../core/globals.js";

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

function getTodayCtaTargetProgress() {
  const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
  const scrollY = window.scrollY || 0;

  // Main morph curve.
  const scrollDriven = clamp((scrollY - 24) / 270);

  // Guarantees the CTA fully compacts when reaching the bottom,
  // even if the page is too short for the normal morph distance.
  const bottomDriven = maxScroll > 0 ? clamp(scrollY / maxScroll) : 0;

  return Math.max(scrollDriven, bottomDriven);
}

function triggerTodayCtaSettleBounce(state) {
  const dock = $("todayFloatingCta");
  if (!dock || !shouldShowTodayFloatingCta()) return;
  if (ctaLastSettledState === state) return;

  ctaLastSettledState = state;
  dock.classList.remove("cta-bounce-expanded", "cta-bounce-compact");
  void dock.offsetWidth;
  dock.classList.add(state === "compact" ? "cta-bounce-compact" : "cta-bounce-expanded");

  clearTimeout(ctaBounceTimeout);
  ctaBounceTimeout = setTimeout(() => {
    dock.classList.remove("cta-bounce-expanded", "cta-bounce-compact");
  }, 360);
}

function syncTodayFloatingCta() {
  const visible = shouldShowTodayFloatingCta();
  $("todayFloatingCta")?.classList.toggle("hidden", !visible);
  if (!visible) {
    document.body.classList.remove("today-cta-compact");
    ctaLastSettledState = null;
  }
  updateTodayCtaCompact();
}

function updateTodayCtaCompact() {
  if (ctaMorphFrame) return;
  ctaMorphFrame = requestAnimationFrame(() => {
    ctaMorphFrame = null;
    applyTodayCtaMorph();
  });
}

function applyTodayCtaMorph() {
  const dock = $("todayFloatingCta");
  const button = $("todayStartWorkout");
  if (!dock || !button) return;

  const visible = shouldShowTodayFloatingCta();
  dock.classList.toggle("hidden", !visible);
  document.body.classList.remove("today-cta-compact");
  if (!visible) return;

  const targetProgress = getTodayCtaTargetProgress();
  const progress = easeInOut(clamp(targetProgress));
  const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 390;

  const fullWidth = Math.min(454, viewportWidth - 44);
  const compactWidth = viewportWidth <= 390 ? 60 : 64;
  const fullHeight = viewportWidth <= 390 ? 74 : 80;
  const compactHeight = viewportWidth <= 390 ? 56 : 58;
  const fullLeft = viewportWidth / 2;
  const compactLeft = viewportWidth - (viewportWidth <= 390 ? 48 : 52);

  const width = lerp(fullWidth, compactWidth, progress);
  const height = lerp(fullHeight, compactHeight, progress);
  const left = lerp(fullLeft, compactLeft, progress);
  const radius = lerp(17, 18, progress);
  const horizontalPadding = lerp(22, 0, progress);
  const gap = lerp(10, 0, progress);
  const fontSize = lerp(18.8, 0, progress);
  const iconSize = lerp(22.5, 24.8, progress);
  const glow = lerp(0.48, 0.34, progress);
  const yLift = lerp(0, 2, progress);

  dock.style.setProperty("width", `${width}px`, "important");
  dock.style.setProperty("left", `${left}px`, "important");
  dock.style.setProperty("transform", "translateX(-50%)", "important");
  dock.style.setProperty("bottom", `calc(${82 + yLift}px + env(safe-area-inset-bottom))`, "important");

  button.style.setProperty("width", `${width}px`, "important");
  button.style.setProperty("min-height", `${height}px`, "important");
  button.style.setProperty("border-radius", `${radius}px`, "important");
  button.style.setProperty("padding", `0 ${horizontalPadding}px`, "important");
  button.style.setProperty("gap", `${gap}px`, "important");
  button.style.setProperty("font-size", `${fontSize}px`, "important");
  button.style.removeProperty("transform");
  button.style.setProperty("box-shadow", `
    0 ${lerp(18, 14, progress)}px ${lerp(42, 30, progress)}px rgba(255, 104, 69, ${lerp(0.42, 0.32, progress)}),
    0 0 ${lerp(42, 28, progress)}px rgba(255, 104, 69, ${glow}),
    inset 0 1px 0 rgba(255, 255, 255, 0.16)
  `, "important");

  const label = button.querySelector(".cta-label");
  const icon = button.querySelector(".cta-icon");
  if (label) {
    label.style.opacity = String(clamp(1 - progress * 1.45));
    label.style.maxWidth = `${Math.max(0, (fullWidth - 120) * (1 - progress))}px`;
    label.style.transform = `translateX(${-10 * progress}px)`;
  }
  if (icon) {
    icon.style.fontSize = `${iconSize}px`;
    icon.style.transform = `translateX(${lerp(0, 1, progress)}px)`;
  }

  if (targetProgress <= 0.015) {
    triggerTodayCtaSettleBounce("expanded");
  } else if (targetProgress >= 0.985) {
    triggerTodayCtaSettleBounce("compact");
  } else {
    ctaLastSettledState = null;
    dock.classList.remove("cta-bounce-expanded", "cta-bounce-compact");
  }
}

function getTodayPlan(date = today()) {
  const day = new Date(`${date}T00:00:00`).getDay();
  const defaults = DEFAULT_SCHEDULE[day] || DEFAULT_SCHEDULE[0];
  const settings = getAppSettings();
  const saved = settings.schedule?.[day] || settings.schedule?.[String(day)] || DEFAULT_APP_SETTINGS.schedule[day] || {};
  const kind = saved.kind || defaults.kind;
  const routine = saved.routine || defaults.routine || "Custom";
  const title = kind === "gym" ? routine : kind === "soccer" ? "Soccer Day" : "Rest Day";
  const note = kind === "gym"
    ? `Suggested from your ${DAY_LABELS[day]} schedule.`
    : kind === "soccer"
      ? "Soccer is treated separately, since you do not track it as a gym workout here."
        : "No gym workout scheduled. Optional recovery, mobility, or custom workout.";
  return { kind, title, routine, note };
}

async function showTodayView() {
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

function mondayFirstWeekDates(baseDate = new Date()) {
  const date = new Date(baseDate);
  date.setHours(0, 0, 0, 0);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  const monday = new Date(date);
  monday.setDate(date.getDate() + diff);
  return Array.from({ length: 7 }, (_, index) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + index);
    return d;
  });
}

function dateKeyFromDate(date) {
  return date.toISOString().slice(0, 10);
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

function inferMuscleTag(exerciseName) {
  const name = String(exerciseName || "").toLowerCase();

  if (/soccer|sprint|agility|conditioning|cooldown|warm.?up/.test(name)) return "Conditioning";
  if (/rear delt|face pull|reverse fly/.test(name)) return "Rear Delts";
  if (/bench|chest|fly|pec|incline/.test(name)) return "Chest";
  if (/tricep|pushdown|skull|dip/.test(name)) return "Triceps";
  if (/shoulder|overhead|lateral|front raise|press/.test(name)) return "Shoulders";
  if (/lat|pulldown|row|back|pull.?up|chin.?up/.test(name)) return "Back";
  if (/bicep|curl|hammer/.test(name)) return "Biceps";
  if (/shrug|trap/.test(name)) return "Traps";
  if (/squat|leg press|quad|lunge|extension/.test(name)) return "Quads";
  if (/romanian|rdl|deadlift|hamstring|leg curl/.test(name)) return "Hamstrings";
  if (/calf/.test(name)) return "Calves";

  return "";
}

function getWorkoutTags(templateName, template) {
  const exercises = template?.exercises || [];
  const tags = [];

  exercises.forEach((exercise) => {
    const tag = inferMuscleTag(exercise);
    if (tag && !tags.includes(tag)) tags.push(tag);
  });

  if (!tags.length) {
    const name = String(templateName || "").toLowerCase();
    if (/chest|push/.test(name)) tags.push("Chest");
    if (/back|pull/.test(name)) tags.push("Back");
    if (/shoulder/.test(name)) tags.push("Shoulders");
    if (/tricep/.test(name)) tags.push("Triceps");
    if (/bicep/.test(name)) tags.push("Biceps");
    if (/leg/.test(name)) tags.push("Legs");
    if (/soccer|conditioning/.test(name)) tags.push("Conditioning");
  }

  return tags.slice(0, 4);
}

function estimateWorkoutDuration(template) {
  const count = template?.exercises?.length || 0;
  if (!count) return "~30 min";
  const minutes = Math.round(Math.min(100, Math.max(30, 12 + count * 8)) / 5) * 5;
  return `~${minutes} min`;
}

function updateTodayGreeting(workouts) {
  const greeting = $("todayGreeting");
  const dateLine = $("todayDateLine");
  const streakBadge = $("todayStreakBadge");
  if (greeting) greeting.innerHTML = `${getGreetingText()}, <span class="accent">Hector</span>!`;
  if (dateLine) dateLine.textContent = prettyTodayDate();
  if (streakBadge) streakBadge.textContent = `🔥 ${getWorkoutStreak(workouts)}`;
}

async function renderTodayView() {
  if (!db || !$("todayView")) return;

  const plan = getTodayPlan(today());
  const templates = await getTemplates();
  const workouts = await getItems("workouts");
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

  renderTodayProgressGlance(workouts.sort((a, b) => b.date.localeCompare(a.date) || (b.createdAt || "").localeCompare(a.createdAt || "")), buildExerciseStats(workouts));
  await renderBackupStatus();
  if (hasActiveDraft) startTodayActiveElapsedTimer(draft);
  else stopTodayActiveElapsedTimer();
  syncTodayFloatingCta();
}

function closeTodayReview() {
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

  const templates = await getTemplates();
  const workouts = await getItems("workouts");
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

async function handleTodayPrimaryCta() {
  if (todayCtaMode === "resume") {
    await resumeWorkoutFromToday();
    return;
  }
  if (todayCtaMode === "hidden") return;
  await startTodayWorkout();
}

async function handleTodayCardAction() {
  haptic(14);
  await startTodayWorkout({ forceNew: true });
}

async function handleTodayWorkoutCardClick(event) {
  if (event?.target?.closest("button, select, option, details, summary, label, input, textarea, a")) return;
  await openTodayWorkoutReview();
}

async function startTodayWorkout(options = {}) {
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
  editingWorkoutId = null;
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

async function resumeWorkoutFromToday() {
  await restoreDraftFromStorage();
}

async function restoreDraftFromStorage() {
  const draft = getDraft();
  if (!draft || !Array.isArray(draft.exercises)) {
    toast("No workout draft found.");
    return false;
  }

  editingWorkoutId = draft.editingWorkoutId || null;
  $("workoutDate").value = draft.date || today();
  await refreshTemplateDropdowns(draft.type);
  $("workoutType").value = draft.type || $("workoutType").value;
  $("startTime").value = draft.startTime || "";
  $("endTime").value = draft.endTime || "";
  $("workoutNotes").value = draft.notes || "";
  $("saveWorkout").textContent = editingWorkoutId ? "Update Workout" : "Save Workout";

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

function stopTodayActiveElapsedTimer() {
  if (todayActiveElapsedInterval) clearInterval(todayActiveElapsedInterval);
  todayActiveElapsedInterval = null;
}

Object.assign(globalThis, { shouldShowTodayFloatingCta, setTodayCtaLabel, getTodayCtaTargetProgress, triggerTodayCtaSettleBounce, syncTodayFloatingCta, updateTodayCtaCompact, applyTodayCtaMorph, getTodayPlan, showTodayView, getGreetingText, prettyTodayDate, mondayFirstWeekDates, dateKeyFromDate, renderTodayWeekProgress, inferMuscleTag, getWorkoutTags, estimateWorkoutDuration, updateTodayGreeting, renderTodayView, closeTodayReview, showTodayReview, todayReviewExerciseList, openTodayWorkoutReview, handleTodayPrimaryCta, handleTodayCardAction, handleTodayWorkoutCardClick, startTodayWorkout, resumeWorkoutFromToday, restoreDraftFromStorage, updateTodayActiveElapsedTimer, startTodayActiveElapsedTimer, stopTodayActiveElapsedTimer });
