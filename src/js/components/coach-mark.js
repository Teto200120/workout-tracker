import { motionBehavior } from "../core/utils.js";

const FOCUSABLE_SELECTOR = [
  "button:not([disabled])",
  "a[href]",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(",");

let activeState = null;
let component = null;
let positionFrame = null;

function isElementVisible(element) {
  if (!(element instanceof Element) || !element.isConnected) return false;
  const style = getComputedStyle(element);
  if (
    style.display === "none" ||
    style.visibility === "hidden" ||
    Number(style.opacity) === 0
  ) {
    return false;
  }
  const rect = element.getBoundingClientRect();
  return rect.width > 0 && rect.height > 0;
}

function targetCandidates(target) {
  if (typeof target === "function") return targetCandidates(target());
  if (typeof target === "string") {
    try {
      return Array.from(document.querySelectorAll(target));
    } catch {
      return [];
    }
  }
  if (target instanceof Element) return [target];
  if (target && typeof target[Symbol.iterator] === "function") {
    return Array.from(target);
  }
  return [];
}

export function resolveEducationTarget(target) {
  return targetCandidates(target).find(isElementVisible) || null;
}

export function isSoftwareKeyboardLikelyOpen() {
  const viewport = window.visualViewport;
  if (!viewport) return false;
  const heightLoss = window.innerHeight - viewport.height;
  return heightLoss > 180 || viewport.height / window.innerHeight < 0.72;
}

export function canPresentEducation(options = {}) {
  if (activeState) return false;
  if (document.body.dataset.screenTransitioning === "true") return false;
  if (options.userEditing || options.dragActive) return false;
  if (isSoftwareKeyboardLikelyOpen()) return false;
  if (
    document.activeElement?.matches(
      "input, textarea, select, [contenteditable='true']",
    )
  ) {
    return false;
  }
  if (document.querySelector("dialog[open]")) return false;
  if (document.querySelector(".completion-overlay:not(.hidden)")) return false;
  if (document.querySelector(".today-review-view:not(.hidden)")) return false;
  if (document.querySelector(".exercise-detail-view:not(.hidden)")) return false;
  if (document.querySelector(".dragging, [data-drag-active='true']")) {
    return false;
  }
  return options.target ? Boolean(resolveEducationTarget(options.target)) : true;
}

function createComponent() {
  const root = document.createElement("div");
  root.className = "coach-mark-root hidden";
  root.setAttribute("aria-hidden", "true");
  root.innerHTML = `
    <div class="coach-mark-blocker" aria-hidden="true"></div>
    <div class="coach-mark-highlight" aria-hidden="true"></div>
    <section class="coach-mark-bubble" role="dialog" aria-modal="true" aria-labelledby="coachMarkTitle" aria-describedby="coachMarkBody">
      <div class="coach-mark-copy">
        <p class="coach-mark-progress" id="coachMarkProgress"></p>
        <h2 id="coachMarkTitle"></h2>
        <p id="coachMarkBody"></p>
      </div>
      <div class="coach-mark-actions">
        <button class="coach-mark-skip" type="button">Skip</button>
        <div>
          <button class="coach-mark-back" type="button">Back</button>
          <button class="coach-mark-next" type="button">Next</button>
        </div>
      </div>
    </section>
  `;
  document.body.appendChild(root);

  const result = {
    root,
    highlight: root.querySelector(".coach-mark-highlight"),
    bubble: root.querySelector(".coach-mark-bubble"),
    progress: root.querySelector("#coachMarkProgress"),
    title: root.querySelector("#coachMarkTitle"),
    body: root.querySelector("#coachMarkBody"),
    back: root.querySelector(".coach-mark-back"),
    next: root.querySelector(".coach-mark-next"),
    skip: root.querySelector(".coach-mark-skip"),
  };

  result.back.addEventListener("click", () => showRelativeStep(-1));
  result.next.addEventListener("click", () => showRelativeStep(1));
  result.skip.addEventListener("click", () => closeCoachMark("skipped"));
  root.addEventListener("pointerdown", (event) => {
    if (!result.bubble.contains(event.target)) event.preventDefault();
  });
  root.addEventListener("click", (event) => {
    if (!result.bubble.contains(event.target)) {
      event.preventDefault();
      event.stopPropagation();
    }
  });
  root.addEventListener("keydown", handleKeydown);
  return result;
}

function getComponent() {
  if (!component) component = createComponent();
  return component;
}

function viewportBounds() {
  const viewport = window.visualViewport;
  return {
    left: viewport?.offsetLeft || 0,
    top: viewport?.offsetTop || 0,
    width: viewport?.width || window.innerWidth,
    height: viewport?.height || window.innerHeight,
  };
}

function settleLayout() {
  return new Promise((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(resolve));
  });
}

function positionCurrentStep() {
  if (!activeState) return;
  const { highlight, bubble } = getComponent();
  const target = activeState.target;
  if (!isElementVisible(target)) {
    showRelativeStep(1);
    return;
  }

  const bounds = viewportBounds();
  const rect = target.getBoundingClientRect();
  const gap = 12;
  const margin = 14;
  const bottomReserve = 92;
  const highlightPadding = 6;
  const targetTop = rect.top + bounds.top;
  const targetLeft = rect.left + bounds.left;

  highlight.style.left = `${Math.max(bounds.left + 4, targetLeft - highlightPadding)}px`;
  highlight.style.top = `${Math.max(bounds.top + 4, targetTop - highlightPadding)}px`;
  highlight.style.width = `${Math.min(rect.width + highlightPadding * 2, bounds.width - 8)}px`;
  highlight.style.height = `${Math.min(rect.height + highlightPadding * 2, bounds.height - 8)}px`;

  const bubbleWidth = Math.min(360, bounds.width - margin * 2);
  bubble.style.width = `${bubbleWidth}px`;
  bubble.style.visibility = "hidden";
  bubble.style.left = `${Math.max(
    bounds.left + margin,
    Math.min(
      targetLeft + rect.width / 2 - bubbleWidth / 2,
      bounds.left + bounds.width - bubbleWidth - margin,
    ),
  )}px`;
  const bubbleHeight = bubble.offsetHeight;
  const availableBelow =
    bounds.top + bounds.height - bottomReserve - (targetTop + rect.height);
  const availableAbove = targetTop - bounds.top;
  const placeBelow =
    availableBelow >= bubbleHeight + gap || availableBelow >= availableAbove;
  const unclampedTop = placeBelow
    ? targetTop + rect.height + gap
    : targetTop - bubbleHeight - gap;
  const maximumTop =
    bounds.top + bounds.height - bottomReserve - bubbleHeight - margin;
  bubble.style.top = `${Math.max(
    bounds.top + margin,
    Math.min(unclampedTop, maximumTop),
  )}px`;
  bubble.dataset.placement = placeBelow ? "below" : "above";
  bubble.style.visibility = "visible";
}

function schedulePosition() {
  if (!activeState || positionFrame) return;
  positionFrame = requestAnimationFrame(() => {
    positionFrame = null;
    positionCurrentStep();
  });
}

function findStep(startIndex, direction) {
  if (!activeState) return null;
  for (
    let index = startIndex;
    index >= 0 && index < activeState.steps.length;
    index += direction
  ) {
    const target = resolveEducationTarget(activeState.steps[index].target);
    if (target) return { index, target };
    activeState.missingTarget = true;
  }
  return null;
}

async function showStep(index, direction = 1) {
  if (!activeState) return;
  const found = findStep(index, direction);
  if (!found) {
    const outcome = activeState.missingTarget ? "deferred" : "completed";
    closeCoachMark(outcome);
    return;
  }

  const stateAtStart = activeState;
  const step = activeState.steps[found.index];
  activeState.index = found.index;
  activeState.target = found.target;
  found.target.scrollIntoView({
    behavior: motionBehavior(),
    block: "center",
    inline: "nearest",
  });
  await settleLayout();
  if (activeState !== stateAtStart || !isElementVisible(found.target)) {
    if (activeState === stateAtStart) {
      activeState.missingTarget = true;
      showStep(found.index + direction, direction);
    }
    return;
  }

  const ui = getComponent();
  ui.progress.textContent = `${found.index + 1} of ${activeState.steps.length}`;
  ui.title.textContent = step.title;
  ui.body.textContent = step.body;
  ui.back.disabled = !findStep(found.index - 1, -1);
  const nextStep = findStep(found.index + 1, 1);
  ui.next.textContent = nextStep
    ? "Next"
    : activeState.finalLabel || "Done";
  activeState.onStepChange?.(found.index);
  positionCurrentStep();
  requestAnimationFrame(() => ui.next.focus({ preventScroll: true }));
}

function showRelativeStep(direction) {
  if (!activeState) return;
  showStep(activeState.index + direction, direction);
}

function handleKeydown(event) {
  if (!activeState) return;
  if (event.key === "Escape") {
    event.preventDefault();
    closeCoachMark("dismissed");
    return;
  }
  if (event.key !== "Tab") return;
  const focusable = Array.from(
    getComponent().bubble.querySelectorAll(FOCUSABLE_SELECTOR),
  ).filter((element) => !element.disabled);
  if (!focusable.length) return;
  const first = focusable[0];
  const last = focusable.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function handleHistoryBack() {
  if (!activeState) return;
  activeState.historyEntryActive = false;
  closeCoachMark("dismissed", { fromHistory: true });
}

function addPositionListeners() {
  window.addEventListener("resize", schedulePosition, { passive: true });
  window.addEventListener("orientationchange", schedulePosition, {
    passive: true,
  });
  window.addEventListener("scroll", schedulePosition, {
    capture: true,
    passive: true,
  });
  window.visualViewport?.addEventListener("resize", schedulePosition, {
    passive: true,
  });
  window.visualViewport?.addEventListener("scroll", schedulePosition, {
    passive: true,
  });
  window.addEventListener("popstate", handleHistoryBack);
}

function removePositionListeners() {
  window.removeEventListener("resize", schedulePosition);
  window.removeEventListener("orientationchange", schedulePosition);
  window.removeEventListener("scroll", schedulePosition, true);
  window.visualViewport?.removeEventListener("resize", schedulePosition);
  window.visualViewport?.removeEventListener("scroll", schedulePosition);
  window.removeEventListener("popstate", handleHistoryBack);
}

export function isCoachMarkOpen() {
  return Boolean(activeState);
}

function restoreFocusAfterClose(closingState) {
  const currentTab = document.querySelector('.tab[aria-current="page"]');
  const launcherIsRestorable =
    closingState.launcher !== document.body &&
    closingState.launcher !== document.documentElement &&
    isElementVisible(closingState.launcher);
  const focusTarget = launcherIsRestorable
    ? closingState.launcher
    : isElementVisible(currentTab)
      ? currentTab
      : isElementVisible(closingState.target)
      ? closingState.target
      : null;
  if (!focusTarget || typeof focusTarget.focus !== "function") return;

  const temporaryTabIndex = !focusTarget.matches(FOCUSABLE_SELECTOR);
  const previousTabIndex = focusTarget.getAttribute("tabindex");
  let restored = false;
  const restoreTabIndex = () => {
    if (!temporaryTabIndex || restored) return;
    restored = true;
    if (previousTabIndex === null) focusTarget.removeAttribute("tabindex");
    else focusTarget.setAttribute("tabindex", previousTabIndex);
  };
  if (temporaryTabIndex) {
    focusTarget.setAttribute("tabindex", "-1");
    focusTarget.addEventListener("blur", restoreTabIndex, { once: true });
  }
  focusTarget.focus({ preventScroll: true });
  requestAnimationFrame(() => {
    if (document.activeElement !== focusTarget) restoreTabIndex();
  });
}

export function closeCoachMark(reason = "dismissed", options = {}) {
  if (!activeState) return;
  const closingState = activeState;
  activeState = null;
  removePositionListeners();
  if (positionFrame) cancelAnimationFrame(positionFrame);
  positionFrame = null;

  const ui = getComponent();
  ui.root.classList.add("hidden");
  ui.root.setAttribute("aria-hidden", "true");
  document.body.classList.remove("coach-mark-open");

  const clearsHistory = closingState.historyEntryActive && !options.fromHistory;
  if (clearsHistory) {
    window.addEventListener(
      "popstate",
      () => setTimeout(() => restoreFocusAfterClose(closingState), 0),
      { once: true },
    );
    history.back();
  }
  closingState.onClose?.({
    reason,
    lastStep: Math.max(0, closingState.index),
  });
  if (!clearsHistory) {
    setTimeout(() => restoreFocusAfterClose(closingState), 0);
  }
}

export function startCoachMark(options = {}) {
  if (activeState || !Array.isArray(options.steps) || !options.steps.length) {
    return false;
  }
  const initialTarget = resolveEducationTarget(options.steps[0].target);
  if (!initialTarget && !options.steps.some((step) => resolveEducationTarget(step.target))) {
    options.onClose?.({ reason: "deferred", lastStep: 0 });
    return false;
  }

  const ui = getComponent();
  activeState = {
    steps: options.steps.map((step) => ({
      target: step.target,
      title: String(step.title || "Guidance"),
      body: String(step.body || ""),
    })),
    index: -1,
    target: initialTarget,
    launcher:
      options.launcher ||
      (document.activeElement === document.body ? null : document.activeElement),
    finalLabel: options.finalLabel || "Done",
    onStepChange: options.onStepChange,
    onClose: options.onClose,
    missingTarget: false,
    historyEntryActive: false,
  };
  ui.skip.textContent = options.skipLabel || "Skip";
  ui.root.classList.remove("hidden");
  ui.root.setAttribute("aria-hidden", "false");
  document.body.classList.add("coach-mark-open");
  addPositionListeners();

  try {
    history.pushState(
      { ...history.state, educationOverlay: Date.now() },
      "",
      window.location.href,
    );
    activeState.historyEntryActive = true;
  } catch {
    activeState.historyEntryActive = false;
  }

  showStep(0, 1);
  return true;
}
