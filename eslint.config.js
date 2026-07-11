import js from "@eslint/js";
import globals from "globals";

const transitionalAppGlobals = Object.fromEntries(
  `DB_NAME DB_VERSION STORES defaultTemplates DEFAULT_SCHEDULE SETTINGS_KEY
  BACKUP_META_KEY DAY_LABELS DEFAULT_APP_SETTINGS db timerInterval timerEnd
  sessionElapsedInterval todayActiveElapsedInterval todayCtaMode exerciseDragState
  activeExerciseDetailEl exerciseDetailTab exerciseDetailRenderToken
  exerciseFocusScrollToken templateDraftExercises editingTemplateId editingWorkoutId
  completionWorkout completionSelectedTags ctaMorphFrame ctaLastSettledState
  ctaBounceTimeout $ all addSetToExercise addTemplateExercise adjustCurrentSetValue
  applyAppSettings applyTodayCtaMorph autoLoadLastSetsForAllExercises averageRpe
  backupAgeText bestHistoricalSetForExercise bindControlValueInput bindEvents
  bindExerciseDetailControls buildCompletionSummary buildExerciseStats
  buildProgressInsights buildSetSummary buildTargetFromLastSets clamp cleanText
  clearAllData clearDraftStorage clearStore clearTemplateDraft cloneDefaultSettings
  closeExerciseDetail closeTodayReview collapseAllButFirstExercise
  collapseAllButIndex collectWorkout compactSetSummary compactTargetReps
  compactTargetSummary completeCurrentSet completedSetChips completedSets
  controlInputHtml dateKeyFromDate dateLabel daysSinceBackup deleteItem
  deleteTemplate deleteWorkout durationLabel easeInOut editTemplate editWorkout
  elapsedSecondsFromClock endExerciseDrag ensureCurrentSetDefaults
  estimateWorkoutDuration exerciseHasUserInput exportData fallbackWeekDates
  finishAndNextExercise finishCompletionPopup focusExerciseInSessionView
  formatCommittedControlValue formatElapsedClock formatPrimaryWeight formatRepsList
  formatSignedNumber getActiveExercise getActiveExerciseIndex getAppSettings
  getBackupMeta getBestSet getCurrentSetDisplayValues getCurrentSetMeta
  getCurrentSetRow getDoneWorkSetRows getDraft getDraftElapsedSeconds
  getDragAfterElement getExerciseEquipment getExerciseGuideData getExercisePr
  getExerciseProfile getExerciseSetProgress getExerciseTrendInsight getGoals
  getGreetingText getItems getLastExercisePerformance getNumericInputValue
  getProgressionRecommendation getRecentPrInsights getSessionElapsedSeconds
  getSessionFocusBand getSessionSetStats getSetFieldInput getSortedExerciseHistory
  getTemplates getTodayCtaTargetProgress getTodayPlan getVolumeInsight
  getWeeklyActivityData getWorkoutStatsSummary getWorkoutStreak getWorkoutTags
  getWorkSetRows goToExerciseOffset handleSessionPrimaryAction handleTodayCardAction
  handleTodayPrimaryCta handleTodayWorkoutCardClick haptic highestRpe iconCheck
  iconChevron iconChevronRight iconGrip iconInfo iconMinus iconPlayOutline iconPlus
  id importData inferMuscleTag init isExerciseComplete isExerciseDetailOpen lerp
  loadLastSameWorkout loadLastSetsIntoExercise loadWorkoutTemplate makeExercise
  makeSetRow metricLabel metricUnit mondayFirstWeekDates motionBehavior moveExerciseDrag
  normalizeRange openDatabase openExercise openExerciseDetail openProfileSubpage
  openStatsDetail openTodayWorkoutReview prefersReducedMotion prettyTodayDate
  previousWeightForSet refreshTemplateDropdowns removeLastSetFromExercise
  removeTemplateDraftExercise renderAll renderBackupStatus renderCompletionTags
  renderDashboard renderExerciseDetails renderExerciseDetailView
  renderExerciseGuideContent renderExerciseLogContent renderExerciseProgress
  renderExerciseSelectors renderGoals renderGuideList renderHistory renderInsightList
  renderPersonalRecordCard renderPersonalRecords renderPersonalRecordsPreview
  renderProfile renderRecentSessionsPreview renderSettings renderStatsGrid
  renderStrengthSnapshot renderTemplateDraft renderTemplates renderTodayProgressGlance
  renderTodayView renderTodayWeekProgress renderWeeklyActivity
  renderWeeklyActivityDetailList renderWeeklyActivityStrip renderWorkout
  renderWorkoutCard renderWorkoutExerciseChips renderWorkoutStats renderWorkoutTags
  renumberExerciseCards replayAnimation resetAppSettings resetTemplates
  restoreDraftFromStorage resumeWorkoutFromToday sanitizeControlInputValue
  saveDraftSilently saveGoalsToStorage saveItem saveSettingsFromForm saveTemplate
  saveWorkout scheduleExerciseFocus scrollInputIntoView seedDefaultTemplates
  setAppSettings setBackupMeta setControlText setExerciseCollapsed
  setExerciseDetailTab setRows setTodayCtaLabel shouldShowTodayFloatingCta
  showCompletionPopup showSessionView showTodayReview showTodayView
  startExerciseDrag startRoutine startSessionElapsedTimer startTimer
  startTodayActiveElapsedTimer startTodayWorkout stopSessionElapsedTimer stopTimer
  stopTodayActiveElapsedTimer store switchScreen syncSessionUi
  syncTodayFloatingCta targetRepsBySetText timeNow timeToMinutes toast today
  todayReviewExerciseList toggleExerciseCollapse toggleExerciseGuide totalSets
  triggerTodayCtaSettleBounce undoLastCompletedSet updateAllExerciseHints
  updateCurrentSetPanel updateExerciseDetailCompleteButton updateExerciseDetailHeader
  updateExerciseFlowButtons updateExerciseHint updateExerciseSummary
  updateSessionElapsedTimer updateSessionPrimaryAction updateSessionProgress
  updateSessionTitle updateTimer updateTodayActiveElapsedTimer updateTodayCtaCompact
  updateTodayGreeting useLastSets workoutDurationMinutes workoutVolume workSetsOnly
  writeCurrentSetValueFromControl`
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
