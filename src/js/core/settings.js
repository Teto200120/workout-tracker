import "./globals.js";

function cloneDefaultSettings() {
  return JSON.parse(JSON.stringify(DEFAULT_APP_SETTINGS));
}

function getAppSettings() {
  try {
    const stored = JSON.parse(localStorage.getItem(SETTINGS_KEY)) || {};
    return {
      ...cloneDefaultSettings(),
      ...stored,
      schedule: { ...cloneDefaultSettings().schedule, ...(stored.schedule || {}) }
    };
  } catch {
    return cloneDefaultSettings();
  }
}

function setAppSettings(settings) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  applyAppSettings();
}

function applyAppSettings() {
  const settings = getAppSettings();
  document.body.classList.toggle("no-animations", !settings.animations);
}

async function renderSettings() {
  if (!$("settingsSchedule")) return;
  const settings = getAppSettings();
  const templates = await getTemplates();
  const routineOptions = templates.map((template) => `<option value="${cleanText(template.name)}">${cleanText(template.name)}</option>`).join("");

  $("settingsSchedule").innerHTML = DAY_LABELS.map((day, index) => {
    const item = settings.schedule[index] || DEFAULT_APP_SETTINGS.schedule[index];
    return `<div class="settings-day-row" data-settings-day="${index}">
      <div class="settings-day-main">
        <span class="settings-row-icon settings-day-icon" aria-hidden="true">${day.slice(0, 2)}</span>
        <div>
          <div class="settings-day-label">${day}</div>
          <p class="settings-row-note">Today suggestion</p>
        </div>
      </div>
      <div class="settings-schedule-controls">
        <div class="settings-field">
          <label>Type</label>
          <select class="settings-day-kind">
            <option value="gym" ${item.kind === "gym" ? "selected" : ""}>Gym</option>
            <option value="rest" ${item.kind === "rest" ? "selected" : ""}>Rest</option>
            <option value="soccer" ${item.kind === "soccer" ? "selected" : ""}>Soccer</option>
          </select>
        </div>
        <div class="settings-field">
          <label>Routine</label>
          <select class="settings-day-routine">${routineOptions}</select>
        </div>
      </div>
    </div>`;
  }).join("");

  all(".settings-day-row").forEach((row) => {
    const index = row.dataset.settingsDay;
    const value = settings.schedule[index]?.routine || DEFAULT_APP_SETTINGS.schedule[index]?.routine || "Custom";
    const routineSelect = row.querySelector(".settings-day-routine");
    if (Array.from(routineSelect.options).some((option) => option.value === value)) routineSelect.value = value;
  });

  $("settingsWeightJump").value = settings.defaultWeightJump;
  $("settingsRpeAware").checked = !!settings.rpeAware;
  $("settingsCompoundMin").value = settings.compoundMin;
  $("settingsCompoundMax").value = settings.compoundMax;
  $("settingsPullMin").value = settings.pullMin;
  $("settingsPullMax").value = settings.pullMax;
  $("settingsIsolationMin").value = settings.isolationMin;
  $("settingsIsolationMax").value = settings.isolationMax;
  $("settingsGeneralMin").value = settings.generalMin;
  $("settingsGeneralMax").value = settings.generalMax;
  $("settingsHaptics").checked = !!settings.haptics;
  $("settingsAnimations").checked = !!settings.animations;
}

async function saveSettingsFromForm() {
  const current = getAppSettings();
  const schedule = {};
  all(".settings-day-row").forEach((row) => {
    const index = row.dataset.settingsDay;
    schedule[index] = {
      kind: row.querySelector(".settings-day-kind").value,
      routine: row.querySelector(".settings-day-routine").value
    };
  });

  const compound = normalizeRange($("settingsCompoundMin").value, $("settingsCompoundMax").value, current.compoundMin, current.compoundMax);
  const pull = normalizeRange($("settingsPullMin").value, $("settingsPullMax").value, current.pullMin, current.pullMax);
  const isolation = normalizeRange($("settingsIsolationMin").value, $("settingsIsolationMax").value, current.isolationMin, current.isolationMax);
  const general = normalizeRange($("settingsGeneralMin").value, $("settingsGeneralMax").value, current.generalMin, current.generalMax);

  setAppSettings({
    schedule,
    defaultWeightJump: Math.max(0.5, Number($("settingsWeightJump").value || current.defaultWeightJump || 5)),
    compoundMin: compound.min,
    compoundMax: compound.max,
    pullMin: pull.min,
    pullMax: pull.max,
    isolationMin: isolation.min,
    isolationMax: isolation.max,
    generalMin: general.min,
    generalMax: general.max,
    rpeAware: $("settingsRpeAware").checked,
    haptics: $("settingsHaptics").checked,
    animations: $("settingsAnimations").checked
  });

  await renderTodayView();
  await updateAllExerciseHints();
  toast("Settings saved.");
}

async function resetAppSettings() {
  if (!confirm("Reset app settings to defaults? Workout history and routines stay.")) return;
  localStorage.removeItem(SETTINGS_KEY);
  applyAppSettings();
  await renderSettings();
  await renderTodayView();
  await updateAllExerciseHints();
  toast("Settings reset.");
}

Object.assign(globalThis, { cloneDefaultSettings, getAppSettings, setAppSettings, applyAppSettings, renderSettings, saveSettingsFromForm, resetAppSettings });
