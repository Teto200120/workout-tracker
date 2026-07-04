import "../core/globals.js";

function renderTemplateDraft() {
  const list = $("templateDraftList");
  if (!templateDraftExercises.length) { list.innerHTML = `<p class="muted small" style="margin:0;">No exercises added yet.</p>`; return; }
  list.innerHTML = templateDraftExercises.map((exercise, index) => `<div class="row" style="margin-bottom:8px;"><span>${index + 1}. ${cleanText(exercise)}</span><button class="danger" type="button" onclick="removeTemplateDraftExercise(${index})">Remove</button></div>`).join("");
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
  if (!confirm("Reset routines to defaults? Workout and weight history will stay.")) return;
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
  if (!templates.length) { container.innerHTML = `<p class="muted">No templates saved.</p>`; return; }
  container.innerHTML = templates.map((template) => `<article class="routine-card"><div class="row" style="align-items:flex-start;"><div><h3>${cleanText(template.name)}</h3><p class="muted small routine-meta">${template.exercises.length} exercises</p><div>${template.exercises.slice(0, 6).map((exercise) => `<span class="pill">${cleanText(exercise)}</span>`).join("") || `<span class="muted small">Empty routine</span>`}${template.exercises.length > 6 ? `<span class="pill">+${template.exercises.length - 6} more</span>` : ""}</div></div><div class="routine-actions"><button class="primary" type="button" onclick="startRoutine('${template.id}')">Start</button><button class="ghost" type="button" onclick="editTemplate('${template.id}')">Edit</button><button class="danger" type="button" onclick="deleteTemplate('${template.id}')">Delete</button></div></div></article>`).join("");
}

Object.assign(globalThis, { renderTemplateDraft, addTemplateExercise, removeTemplateDraftExercise, clearTemplateDraft, saveTemplate, editTemplate, deleteTemplate, startRoutine, resetTemplates, renderTemplates });
