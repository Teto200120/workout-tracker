import { toast } from "./core/utils.js";
import { init } from "./router.js";
import { startupFailureMessage } from "./schema/errors.js";
import { showStartupFailure } from "./screens/onboarding.js";

init().catch((error) => {
  console.error("Tracker startup failed:", error);
  const message = startupFailureMessage(error);
  showStartupFailure(message);
  toast(message);
});

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("service-worker.js").catch((error) => {
      console.warn("Service worker registration failed:", error);
    });
  });
}
