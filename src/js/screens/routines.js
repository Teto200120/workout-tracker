import "../core/globals.js";

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
      <button class="danger routine-remove-action" type="button" onclick="removeTemplateDraftExercise(${index})">Remove</button>
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
  const existing = (await getTemplates()).find((template) => template.name.toLowerCase() === name.toLowerCase());
  const template = {
    id: editingTemplateId || existing?.id || id(),
    name,
    exercises: [...templateDraftExercises],
    createdAt: existing?.createdAt || new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };
  await saveItem("templates", template);
  clearTemplateDraft();
  await refreshTemplateDropdowns(name);
  await renderAll();
  toast("Routine saved.");
}

async function editTemplate(templateId) {
  const template = (await getTemplates()).find((item) => item.id === templateId);
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
  await deleteItem("templates", templateId);
  await refreshTemplateDropdowns();
  await renderAll();
  toast("Routine deleted.");
}

async function startRoutine(templateId) {
  const template = (await getTemplates()).find((item) => item.id === templateId);
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
  await clearStore("templates");
  await seedDefaultTemplates();
  await refreshTemplateDropdowns();
  clearTemplateDraft();
  await renderAll();
  toast("Default routines restored.");
}

async function renderTemplates() {
  renderTemplateDraft();
  const templates = await getTemplates();
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
          <button class="primary routine-start-action" type="button" onclick="startRoutine('${template.id}')">Start</button>
          <button class="ghost routine-edit-action" type="button" onclick="editTemplate('${template.id}')">Edit</button>
          <button class="danger routine-delete-action" type="button" onclick="deleteTemplate('${template.id}')">Delete</button>
        </div>
      </article>
    `;
  }).join("");
}

Object.assign(globalThis, { renderTemplateDraft, addTemplateExercise, removeTemplateDraftExercise, clearTemplateDraft, saveTemplate, editTemplate, deleteTemplate, startRoutine, resetTemplates, renderTemplates });
