import { DEFAULT_TEMPLATES } from "../core/constants.js";
import {
  buildExerciseOptions,
  resolveExerciseName,
  searchExerciseOptions,
} from "../domain/exercise-options.js";
import { getRoutines, getWorkouts } from "../storage/indexed-db.js";

export function bindExercisePicker({
  trigger,
  getCurrentExerciseNames,
  onSelect,
}) {
  const dialog = document.querySelector("#exercisePicker");
  const browsePanel = document.querySelector("#exercisePickerBrowse");
  const createPanel = document.querySelector("#exercisePickerCreate");
  const searchInput = document.querySelector("#exercisePickerSearch");
  const optionsContainer = document.querySelector("#exercisePickerOptions");
  const resultCount = document.querySelector("#exercisePickerResultCount");
  const emptyState = document.querySelector("#exercisePickerEmpty");
  const createAction = document.querySelector("#exercisePickerCreateAction");
  const customInput = document.querySelector("#exercisePickerCustomName");
  const customError = document.querySelector("#exercisePickerCustomError");
  const customForm = document.querySelector("#exercisePickerCreateForm");
  const backAction = document.querySelector("#exercisePickerCreateBack");
  const closeActions = document.querySelectorAll(
    "[data-exercise-picker-close]",
  );

  if (
    !trigger ||
    !dialog ||
    !browsePanel ||
    !createPanel ||
    !searchInput ||
    !optionsContainer ||
    !resultCount ||
    !emptyState ||
    !createAction ||
    !customInput ||
    !customError ||
    !customForm ||
    !backAction
  ) {
    return;
  }

  let options = [];
  let selecting = false;

  function showBrowsePanel() {
    browsePanel.classList.remove("hidden");
    createPanel.classList.add("hidden");
    customError.textContent = "";
  }

  function showCreatePanel() {
    browsePanel.classList.add("hidden");
    createPanel.classList.remove("hidden");
    customInput.value = searchInput.value.trim();
    customError.textContent = "";
    requestAnimationFrame(() => customInput.focus());
  }

  function renderOptions() {
    const matches = searchExerciseOptions(options, searchInput.value);
    optionsContainer.replaceChildren();

    for (const name of matches) {
      const button = document.createElement("button");
      button.className = "exercise-picker-option";
      button.type = "button";
      button.dataset.exerciseName = name;
      button.textContent = name;
      optionsContainer.appendChild(button);
    }

    const noun = matches.length === 1 ? "exercise" : "exercises";
    resultCount.textContent = `${matches.length} ${noun}`;
    emptyState.classList.toggle("hidden", matches.length > 0);
    if (!matches.length) {
      const query = searchInput.value.trim();
      emptyState.textContent = query
        ? `No exercises match “${query}”. You can create it below.`
        : "No exercises are available yet. Create your first one below.";
    }
  }

  async function chooseExercise(name) {
    if (selecting) return;
    selecting = true;
    dialog.close("selected");
    try {
      await onSelect(name);
    } finally {
      selecting = false;
    }
  }

  async function openPicker() {
    if (dialog.open) return;
    selecting = false;
    options = [];
    searchInput.value = "";
    customInput.value = "";
    customError.textContent = "";
    resultCount.textContent = "Loading exercises…";
    emptyState.classList.add("hidden");
    optionsContainer.replaceChildren();
    createAction.disabled = true;
    showBrowsePanel();
    dialog.showModal();
    requestAnimationFrame(() => searchInput.focus());

    const [routines, workouts] = await Promise.all([
      getRoutines(),
      getWorkouts(),
    ]);
    if (!dialog.open) return;
    options = buildExerciseOptions({
      defaultRoutines: Object.entries(DEFAULT_TEMPLATES).map(
        ([name, exercises]) => ({ name, exercises }),
      ),
      routines,
      workouts,
      currentExercises: getCurrentExerciseNames(),
    });
    createAction.disabled = false;
    renderOptions();
  }

  trigger.addEventListener("click", openPicker);
  searchInput.addEventListener("input", renderOptions);
  optionsContainer.addEventListener("click", (event) => {
    const button = event.target.closest("[data-exercise-name]");
    if (button) chooseExercise(button.dataset.exerciseName);
  });
  createAction.addEventListener("click", showCreatePanel);
  backAction.addEventListener("click", () => {
    showBrowsePanel();
    requestAnimationFrame(() => searchInput.focus());
  });
  customForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = resolveExerciseName(customInput.value, options);
    if (!name) {
      customError.textContent = "Enter an exercise name.";
      customInput.focus();
      return;
    }
    customError.textContent = "";
    chooseExercise(name);
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
    showBrowsePanel();
    trigger.focus({ preventScroll: true });
  });
}
