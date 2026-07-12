import "../core/globals.js";
import { refreshTemplateDropdowns } from "../components/routine-selectors.js";
import { cleanText, id, toast } from "../core/utils.js";
import { clearRoutines, deleteRoutine, getRoutines, saveRoutine, seedDefaultTemplates } from "../storage/indexed-db.js";
import { loadWorkoutTemplate, showSessionView } from "./active-workout.js";

let templateDraftExercises = [];
let editingTemplateId = null;

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
  const value = input.value.trim();
  if (!value) { toast("Enter an exercise name first."); return; }
  templateDraftExercises.push(value);
  input.value = "";
  renderTemplateDraft();
}

function removeTemplateDraftExercise(index) {
  templateDraftExercises.splice(index, 1);
  renderTemplateDraft();
}

function clearTemplateDraft() {
  editingTemplateId = null;
  templateDraftExercises = [];
  $("templateName").value = "";
  $("templateExerciseInput").value = "";
  renderTemplateDraft();
  toast("Routine draft cleared.");
}

async function saveTemplate() {
  const name = $("templateName").value.trim();
  if (!name) { toast("Enter a template name first."); return; }
  const existing = (await getRoutines()).find((template) => template.name.toLowerCase() === name.toLowerCase());
  const template = {
    id: editingTemplateId || existing?.id || id(),
    name,
    exercises: [...templateDraftExercises],
    createdAt: existing?.createdAt || new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };
  await saveRoutine(template);
  clearTemplateDraft();
  await refreshTemplateDropdowns(name);
  await renderAll();
  toast("Routine saved.");
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
  if (!confirm("Delete this routine? Workout history will not be deleted.")) return;
  await deleteRoutine(templateId);
  await refreshTemplateDropdowns();
  await renderAll();
  toast("Routine deleted.");
}

async function startRoutine(templateId) {
  const template = (await getRoutines()).find((item) => item.id === templateId);
  if (!template) return;
  await refreshTemplateDropdowns(template.name);
  $("workoutType").value = template.name;
  await loadWorkoutTemplate();
  switchScreen("log");
  showSessionView();
  toast(`${template.name} loaded.`);
}

async function resetTemplates() {
  if (!confirm("Reset routines to defaults? Workout history will stay.")) return;
  await clearRoutines();
  await seedDefaultTemplates();
  await refreshTemplateDropdowns();
  clearTemplateDraft();
  await renderAll();
  toast("Default routines restored.");
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
