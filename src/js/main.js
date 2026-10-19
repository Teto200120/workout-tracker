import { toast } from "./core/utils.js";
import { init } from "./router.js";
import { startupFailureMessage } from "./schema/errors.js";
import { showStartupFailure } from "./screens/onboarding.js";

window.addEventListener("tracker-storage-unavailable", () => {
  showStartupFailure(
    "The database connection closed. Reload the tracker to continue. Your saved data is still on this device.",
  );
});

init().catch((error) => {
  console.error("Tracker startup failed:", error);
  const message = startupFailureMessage(error);
  showStartupFailure(message);
  toast(message);
});

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("service-worker.js")
      .then((registration) => {
        const notify = () => {
          if (registration.waiting && navigator.serviceWorker.controller)
            toast(
              "Update ready. Finish your workout, then close all tracker windows and reopen to update.",
            );
        };
        notify();
        registration.addEventListener("updatefound", () => {
          registration.installing?.addEventListener("statechange", notify);
        });
      })
      .catch((error) => {
        console.warn("Service worker registration failed:", error);
      });
  });
}
