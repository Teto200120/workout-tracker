import "../core/globals.js";

function iconCheck() {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>`;
}

function iconInfo() {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/></svg>`;
}

function iconChevron() {
  return `<svg class="chevron-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>`;
}

function iconChevronRight() {
  return `<svg class="chevron-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>`;
}

function iconPlayOutline() {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7-11-7Z"/></svg>`;
}

function iconGrip() {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01"/></svg>`;
}

function iconPlus() {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>`;
}

function iconMinus() {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"/></svg>`;
}

Object.assign(globalThis, { iconCheck, iconInfo, iconChevron, iconChevronRight, iconPlayOutline, iconGrip, iconPlus, iconMinus });
