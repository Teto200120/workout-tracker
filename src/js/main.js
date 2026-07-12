import { toast } from "./core/utils.js";
import { init } from "./router.js";

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
