import "./core/globals.js";
import "./core/utils.js";
import "./core/settings.js";
import "./storage/local.js";
import "./storage/indexed-db.js";
import "./components/icons.js";
import "./screens/progress.js";
import "./screens/today.js";
import "./screens/active-workout.js";
import "./screens/history.js";
import "./screens/routines.js";
import "./screens/backup.js";
import "./screens/profile.js";
import "./screens/timers.js";
import "./router.js";

init().catch((error) => {
  console.error(error);
  toast("Tracker could not start. Check browser storage permissions.");
});

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("service-worker.js").catch((error) => {
      console.warn("Service worker registration failed:", error);
    });
  });
}
