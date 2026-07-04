import "../core/globals.js";

async function renderHistory() {
  const workouts = (await getItems("workouts")).sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  const filter = $("historyFilter").value;
  const search = $("exerciseSearch").value.trim().toLowerCase();
  const visible = workouts.filter((workout) => {
    const typeMatch = filter === "All" || workout.type === filter;
    const searchMatch = !search || workout.exercises.some((exercise) => exercise.name.toLowerCase().includes(search));
    return typeMatch && searchMatch;
  });
  const volume = workouts.reduce((sum, workout) => sum + workoutVolume(workout), 0);
  const sets = workouts.reduce((sum, workout) => sum + totalSets(workout), 0);
  const durations = workouts.map(workoutDurationMinutes).filter(Boolean);
  const avgDuration = durations.length ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length) : 0;
  $("workoutStats").innerHTML = `<div class="stat"><strong>${workouts.length}</strong><span class="muted small">Workouts</span></div><div class="stat"><strong>${sets}</strong><span class="muted small">Total Sets</span></div><div class="stat"><strong>${Math.round(volume).toLocaleString()}</strong><span class="muted small">Total Volume</span></div><div class="stat"><strong>${durationLabel(avgDuration)}</strong><span class="muted small">Avg Duration</span></div>`;
  const list = $("historyList");
  if (!visible.length) { list.innerHTML = `<p class="muted">No workouts found.</p>`; return; }
  list.innerHTML = visible.map(renderWorkout).join("");
}

function renderWorkoutTags(workout) {
  const tags = Array.isArray(workout.tags) ? workout.tags : [];
  if (!tags.length) return "";
  return `<div style="margin-bottom:8px;">${tags.map((tag) => `<span class="pill">${cleanText(tag)}</span>`).join("")}</div>`;
}

function renderWorkout(workout) {
  return `<article class="list-item"><div class="row" style="align-items:flex-start;"><div><h3>${cleanText(workout.type)}</h3><p class="muted small" style="margin-bottom:8px;">${dateLabel(workout.date)} · ${workout.exercises.length} exercises · ${completedSets(workout)} done / ${totalSets(workout)} work sets · ${Math.round(workoutVolume(workout)).toLocaleString()} volume · ${durationLabel(workoutDurationMinutes(workout))}</p>${workout.notes ? `<p class="small">${cleanText(workout.notes)}</p>` : ""}${renderWorkoutTags(workout)}<div>${workout.exercises.map((exercise) => `<span class="pill">${cleanText(exercise.name || "Unnamed")} · ${exercise.sets.length}</span>`).join("")}</div><details class="detail"><summary><strong>Full workout</strong></summary>${workout.exercises.map(renderExerciseDetails).join("")}</details></div><div class="stack" style="max-width:130px;"><button class="ghost" onclick="editWorkout('${workout.id}')">Edit</button><button class="danger" onclick="deleteWorkout('${workout.id}')">Delete</button></div></div></article>`;
}

function renderExerciseDetails(exercise) {
  return `<div style="margin-top:12px;"><strong>${cleanText(exercise.name || "Unnamed Exercise")}</strong>${exercise.notes ? `<p class="muted small" style="margin:6px 0;">${cleanText(exercise.notes)}</p>` : ""}<table><thead><tr><th>Set</th><th>Weight</th><th>Reps</th><th>RPE</th><th>Status</th></tr></thead><tbody>${exercise.sets.map((set, index) => `<tr><td>${index + 1}</td><td>${cleanText(set.weight || "-")}</td><td>${cleanText(set.reps || "-")}</td><td>${cleanText(set.rpe || "-")}</td><td>${set.warmup ? "Warm-up" : "Work"}${set.done ? " · Done" : ""}</td></tr>`).join("")}</tbody></table></div>`;
}

async function editWorkout(workoutId) {
  const workout = (await getItems("workouts")).find((item) => item.id === workoutId);
  if (!workout) return;

  editingWorkoutId = workout.id;
  $("workoutDate").value = workout.date;
  $("startTime").value = workout.startTime || "";
  $("endTime").value = workout.endTime || "";
  await refreshTemplateDropdowns(workout.type);
  $("workoutType").value = workout.type;
  $("workoutNotes").value = workout.notes || "";
  $("saveWorkout").textContent = "Update Workout";

  const list = $("exerciseList");
  list.innerHTML = "";
  workout.exercises.forEach((exercise) => list.appendChild(makeExercise(exercise)));
  collapseAllButFirstExercise();
  await updateAllExerciseHints();
  switchScreen("log");
  showSessionView();
  toast("Workout loaded for editing.");
}

async function deleteWorkout(workoutId) {
  if (!confirm("Delete this workout?")) return;
  await deleteItem("workouts", workoutId);
  await renderAll();
  toast("Workout deleted.");
}

async function saveWeight() {
  const value = Number($("weightValue").value);
  if (!value) { toast("Enter your weight first."); return; }
  await saveItem("weights", { id: id(), date: $("weightDate").value || today(), weight: value, notes: $("weightNotes").value.trim(), createdAt: new Date().toISOString() });
  $("weightValue").value = "";
  $("weightNotes").value = "";
  await renderAll();
  toast("Weight saved.");
}

async function renderWeights() {
  const weights = (await getItems("weights")).sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  const stats = $("weightStats");
  const list = $("weightList");
  if (!weights.length) {
    stats.innerHTML = `<div class="stat"><strong>-</strong><span class="muted small">Latest</span></div><div class="stat"><strong>-</strong><span class="muted small">Change</span></div><div class="stat"><strong>0</strong><span class="muted small">Entries</span></div>`;
    list.innerHTML = `<p class="muted">No weight entries yet.</p>`;
    return;
  }
  const latest = weights[0];
  const oldest = weights[weights.length - 1];
  const change = latest.weight - oldest.weight;
  stats.innerHTML = `<div class="stat"><strong>${latest.weight.toFixed(1)}</strong><span class="muted small">Latest lb</span></div><div class="stat"><strong>${change >= 0 ? "+" : ""}${change.toFixed(1)}</strong><span class="muted small">Total Change</span></div><div class="stat"><strong>${weights.length}</strong><span class="muted small">Entries</span></div>`;
  list.innerHTML = weights.map((entry) => `<article class="list-item"><div class="row" style="align-items:flex-start;"><div><h3>${entry.weight.toFixed(1)} lb</h3><p class="muted small" style="margin-bottom:6px;">${dateLabel(entry.date)}</p>${entry.notes ? `<p class="small">${cleanText(entry.notes)}</p>` : ""}</div><button class="danger" onclick="deleteWeight('${entry.id}')">Delete</button></div></article>`).join("");
}

async function deleteWeight(weightId) {
  if (!confirm("Delete this weight entry?")) return;
  await deleteItem("weights", weightId);
  await renderAll();
  toast("Weight deleted.");
}

Object.assign(globalThis, { renderHistory, renderWorkoutTags, renderWorkout, renderExerciseDetails, editWorkout, deleteWorkout, saveWeight, renderWeights, deleteWeight });
