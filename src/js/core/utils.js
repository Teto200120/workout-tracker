import "./globals.js";
import { getAppSettings } from "../storage/local.js";

export function today() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export function timeNow() {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

export function id() { return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`; }

export function cleanText(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

export function dateLabel(value) {
  return new Date(`${value}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

export function toast(message) {
  const el = $("toast");
  el.textContent = message;
  el.classList.add("show");
  setTimeout(() => el.classList.remove("show"), 2200);
}

export function clamp(value, min = 0, max = 1) {
  return Math.min(max, Math.max(min, value));
}

export function lerp(start, end, amount) {
  return start + (end - start) * amount;
}

export function easeInOut(value) {
  return value * value * (3 - 2 * value);
}

export function prefersReducedMotion() {
  return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export function motionBehavior() {
  const settings = getAppSettings();
  return prefersReducedMotion() || !settings.animations ? "auto" : "smooth";
}

export function haptic(pattern = 18) {
  const settings = getAppSettings();
  if (settings.haptics && navigator.vibrate) navigator.vibrate(pattern);
}

export function replayAnimation(el, className, duration = 360) {
  const settings = getAppSettings();
  if (!el || prefersReducedMotion() || !settings.animations) return;
  el.classList.remove(className);
  void el.offsetWidth;
  el.classList.add(className);
  setTimeout(() => el.classList.remove(className), duration);
}

export function normalizeRange(min, max, fallbackMin, fallbackMax) {
  const cleanMin = Math.max(1, Number(min || fallbackMin));
  const cleanMax = Math.max(cleanMin, Number(max || fallbackMax));
  return { min: cleanMin, max: cleanMax };
}

export function scrollInputIntoView(input) {
  if (!input || window.innerWidth > 760) return;
  setTimeout(() => {
    input.scrollIntoView({ behavior: motionBehavior(), block: "center" });
  }, 260);
}

