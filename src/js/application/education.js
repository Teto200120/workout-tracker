import {
  createDefaultEducationState,
  normalizeEducationState,
  resetAllEducation,
  transitionEducationExperience,
} from "../domain/education.js";
import {
  getEducationRecord,
  setEducationRecord,
} from "../storage/local.js";

let cachedState = null;
const pendingReplays = new Set();
const visibleOffers = new Set();

function currentTimestamp() {
  return new Date().toISOString();
}

function readState() {
  if (cachedState) return cachedState;
  try {
    cachedState = normalizeEducationState(getEducationRecord());
  } catch {
    cachedState = createDefaultEducationState();
  }
  return cachedState;
}

function persistState(nextState) {
  cachedState = normalizeEducationState(nextState);
  try {
    setEducationRecord(cachedState);
    return { saved: true, state: normalizeEducationState(cachedState) };
  } catch (error) {
    return {
      saved: false,
      error,
      state: normalizeEducationState(cachedState),
    };
  }
}

export function getEducationState() {
  return normalizeEducationState(readState());
}

export function getEducationExperience(experienceId) {
  return getEducationState().experiences[experienceId] || null;
}

export function ensureEducationState() {
  return persistState(readState());
}

export function updateEducationExperience(
  experienceId,
  status,
  options = {},
) {
  const nextState = transitionEducationExperience(
    readState(),
    experienceId,
    status,
    {
      ...options,
      now: options.now || currentTimestamp(),
    },
  );
  return persistState(nextState);
}

export function claimEducationOffer(experienceId) {
  if (visibleOffers.has(experienceId)) {
    return { visible: true, saved: true };
  }
  const experience = getEducationExperience(experienceId);
  if (experience?.status !== "unseen" || pendingReplays.has(experienceId)) {
    return { visible: false, saved: true };
  }
  const result = updateEducationExperience(experienceId, "offered");
  visibleOffers.add(experienceId);
  return { ...result, visible: true };
}

export function hideEducationOffer(experienceId) {
  visibleOffers.delete(experienceId);
}

export function isEducationOfferVisible(experienceId) {
  return visibleOffers.has(experienceId);
}

export function requestEducationReplay(experienceId) {
  pendingReplays.add(experienceId);
  visibleOffers.delete(experienceId);
  return updateEducationExperience(experienceId, "offered", { lastStep: 0 });
}

export function consumeEducationReplay(experienceId) {
  const requested = pendingReplays.has(experienceId);
  pendingReplays.delete(experienceId);
  return requested;
}

export function hasEducationReplay(experienceId) {
  return pendingReplays.has(experienceId);
}

export function resetAllGuidance() {
  pendingReplays.clear();
  visibleOffers.clear();
  return persistState(resetAllEducation(readState()));
}

export function forgetEducationState() {
  cachedState = null;
  pendingReplays.clear();
  visibleOffers.clear();
}
