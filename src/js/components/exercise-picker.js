import {
  getCatalogStatus,
  loadCatalog,
} from "../catalog/catalog-loader.js";
import {
  getCatalogFilterOptions,
  mergeLocalAndCatalogResults,
  resolveCatalogCanonicalName,
} from "../catalog/catalog-search.js";
import { buildCatalogPreviewSummary } from "../catalog/exercise-guide-adapter.js";
import { DEFAULT_TEMPLATES } from "../core/constants.js";
import { buildExerciseOptions } from "../domain/exercise-options.js";
import {
  INPUT_LIMITS,
  firstValidationMessage,
  validateExerciseName,
  validateSearchText,
} from "../domain/input-guardrails.js";
import { getRoutines, getWorkouts } from "../storage/indexed-db.js";

const CATALOG_RESULT_LIMIT = INPUT_LIMITS.catalogResults;
const LOCAL_RESULT_LIMIT = 3;
const PICKER_CONTEXTS = new Set(["active-workout", "routine"]);
const PICKER_MODES = new Set(["add", "replace"]);

let pickerController = null;

export function createExercisePickerSelectionSession({
  context = "active-workout",
  mode = "add",
  onSelect,
  onCancel,
} = {}) {
  if (!PICKER_CONTEXTS.has(context)) {
    throw new TypeError(`Unsupported exercise-picker context: ${context}`);
  }
  if (!PICKER_MODES.has(mode)) {
    throw new TypeError(`Unsupported exercise-picker mode: ${mode}`);
  }
  let settled = false;

  return Object.freeze({
    context,
    mode,
    get settled() {
      return settled;
    },
    select(name) {
      if (settled || typeof name !== "string") {
        return { accepted: false, result: null, promise: Promise.resolve(false) };
      }
      const validation = validateExerciseName(name);
      if (!validation.valid) {
        return { accepted: false, result: null, promise: Promise.resolve(false) };
      }
      settled = true;
      const result = Object.freeze({ name: validation.normalized });
      return {
        accepted: true,
        result,
        promise: Promise.resolve(onSelect?.(result)).then(() => true),
      };
    },
    cancel() {
      if (settled) return { accepted: false, promise: Promise.resolve(false) };
      settled = true;
      return {
        accepted: true,
        promise: Promise.resolve(onCancel?.()).then(() => true),
      };
    },
  });
}

function appendText(parent, tagName, text, className = "") {
  const element = document.createElement(tagName);
  if (className) element.className = className;
  element.textContent = text;
  parent.appendChild(element);
  return element;
}

function displayValue(value) {
  return String(value || "")
    .split(" ")
    .filter(Boolean)
    .map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join(" ");
}

function createExercisePickerController() {
  const dialog = document.querySelector("#exercisePicker");
  const title = document.querySelector("#exercisePickerTitle");
  const description = document.querySelector("#exercisePickerDescription");
  const browsePanel = document.querySelector("#exercisePickerBrowse");
  const createPanel = document.querySelector("#exercisePickerCreate");
  const previewPanel = document.querySelector("#exercisePickerPreview");
  const searchInput = document.querySelector("#exercisePickerSearch");
  const optionsContainer = document.querySelector("#exercisePickerOptions");
  const resultCount = document.querySelector("#exercisePickerResultCount");
  const emptyState = document.querySelector("#exercisePickerEmpty");
  const catalogStatus = document.querySelector("#exercisePickerCatalogStatus");
  const filterToggle = document.querySelector("#exercisePickerFilterToggle");
  const filterCount = document.querySelector("#exercisePickerFilterCount");
  const filters = document.querySelector("#exercisePickerFilters");
  const muscleFilter = document.querySelector("#exercisePickerMuscleFilter");
  const equipmentFilter = document.querySelector(
    "#exercisePickerEquipmentFilter",
  );
  const categoryFilter = document.querySelector(
    "#exercisePickerCategoryFilter",
  );
  const resetFilters = document.querySelector("#exercisePickerResetFilters");
  const createAction = document.querySelector("#exercisePickerCreateAction");
  const customInput = document.querySelector("#exercisePickerCustomName");
  const customError = document.querySelector("#exercisePickerCustomError");
  const customForm = document.querySelector("#exercisePickerCreateForm");
  const backAction = document.querySelector("#exercisePickerCreateBack");
  const previewBack = document.querySelector("#exercisePickerPreviewBack");
  const previewContent = document.querySelector(
    "#exercisePickerPreviewContent",
  );
  const previewAdd = document.querySelector("#exercisePickerPreviewAdd");
  const createSubmit = customForm?.querySelector('button[type="submit"]');
  const closeButton = document.querySelector(".exercise-picker-close");
  const closeActions = document.querySelectorAll(
    "[data-exercise-picker-close]",
  );

  if (
    !dialog ||
    !title ||
    !description ||
    !browsePanel ||
    !createPanel ||
    !previewPanel ||
    !searchInput ||
    !optionsContainer ||
    !resultCount ||
    !emptyState ||
    !catalogStatus ||
    !filterToggle ||
    !filterCount ||
    !filters ||
    !muscleFilter ||
    !equipmentFilter ||
    !categoryFilter ||
    !resetFilters ||
    !createAction ||
    !customInput ||
    !customError ||
    !customForm ||
    !backAction ||
    !previewBack ||
    !previewContent ||
    !previewAdd ||
    !createSubmit ||
    !closeButton
  ) {
    return;
  }

  let localOptions = [];
  let catalogExercises = [];
  let activePreview = null;
  let selecting = false;
  let openToken = 0;
  let showAllLocalResults = false;
  let activeRequest = null;
  let selectionSession = null;
  let returnFocusTarget = null;

  function normalizeRequest(request = {}) {
    const context = PICKER_CONTEXTS.has(request.context)
      ? request.context
      : "active-workout";
    const mode = PICKER_MODES.has(request.mode) ? request.mode : "add";
    return {
      context,
      mode,
      currentName:
        typeof request.currentName === "string" ? request.currentName : "",
      excludedNames: Array.isArray(request.excludedNames)
        ? [...request.excludedNames]
        : [],
      getCurrentExerciseNames:
        typeof request.getCurrentExerciseNames === "function"
          ? request.getCurrentExerciseNames
          : () => [...(request.currentExerciseNames || [])],
      onSelect: request.onSelect,
      onCancel: request.onCancel,
      origin: request.origin,
      getReturnFocusTarget:
        typeof request.getReturnFocusTarget === "function"
          ? request.getReturnFocusTarget
          : null,
    };
  }

  function applyRequestCopy(request) {
    const routine = request.context === "routine";
    const replacing = request.mode === "replace";
    title.textContent = routine
      ? replacing
        ? "Change Routine Exercise"
        : "Add Exercise to Routine"
      : "Add Exercise";
    description.textContent = routine
      ? "Choose from your exercises and the offline catalog, or use a custom exercise name for this routine."
      : "Choose from your exercises and the offline catalog, or create a custom exercise.";
    const actionLabel = routine
      ? replacing
        ? "Use in Routine"
        : "Add to Routine"
      : "Add to Workout";
    previewAdd.textContent = actionLabel;
    createSubmit.textContent = actionLabel;
    closeButton.setAttribute("aria-label", `Close ${title.textContent}`);
  }

  function currentFilters() {
    return {
      primaryMuscle: muscleFilter.value,
      equipment: equipmentFilter.value,
      category: categoryFilter.value,
    };
  }

  function activeFilterCount() {
    return Object.values(currentFilters()).filter(Boolean).length;
  }

  function setFiltersExpanded(expanded) {
    const canExpand = catalogExercises.length > 0;
    const nextExpanded = Boolean(expanded && canExpand);
    filters.classList.toggle("hidden", !nextExpanded);
    filterToggle.setAttribute("aria-expanded", String(nextExpanded));
  }

  function updateFilterState() {
    const count = activeFilterCount();
    filterCount.textContent = String(count);
    filterCount.classList.toggle("hidden", count === 0);
    filterToggle.classList.toggle("is-active", count > 0);
    filterToggle.disabled = catalogExercises.length === 0;
    filterToggle.setAttribute(
      "aria-label",
      count ? `Filters, ${count} active` : "Filters",
    );
    resetFilters.disabled = count === 0;
  }

  function showBrowsePanel() {
    browsePanel.classList.remove("hidden");
    createPanel.classList.add("hidden");
    previewPanel.classList.add("hidden");
    customError.textContent = "";
    activePreview = null;
  }

  function showCreatePanel() {
    browsePanel.classList.add("hidden");
    previewPanel.classList.add("hidden");
    createPanel.classList.remove("hidden");
    customInput.value = searchInput.value.trim();
    customError.textContent = "";
    requestAnimationFrame(() => customInput.focus());
  }

  function appendMetadata(container, label, values) {
    const items = Array.isArray(values) ? values : [values];
    const available = items.filter(Boolean);
    if (!available.length) return;
    const row = appendText(container, "div", "", "exercise-picker-preview-meta");
    appendText(row, "dt", label);
    appendText(row, "dd", available.map(displayValue).join(", "));
  }

  function showPreviewPanel(exercise) {
    const summary = buildCatalogPreviewSummary(exercise);
    activePreview = exercise;
    browsePanel.classList.add("hidden");
    createPanel.classList.add("hidden");
    previewPanel.classList.remove("hidden");
    previewContent.replaceChildren();

    appendText(
      previewContent,
      "span",
      "Catalog preview",
      "exercise-picker-preview-eyebrow",
    );
    appendText(previewContent, "h3", exercise.name);
    const metadata = document.createElement("dl");
    metadata.className = "exercise-picker-preview-metadata";
    appendMetadata(metadata, "Equipment", summary.equipment);
    appendMetadata(metadata, "Primary muscles", summary.primaryMuscles);
    appendMetadata(metadata, "Difficulty", summary.difficulty);
    appendMetadata(metadata, "Category", summary.category);
    previewContent.appendChild(metadata);

    if (summary.instructionPreview.length) {
      appendText(previewContent, "h4", "Instruction preview");
      const instructions = document.createElement("ol");
      instructions.className = "exercise-picker-instructions";
      summary.instructionPreview.forEach((instruction) =>
        appendText(instructions, "li", instruction),
      );
      previewContent.appendChild(instructions);
      appendText(
        previewContent,
        "p",
        `${summary.remainingInstructionCount} more ${summary.remainingInstructionCount === 1 ? "step" : "steps"} available in the Guide after adding.`,
        "exercise-picker-preview-more",
      );
    } else if (summary.remainingInstructionCount) {
      appendText(
        previewContent,
        "p",
        "Full instructions are available in the Guide after adding.",
        "exercise-picker-preview-muted",
      );
    } else {
      appendText(
        previewContent,
        "p",
        "Instructions are not available for this catalog entry.",
        "exercise-picker-preview-muted",
      );
    }

    const source = appendText(
      previewContent,
      "p",
      "",
      "exercise-picker-attribution",
    );
    if (summary.attribution.url) {
      const sourceLink = document.createElement("a");
      sourceLink.href = summary.attribution.url;
      sourceLink.target = "_blank";
      sourceLink.rel = "noreferrer";
      sourceLink.textContent = summary.attribution.label;
      source.appendChild(sourceLink);
    } else {
      source.textContent = summary.attribution.label;
    }
    if (summary.attribution.license) {
      source.append(` · ${summary.attribution.license}`);
    }
    previewContent.scrollTop = 0;
    requestAnimationFrame(() => previewBack.focus());
  }

  function populateSelect(select, values, placeholder) {
    const previous = select.value;
    select.replaceChildren();
    const first = document.createElement("option");
    first.value = "";
    first.textContent = placeholder;
    select.appendChild(first);
    values.forEach((value) => {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = displayValue(value);
      select.appendChild(option);
    });
    select.value = values.includes(previous) ? previous : "";
  }

  function updateFilterOptions() {
    const available = getCatalogFilterOptions(catalogExercises);
    populateSelect(muscleFilter, available.primaryMuscles, "All muscles");
    populateSelect(equipmentFilter, available.equipment, "All equipment");
    populateSelect(categoryFilter, available.categories, "All categories");
    setFiltersExpanded(false);
    updateFilterState();
  }

  function updateCatalogStatus(status) {
    catalogStatus.className = `exercise-picker-catalog-status is-${status.status}`;
    if (status.status === "loading") {
      catalogStatus.textContent = "Loading catalog…";
      return;
    }
    if (status.status === "ready") {
      const skipped = status.skippedRecordCount
        ? ` · ${status.skippedRecordCount} skipped`
        : "";
      catalogStatus.textContent = `${catalogExercises.length} offline${skipped}`;
      return;
    }
    if (status.status === "unavailable") {
      catalogStatus.textContent = "Catalog unavailable · local only";
      return;
    }
    catalogStatus.textContent = "Local only";
  }

  function appendLocalResult(name) {
    const button = document.createElement("button");
    button.className = "exercise-picker-option is-local";
    button.type = "button";
    button.dataset.exerciseName = name;
    button.setAttribute("aria-label", name);
    appendText(button, "span", name, "exercise-picker-option-name");
    const source = appendText(
      button,
      "span",
      "Yours",
      "exercise-picker-option-source",
    );
    source.setAttribute("aria-hidden", "true");
    optionsContainer.appendChild(button);
  }

  function appendCatalogResult(exercise) {
    const button = document.createElement("button");
    button.className = "exercise-picker-option is-catalog";
    button.type = "button";
    button.dataset.catalogId = exercise.catalogId;
    button.setAttribute("aria-label", `View ${exercise.name} details`);
    appendText(button, "span", exercise.name, "exercise-picker-option-name");
    appendText(button, "span", "Catalog", "exercise-picker-option-source");
    const metadata = [exercise.primaryMuscles[0], exercise.equipment[0]]
      .filter(Boolean)
      .map(displayValue)
      .join(" · ");
    if (metadata) {
      appendText(button, "span", metadata, "exercise-picker-option-metadata");
    }
    optionsContainer.appendChild(button);
  }

  function appendSectionHeading(label, count) {
    const heading = document.createElement("div");
    heading.className = "exercise-picker-section-heading";
    appendText(heading, "strong", label);
    appendText(heading, "span", String(count));
    optionsContainer.appendChild(heading);
  }

  function appendShowAllLocalResult(count) {
    const button = document.createElement("button");
    button.className = "exercise-picker-show-local";
    button.type = "button";
    button.dataset.showAllLocal = "true";
    button.textContent = `Show all ${count} of your exercises`;
    optionsContainer.appendChild(button);
  }

  function renderOptions() {
    const searchValidation = validateSearchText(searchInput.value);
    if (!searchValidation.valid) {
      searchInput.setAttribute("aria-invalid", "true");
      optionsContainer.replaceChildren();
      resultCount.textContent = firstValidationMessage(searchValidation);
      emptyState.classList.remove("hidden");
      emptyState.textContent = "Shorten the search to continue.";
      updateFilterState();
      return;
    }
    searchInput.removeAttribute("aria-invalid");
    const matches = mergeLocalAndCatalogResults({
      localNames: localOptions,
      catalogExercises,
      query: searchInput.value,
      filters: currentFilters(),
    });
    const visibleLocal = showAllLocalResults
      ? matches.local
      : matches.local.slice(0, LOCAL_RESULT_LIMIT);
    const visibleCatalog = matches.catalog.slice(0, CATALOG_RESULT_LIMIT);
    optionsContainer.replaceChildren();

    if (visibleLocal.length) {
      appendSectionHeading(
        "Your exercises",
        matches.local.length > visibleLocal.length
          ? `${visibleLocal.length} of ${matches.local.length}`
          : matches.local.length,
      );
      visibleLocal.forEach(appendLocalResult);
      if (matches.local.length > visibleLocal.length) {
        appendShowAllLocalResult(matches.local.length);
      }
    }
    if (visibleCatalog.length) {
      appendSectionHeading(
        "Exercise catalog",
        matches.catalog.length > visibleCatalog.length
          ? `${visibleCatalog.length}+`
          : visibleCatalog.length,
      );
      visibleCatalog.forEach(appendCatalogResult);
    }

    const total = matches.local.length + matches.catalog.length;
    const noun = total === 1 ? "exercise" : "exercises";
    resultCount.textContent = `${total} ${noun}`;
    emptyState.classList.toggle("hidden", total > 0);
    if (!total) {
      const query = searchInput.value.trim();
      const hasFilters = Object.values(currentFilters()).some(Boolean);
      emptyState.textContent = hasFilters
        ? "No catalog exercises match these filters. Clear filters or create a custom exercise."
        : query
          ? `No exercises match “${query}”. You can create it below.`
          : "No exercises are available yet. Create your first one below.";
    }
    updateFilterState();
  }

  async function chooseExercise(name) {
    if (selecting) return;
    const selection = selectionSession?.select(name);
    if (!selection?.accepted) return;
    selecting = true;
    dialog.close("selected");
    try {
      await selection.promise;
    } catch (error) {
      console.info("Exercise selection callback failed.", error);
    } finally {
      selecting = false;
    }
  }

  async function openPicker(request = {}) {
    if (dialog.open) return false;
    activeRequest = normalizeRequest(request);
    applyRequestCopy(activeRequest);
    selectionSession = createExercisePickerSelectionSession(activeRequest);
    returnFocusTarget = activeRequest.origin || document.activeElement;
    const token = ++openToken;
    selecting = false;
    localOptions = [];
    catalogExercises = [];
    searchInput.value =
      activeRequest.mode === "replace" ? activeRequest.currentName : "";
    customInput.value = "";
    customInput.removeAttribute("aria-invalid");
    customError.textContent = "";
    muscleFilter.value = "";
    equipmentFilter.value = "";
    categoryFilter.value = "";
    showAllLocalResults = false;
    setFiltersExpanded(false);
    updateFilterState();
    resultCount.textContent = "Loading local exercises…";
    emptyState.classList.add("hidden");
    optionsContainer.replaceChildren();
    createAction.disabled = true;
    showBrowsePanel();
    dialog.returnValue = "";
    dialog.showModal();
    requestAnimationFrame(() => searchInput.focus());

    const catalogPromise = loadCatalog();
    updateCatalogStatus(getCatalogStatus());
    catalogPromise.then((catalog) => {
      catalogExercises = catalog.exercises;
      if (!dialog.open || token !== openToken) return;
      updateFilterOptions();
      updateCatalogStatus(catalog);
      renderOptions();
    });

    let routines = [];
    let workouts = [];
    try {
      [routines, workouts] = await Promise.all([getRoutines(), getWorkouts()]);
    } catch (error) {
      console.info("Local exercise options could not be loaded.", error);
    }
    if (!dialog.open || token !== openToken) return;
    let currentExercises = [];
    try {
      const values = activeRequest.getCurrentExerciseNames();
      currentExercises = Array.isArray(values) ? values : [];
    } catch (error) {
      console.info("Current exercise names could not be read.", error);
    }
    localOptions = buildExerciseOptions({
      defaultRoutines: Object.entries(DEFAULT_TEMPLATES).map(
        ([name, exercises]) => ({ name, exercises }),
      ),
      routines,
      workouts,
      currentExercises,
    });
    createAction.disabled = false;
    renderOptions();
    return true;
  }

  searchInput.addEventListener("input", () => {
    showAllLocalResults = false;
    renderOptions();
  });
  filterToggle.addEventListener("click", () => {
    setFiltersExpanded(filterToggle.getAttribute("aria-expanded") !== "true");
  });
  [muscleFilter, equipmentFilter, categoryFilter].forEach((select) =>
    select.addEventListener("change", () => {
      showAllLocalResults = false;
      renderOptions();
    }),
  );
  resetFilters.addEventListener("click", () => {
    muscleFilter.value = "";
    equipmentFilter.value = "";
    categoryFilter.value = "";
    showAllLocalResults = false;
    renderOptions();
    resetFilters.focus();
  });
  optionsContainer.addEventListener("click", (event) => {
    if (event.target.closest("[data-show-all-local]")) {
      showAllLocalResults = true;
      renderOptions();
      optionsContainer.querySelector("[data-exercise-name]")?.focus();
      return;
    }
    const localButton = event.target.closest("[data-exercise-name]");
    if (localButton) {
      chooseExercise(localButton.dataset.exerciseName);
      return;
    }
    const catalogButton = event.target.closest("[data-catalog-id]");
    const exercise = catalogExercises.find(
      (item) => item.catalogId === catalogButton?.dataset.catalogId,
    );
    if (exercise) showPreviewPanel(exercise);
  });
  createAction.addEventListener("click", showCreatePanel);
  backAction.addEventListener("click", () => {
    showBrowsePanel();
    requestAnimationFrame(() => searchInput.focus());
  });
  previewBack.addEventListener("click", () => {
    showBrowsePanel();
    requestAnimationFrame(() => searchInput.focus());
  });
  previewAdd.addEventListener("click", () => {
    if (activePreview) chooseExercise(activePreview.name);
  });
  customForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const validation = validateExerciseName(customInput.value);
    if (!validation.valid) {
      customInput.setAttribute("aria-invalid", "true");
      customError.textContent = validation.errors[0]?.code === "required"
        ? "Enter an exercise name."
        : firstValidationMessage(validation);
      customInput.focus();
      return;
    }
    const name = resolveCatalogCanonicalName(
      validation.normalized,
      localOptions,
      catalogExercises,
    );
    if (!name) {
      customError.textContent = "Enter an exercise name.";
      customInput.focus();
      return;
    }
    customInput.removeAttribute("aria-invalid");
    customError.textContent = "";
    chooseExercise(name);
  });
  customInput.addEventListener("input", () => {
    const validation = validateExerciseName(customInput.value);
    if (validation.valid) {
      customInput.removeAttribute("aria-invalid");
      customError.textContent = "";
    }
  });
  closeActions.forEach((button) =>
    button.addEventListener("click", () => dialog.close("cancel")),
  );
  dialog.addEventListener(
    "keydown",
    (event) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      event.stopPropagation();
      dialog.close("cancel");
    },
    true,
  );
  dialog.addEventListener("cancel", (event) => {
    event.preventDefault();
    dialog.close("cancel");
  });
  dialog.addEventListener("click", (event) => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    const inside =
      event.clientX >= bounds.left &&
      event.clientX <= bounds.right &&
      event.clientY >= bounds.top &&
      event.clientY <= bounds.bottom;
    if (!inside) dialog.close("cancel");
  });
  dialog.addEventListener("close", () => {
    openToken += 1;
    const closedSession = selectionSession;
    const focusTarget =
      activeRequest?.getReturnFocusTarget?.() || returnFocusTarget;
    selectionSession = null;
    activeRequest = null;
    returnFocusTarget = null;
    showBrowsePanel();
    if (dialog.returnValue !== "selected") {
      closedSession?.cancel().promise.catch((error) => {
        console.info("Exercise picker cancellation callback failed.", error);
      });
    }
    if (focusTarget?.isConnected && typeof focusTarget.focus === "function") {
      focusTarget.focus({ preventScroll: true });
    }
  });

  return Object.freeze({ open: openPicker });
}

export function initializeExercisePicker() {
  if (!pickerController) pickerController = createExercisePickerController();
  return pickerController;
}

export function openExercisePicker(options = {}) {
  const controller = initializeExercisePicker();
  return controller?.open(options) ?? Promise.resolve(false);
}

export function bindExercisePicker({ trigger, ...options }) {
  if (!trigger) return () => {};
  initializeExercisePicker();
  const handleOpen = () =>
    openExercisePicker({ ...options, origin: trigger });
  trigger.addEventListener("click", handleOpen);
  return () => trigger.removeEventListener("click", handleOpen);
}
