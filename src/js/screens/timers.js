import "../core/globals.js";
import { haptic, toast } from "../core/utils.js";

let timerInterval = null;
let timerEnd = 0;

export function startTimer(seconds) {
  timerEnd = Date.now() + seconds * 1000;
  if (timerInterval) clearInterval(timerInterval);
  updateTimer();
  timerInterval = setInterval(updateTimer, 250);
  $("timerText").textContent = `Resting for ${seconds} seconds.`;
}

function updateTimer() {
  const remaining = Math.max(0, Math.ceil((timerEnd - Date.now()) / 1000));
  const minutes = String(Math.floor(remaining / 60)).padStart(2, "0");
  const seconds = String(remaining % 60).padStart(2, "0");
  $("timerDisplay").textContent = `${minutes}:${seconds}`;
  if (remaining <= 0 && timerInterval) {
    clearInterval(timerInterval);
    timerInterval = null;
    $("timerText").textContent = "Rest done. Start the next set.";
    haptic([140, 70, 140]);
    toast("Rest done.");
  }
}

export function stopTimer() {
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = null;
  timerEnd = 0;
  $("timerDisplay").textContent = "00:00";
  $("timerText").textContent = "Timer stopped.";
}

