import "../core/globals.js";
import { createActionCoordinator } from "../application/action-coordinator.js";
import { refreshTemplateDropdowns } from "../components/routine-selectors.js";
import { cleanText, id, toast } from "../core/utils.js";
import {
  INPUT_LIMITS,
  WARNING_THRESHOLDS,
  firstValidationMessage,
  nameComparisonKey,
  validateExerciseName,
  validateRoutineInput,
  validateRoutineName
} from "../domain/input-guardrails.js";
import { clearRoutines, deleteRoutine, getRoutines, saveRoutine, seedDefaultTemplates } from "../storage/indexed-db.js";
import { loadWorkoutTemplate, showSessionView } from "./active-workout.js";

let templateDraftExercises = [];
let editingTemplateId = null;
const routineMutationCoordinator = createActionCoordinator();
const routineStartCoordinator = createActionCoordinator();

function setRoutineFieldFeedback(inputId, feedbackId, result) {
  const input = $(inputId);
  const feedback = $(feedbackId);
  if (!input || !feedback) return;
  if (result.valid) input.removeAttribute("aria-invalid");
  else input.setAttribute("aria-invalid", "true");
  feedback.textContent = firstValidationMessage(result);
  feedback.classList.toggle(
    "is-warning",
    result.valid && result.warnings.length > 0,
  );
}

function renderTemplateDraft() {
  const list = $("templateDraftList");
  if (!templateDraftExercises.length) {
    list.innerHTML = `
      <div class="routine-empty-state routine-draft-empty">
        <strong>No draft exercises yet</strong>
        <p class="muted small">Add exercises above to build this routine.</p>
      </div>
    `;
    return;
  }
  list.innerHTML = templateDraftExercises.map((exercise, index) => `
    <div class="routine-draft-row">
      <span class="routine-draft-index">${index + 1}</span>
      <span class="routine-draft-name">${cleanText(exercise)}</span>
      <button class="danger routine-remove-action" type="button" data-routine-action="remove-draft" data-exercise-index="${index}">Remove</button>
    </div>
  `).join("");
}

function addTemplateExercise() {
  const input = $("templateExerciseInput");
  const result = validateExerciseName(input.value);
  setRoutineFieldFeedback(
    "templateExerciseInput",
    "templateExerciseError",
    result,
  );
  if (!result.valid) {
    input.focus();
    toast(firstValidationMessage(result));
    return;
  }
  if (templateDraftExercises.length >= INPUT_LIMITS.exercisesPerRoutine) {
    const message = `A routine is limited to ${INPUT_LIMITS.exercisesPerRoutine} exercises.`;
    $("templateExerciseError").textContent = message;
    toast(message);
    return;
  }
  const value = result.normalized;
  const duplicate = templateDraftExercises.some(
    (exercise) => nameComparisonKey(exercise) === nameComparisonKey(value),
  );
  if (duplicate && !confirm(`${value} is already in this routine. Add it again?`)) {
    return;
  }
  templateDraftExercises.push(value);
  input.value = "";
  $("templateExerciseError").textContent = "";
  renderTemplateDraft();
  if (
    templateDraftExercises.length ===
    WARNING_THRESHOLDS.exercisesPerRoutine + 1
  ) {
    toast("This routine now has an unusually large number of exercises.");
  }
}

function removeTemplateDraftExercise(index) {
  templateDraftExercises.splice(index, 1);
  renderTemplateDraft();
}

function clearTemplateDraft(showMessage = true) {
  editingTemplateId = null;
  templateDraftExercises = [];
  $("templateName").value = "";
  $("templateExerciseInput").value = "";
  renderTemplateDraft();
  $("templateName")?.removeAttribute("aria-invalid");
  $("templateExerciseInput")?.removeAttribute("aria-invalid");
  if ($("templateNameError")) $("templateNameError").textContent = "";
  if ($("templateExerciseError")) $("templateExerciseError").textContent = "";
  if (showMessage) toast("Routine draft cleared.");
}

async function saveTemplate() {
  return routineMutationCoordinator.run(async () => {
    const button = $("saveTemplate");
    if (button) button.disabled = true;
    try {
      const nameResult = validateRoutineName($("templateName").value);
      setRoutineFieldFeedback("templateName", "templateNameError", nameResult);
      const name = nameResult.normalized;
      const template = {
        id: editingTemplateId || id(),
        name,
        exercises: [...templateDraftExercises],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
      const validation = validateRoutineInput(template);
      if (!validation.valid) {
        const error = validation.errors[0];
        if (error.path === "routine.name") {
          setRoutineFieldFeedback(
            "templateName",
            "templateNameError",
            { valid: false, errors: [error], warnings: [] },
          );
          $("templateName").focus();
        } else {
          $("templateExerciseError").textContent = error.message;
        }
        toast(error.message);
        return false;
      }
      const routines = await getRoutines();
      const existing = routines.find(
        (item) => nameComparisonKey(item.name) === nameComparisonKey(name),
      );
      if (!editingTemplateId && !existing && routines.length >= INPUT_LIMITS.routines) {
        toast(`The app is limited to ${INPUT_LIMITS.routines} saved routines.`);
        return false;
      }
      if (editingTemplateId && existing && existing.id !== editingTemplateId) {
        const message = "Another routine already uses this name.";
        $("templateNameError").textContent = message;
        $("templateName").focus();
        toast(message);
        return false;
      }
      if (!editingTemplateId && existing) {
        if (!confirm(`Replace the existing ${existing.name} routine?`)) return false;
        template.id = existing.id;
        template.createdAt = existing.createdAt;
      } else if (editingTemplateId) {
        const edited = routines.find((item) => item.id === editingTemplateId);
        template.createdAt = edited?.createdAt || template.createdAt;
      }
      if (
        validation.warnings.length &&
        !confirm(`${validation.warnings[0].message}\n\nSave this routine anyway?`)
      ) {
        return false;
      }
      await saveRoutine(template);
      clearTemplateDraft(false);
      await refreshTemplateDropdowns(name);
      await renderAll();
      toast("Routine saved.");
      return true;
    } catch (error) {
      console.info("Routine save failed.", error);
      toast("Could not save the routine. Your draft is still available.");
      return false;
    } finally {
      if (button) button.disabled = false;
    }
  }).promise;
}

async function editTemplate(templateId) {
  const template = (await getRoutines()).find((item) => item.id === templateId);
  if (!template) return;
  editingTemplateId = template.id;
  $("templateName").value = template.name;
  templateDraftExercises = [...template.exercises];
  renderTemplateDraft();
  switchScreen("templates");
  toast("Routine loaded for editing.");
}

async function deleteTemplate(templateId) {
  return routineMutationCoordinator.run(async () => {
    try {
      if (!confirm("Delete this routine? Workout history will not be deleted.")) return false;
      await deleteRoutine(templateId);
      await refreshTemplateDropdowns();
      await renderAll();
      toast("Routine deleted.");
      return true;
    } catch (error) {
      console.info("Routine delete failed.", error);
      toast("Could not delete the routine. Existing data is unchanged.");
      return false;
    }
  }).promise;
}

async function startRoutine(templateId) {
  return routineStartCoordinator.run(async () => {
    try {
      const template = (await getRoutines()).find((item) => item.id === templateId);
      if (!template) return false;
      await refreshTemplateDropdowns(template.name);
      $("workoutType").value = template.name;
      await loadWorkoutTemplate();
      switchScreen("log");
      showSessionView();
      toast(`${template.name} loaded.`);
      return true;
    } catch (error) {
      console.info("Routine start failed.", error);
      toast("Could not start this routine. Try again.");
      return false;
    }
  }).promise;
}

async function resetTemplates() {
  return routineMutationCoordinator.run(async () => {
    const button = $("resetTemplates");
    if (button) button.disabled = true;
    try {
      if (!confirm("Reset routines to defaults? Workout history will stay.")) return false;
      await clearRoutines();
      await seedDefaultTemplates();
      await refreshTemplateDropdowns();
      clearTemplateDraft(false);
      await renderAll();
      toast("Default routines restored.");
      return true;
    } catch (error) {
      console.info("Routine reset failed.", error);
      toast("Could not reset routines. Existing routines were left in place where possible.");
      return false;
    } finally {
      if (button) button.disabled = false;
    }
  }).promise;
}

export async function renderTemplates() {
  renderTemplateDraft();
  const templates = await getRoutines();
  const container = $("savedTemplates");
  if (!templates.length) {
    container.innerHTML = `
      <div class="routine-empty-state routine-library-empty">
        <strong>No saved routines yet</strong>
        <p class="muted small">Create a reusable plan with the builder above.</p>
      </div>
    `;
    return;
  }
  container.innerHTML = templates.map((template) => {
    const exercises = template.exercises || [];
    const exerciseChips = exercises.slice(0, 6).map((exercise) => `<span class="routine-chip">${cleanText(exercise)}</span>`).join("");
    const moreChip = exercises.length > 6 ? `<span class="routine-chip routine-chip-more">+${exercises.length - 6} more</span>` : "";
    const emptyState = exercises.length ? "" : `<span class="routine-chip routine-chip-empty">Empty routine</span>`;
    return `
      <article class="routine-card">
        <div class="routine-card-copy">
          <div class="routine-card-heading">
            <h3>${cleanText(template.name)}</h3>
            <span>${exercises.length} exercises</span>
          </div>
          <div class="routine-chip-row">
            ${exerciseChips}${moreChip}${emptyState}
          </div>
        </div>
        <div class="routine-actions">
          <button class="primary routine-start-action" type="button" data-routine-action="start" data-template-id="${cleanText(template.id)}">Start</button>
          <button class="ghost routine-edit-action" type="button" data-routine-action="edit" data-template-id="${cleanText(template.id)}">Edit</button>
          <button class="danger routine-delete-action" type="button" data-routine-action="delete" data-template-id="${cleanText(template.id)}">Delete</button>
        </div>
      </article>
    `;
  }).join("");
}

export function bindRoutineActions() {
  $("templateDraftList").addEventListener("click", (event) => {
    const button = event.target.closest('[data-routine-action="remove-draft"]');
    if (button) removeTemplateDraftExercise(Number(button.dataset.exerciseIndex));
  });
  $("savedTemplates").addEventListener("click", (event) => {
    const button = event.target.closest("[data-routine-action]");
    if (!button) return;
    if (button.dataset.routineAction === "start") startRoutine(button.dataset.templateId);
    if (button.dataset.routineAction === "edit") editTemplate(button.dataset.templateId);
    if (button.dataset.routineAction === "delete") deleteTemplate(button.dataset.templateId);
  });
}

export { addTemplateExercise, clearTemplateDraft, resetTemplates, saveTemplate };
