const CACHE_NAME = "hector-workout-tracker-pwa-v17";
const APP_SHELL = [
  "./",
  "./index.html",
  "./manifest.json",
  "./icon-192.png",
  "./icon-512.png",
  "./src/styles/main.css",
  "./src/styles/base.css",
  "./src/styles/onboarding.css",
  "./src/styles/today-base.css",
  "./src/styles/active-workout-base.css",
  "./src/styles/screens.css",
  "./src/styles/motion.css",
  "./src/styles/today.css",
  "./src/styles/active-workout.css",
  "./src/js/main.js",
  "./src/js/router.js",
  "./src/js/application/backup.js",
  "./src/js/application/action-coordinator.js",
  "./src/js/application/data-schema.js",
  "./src/js/application/display-name.js",
  "./src/js/application/schedule.js",
  "./src/js/core/constants.js",
  "./src/js/core/globals.js",
  "./src/js/core/utils.js",
  "./src/js/core/settings.js",
  "./src/js/storage/local.js",
  "./src/js/storage/indexed-db.js",
  "./src/js/components/icons.js",
  "./src/js/components/exercise-picker.js",
  "./src/js/components/routine-selectors.js",
  "./src/js/catalog/catalog-contract.js",
  "./src/js/catalog/catalog-loader.js",
  "./src/js/catalog/catalog-search.js",
  "./src/js/catalog/exercise-aliases.js",
  "./src/js/catalog/exercise-catalog-resolver.js",
  "./src/js/catalog/exercise-guide-adapter.js",
  "./src/js/catalog/free-exercise-db-adapter.js",
  "./src/data/exercise-catalog.json",
  "./src/js/domain/exercise-options.js",
  "./src/js/domain/input-guardrails.js",
  "./src/js/domain/routine-draft.js",
  "./src/js/domain/schedule.js",
  "./src/js/domain/training-rules.js",
  "./src/js/domain/workout-metrics.js",
  "./src/js/schema/contracts.js",
  "./src/js/schema/errors.js",
  "./src/js/schema/migrations.js",
  "./src/js/schema/normalize.js",
  "./src/js/schema/validators.js",
  "./src/js/schema/versions.js",
  "./src/js/screens/today.js",
  "./src/js/screens/active-workout.js",
  "./src/js/screens/progress.js",
  "./src/js/screens/history.js",
  "./src/js/screens/routines.js",
  "./src/js/screens/backup.js",
  "./src/js/screens/profile.js",
  "./src/js/screens/onboarding.js"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.map((key) => key !== CACHE_NAME ? caches.delete(key) : null))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const isNavigation = event.request.mode === "navigate" || event.request.destination === "document";

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response && response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
        }
        return response;
      })
      .catch(() => caches.match(event.request).then((cached) => {
        if (cached) return cached;
        if (isNavigation) return caches.match("./index.html");
        throw new Error(`No cached response for ${event.request.url}`);
      }))
  );
});
