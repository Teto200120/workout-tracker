import "./globals.js";
import { createActionCoordinator } from "../application/action-coordinator.js";
import { DAY_LABELS, DEFAULT_APP_SETTINGS } from "./constants.js";
import { cleanText, toast } from "./utils.js";
import {
  firstValidationMessage,
  validateSettingsInput
} from "../domain/input-guardrails.js";
import { getRoutines } from "../storage/indexed-db.js";
import {
  cloneDefaultSettings,
  getAppSettings,
  setAppSettings as persistAppSettings
} from "../storage/local.js";

export { cloneDefaultSettings, getAppSettings };

const settingsCoordinator = createActionCoordinator();

const SETTINGS_INPUT_IDS = Object.freeze({
  defaultWeightJump: "settingsWeightJump",
  compoundMin: "settingsCompoundMin",
  compoundMax: "settingsCompoundMax",
  pullMin: "settingsPullMin",
  pullMax: "settingsPullMax",
  isolationMin: "settingsIsolationMin",
  isolationMax: "settingsIsolationMax",
  generalMin: "settingsGeneralMin",
  generalMax: "settingsGeneralMax"
});

function clearSettingsValidation() {
  Object.values(SETTINGS_INPUT_IDS).forEach((inputId) =>
    $(inputId)?.removeAttribute("aria-invalid"),
  );
  all("[data-settings-validation]").forEach((feedback) => feedback.remove());
}

function showSettingsValidation(error) {
  clearSettingsValidation();
  const field = error?.path?.replace(/^settings\./u, "");
  const input = $(SETTINGS_INPUT_IDS[field]);
  if (!input) return;
  input.setAttribute("aria-invalid", "true");
  const feedback = document.createElement("p");
  feedback.className = "field-validation";
  feedback.dataset.settingsValidation = "true";
  feedback.setAttribute("role", "alert");
  feedback.textContent = error.message;
  const row = input.closest(".settings-list-row");
  (row || input).insertAdjacentElement("afterend", feedback);
  input.focus();
}

function numericFormValue(value) {
  return Number(String(value).trim().replace(",", "."));
}

export function setAppSettings(settings) {
  persistAppSettings(settings);
  applyAppSettings();
}

export function applyAppSettings() {
  const settings = getAppSettings();
  document.body.classList.toggle("no-animations", !settings.animations);
}

export async function renderSettings() {
  if (!$("settingsSchedule")) return;
  const settings = getAppSettings();
  const templates = await getRoutines();
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

export async function saveSettingsFromForm() {
  return settingsCoordinator.run(async () => {
    const saveButton = $("saveSettings");
    if (saveButton) saveButton.disabled = true;
    try {
      const schedule = {};
      all(".settings-day-row").forEach((row) => {
        const index = row.dataset.settingsDay;
        schedule[index] = {
          kind: row.querySelector(".settings-day-kind").value,
          routine: row.querySelector(".settings-day-routine").value
        };
      });

      const rawSettings = {
        defaultWeightJump: $("settingsWeightJump").value,
        compoundMin: $("settingsCompoundMin").value,
        compoundMax: $("settingsCompoundMax").value,
        pullMin: $("settingsPullMin").value,
        pullMax: $("settingsPullMax").value,
        isolationMin: $("settingsIsolationMin").value,
        isolationMax: $("settingsIsolationMax").value,
        generalMin: $("settingsGeneralMin").value,
        generalMax: $("settingsGeneralMax").value
      };
      const validation = validateSettingsInput(rawSettings);
      if (!validation.valid) {
        showSettingsValidation(validation.errors[0]);
        toast(firstValidationMessage(validation));
        return false;
      }
      if (
        validation.warnings.length &&
        !confirm(`${firstValidationMessage({ errors: [], warnings: validation.warnings })}\n\nSave these settings anyway?`)
      ) {
        return false;
      }
      clearSettingsValidation();
      setAppSettings({
        displayName: getAppSettings().displayName,
        schedule,
        defaultWeightJump: numericFormValue(rawSettings.defaultWeightJump),
        compoundMin: numericFormValue(rawSettings.compoundMin),
        compoundMax: numericFormValue(rawSettings.compoundMax),
        pullMin: numericFormValue(rawSettings.pullMin),
        pullMax: numericFormValue(rawSettings.pullMax),
        isolationMin: numericFormValue(rawSettings.isolationMin),
        isolationMax: numericFormValue(rawSettings.isolationMax),
        generalMin: numericFormValue(rawSettings.generalMin),
        generalMax: numericFormValue(rawSettings.generalMax),
        rpeAware: $("settingsRpeAware").checked,
        haptics: $("settingsHaptics").checked,
        animations: $("settingsAnimations").checked
      });
      toast("Settings saved.");
      return true;
    } catch (error) {
      console.info("Settings save failed.", error);
      toast("Could not save settings. Your entered values are still available.");
      return false;
    } finally {
      if (saveButton) saveButton.disabled = false;
    }
  }).promise;
}

export async function resetAppSettings() {
  return settingsCoordinator.run(async () => {
    const resetButton = $("resetSettings");
    if (resetButton) resetButton.disabled = true;
    try {
      if (!confirm("Reset app settings to defaults? Workout history and routines stay.")) return false;
      const displayName = getAppSettings().displayName;
      setAppSettings({ ...cloneDefaultSettings(), displayName });
      applyAppSettings();
      await renderSettings();
      toast("Settings reset.");
      return true;
    } catch (error) {
      console.info("Settings reset failed.", error);
      toast("Could not reset settings. Existing settings were left in place.");
      return false;
    } finally {
      if (resetButton) resetButton.disabled = false;
    }
  }).promise;
}

