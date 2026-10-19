const CACHE_NAME = "hector-workout-tracker-pwa-0.4.0-7fe2be0dab5108de";
const APP_SHELL = [
  "./",
  "./index.html",
  "./manifest.json",
  "./icon-192.png",
  "./icon-512.png",
  "./src/styles/main.css",
  "./src/styles/base.css",
  "./src/styles/onboarding.css",
  "./src/styles/education.css",
  "./src/styles/today-layout.css",
  "./src/styles/session-layout.css",
  "./src/styles/screens.css",
  "./src/styles/motion.css",
  "./src/styles/today-components.css",
  "./src/styles/session-components.css",
  "./src/js/main.js",
  "./src/js/router.js",
  "./src/js/application/backup.js",
  "./src/js/application/deadline.js",
  "./src/js/application/maintenance.js",
  "./src/js/application/recovery.js",
  "./src/js/application/action-coordinator.js",
  "./src/js/application/data-schema.js",
  "./src/js/application/display-name.js",
  "./src/js/application/education.js",
  "./src/js/application/feedback.js",
  "./src/js/application/feedback-transport.js",
  "./src/js/application/routine-seeding.js",
  "./src/js/application/schedule.js",
  "./src/js/core/constants.js",
  "./src/js/core/globals.js",
  "./src/js/core/utils.js",
  "./src/js/core/settings.js",
  "./src/js/core/release.js",
  "./src/js/storage/local.js",
  "./src/js/storage/feedback-outbox.js",
  "./src/js/storage/indexed-db.js",
  "./src/js/storage/transaction.js",
  "./src/js/storage/connection.js",
  "./src/js/storage/operation.js",
  "./src/js/storage/rescue.js",
  "./src/js/screens/rescue.js",
  "./src/js/screens/settings.js",
  "./src/js/domain/record-id.js",
  "./src/js/domain/routine-seeding.js",
  "./src/js/components/icons.js",
  "./src/js/components/exercise-picker.js",
  "./src/js/components/coach-mark.js",
  "./src/js/components/routine-selectors.js",
  "./src/js/catalog/catalog-contract.js",
  "./src/js/catalog/catalog-loader.js",
  "./src/js/catalog/catalog-search.js",
  "./src/js/catalog/exercise-aliases.js",
  "./src/js/catalog/exercise-catalog-resolver.js",
  "./src/js/catalog/exercise-guide-adapter.js",
  "./src/js/catalog/free-exercise-db-adapter.js",
  "./src/js/catalog/starter-routines.js",
  "./src/data/exercise-catalog.json",
  "./src/js/domain/exercise-options.js",
  "./src/js/domain/education.js",
  "./src/js/domain/input-guardrails.js",
  "./src/js/domain/feedback.js",
  "./src/js/domain/routine-draft.js",
  "./src/js/domain/schedule.js",
  "./src/js/domain/training-rules.js",
  "./src/js/domain/workout-metrics.js",
  "./src/js/domain/workout-session.js",
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
  "./src/js/screens/feedback.js",
  "./src/js/screens/profile.js",
  "./src/js/screens/onboarding.js",
];

const SHELL_INTEGRITY = {
  "./": "sha256-Pg0vdjYYbO5XzqwgkKG92SkI56ed+y0rdvnp9ayDX38=",
  "./index.html": "sha256-Pg0vdjYYbO5XzqwgkKG92SkI56ed+y0rdvnp9ayDX38=",
  "./manifest.json": "sha256-Zo4dIf2BrJxnmqI00cUae5sURJaYal0TUzlh9ROjTI4=",
  "./icon-192.png": "sha256-hdrs0JeE/EgYHx9jUkf/B3URpXtoUfr0FmvwzDGKVp0=",
  "./icon-512.png": "sha256-CPP3A8IAZkWRitFWmhjZlUtPhw/HawFO9rSVsWGcPGk=",
  "./src/styles/main.css":
    "sha256-B8TNI6n71BMT9oHg7ZKeZTH06vQfJSYjVGxgklGZubs=",
  "./src/styles/base.css":
    "sha256-iCZV9E7VlOkMaFXFX2NqjykWc/2og1D7YN88KnCGPME=",
  "./src/styles/onboarding.css":
    "sha256-pg0eyTeqg1Cg2EJdbsi+TOugsfZDH+15Tl4DOp6sZY0=",
  "./src/styles/education.css":
    "sha256-PLiJT2ehjlVzGhqEulum4oVMaaTEl0EBJW+nEvTGCME=",
  "./src/styles/today-layout.css":
    "sha256-84epl+o6SkfsKg8j/93+/irMWFOhfRB8bZM/qyWx8Ys=",
  "./src/styles/session-layout.css":
    "sha256-IXQv3k1GX4A9m+9BVf7U1Co/qHFJ8gATxgfPscXdF6Q=",
  "./src/styles/screens.css":
    "sha256-g/RaWgwgBnGEfq+5d+zOA1d2Pe2/JCbEiLpDHHnUi7A=",
  "./src/styles/motion.css":
    "sha256-d3CHjH9RIgOVsqXtRTLBzrhD5z6sw2jCxxkBpYeS+mE=",
  "./src/styles/today-components.css":
    "sha256-ymEys2hAMLAao5niy/IiIkqc2ZqWmwZGotaybZTfcaQ=",
  "./src/styles/session-components.css":
    "sha256-UGz1TUfJgbNZDXW9d/n3NyDltiQ3/AjPHenvWKw7U94=",
  "./src/js/main.js": "sha256-r7/Y0i3epr+TpkOAbzMfD0Me4deUsdu445eIZoQJAl4=",
  "./src/js/router.js": "sha256-J6pHngv7HXE5Y86ZRd9t+dCrM3NDbN8GZD3hxoyQUTU=",
  "./src/js/application/backup.js":
    "sha256-YnGAYpR8uiMJdP6qGTYBJsTW8piUkG9q7tsC+gsiYNs=",
  "./src/js/application/deadline.js":
    "sha256-2m1S8GA70Eklwkd8HCu4ya3qR55pwbaoIQqldzlp+o0=",
  "./src/js/application/maintenance.js":
    "sha256-HjkdTcF2ZBLKav+HobmMY8K6i+Fxeu3jM7IYL9Gk/hE=",
  "./src/js/application/recovery.js":
    "sha256-iIgyk3RujF07KR/dHk1el6B/eDvo7QrySuHSmcOUmpM=",
  "./src/js/application/action-coordinator.js":
    "sha256-PdOSUIJHzxfrKC10lybr/xP/t65KgeIVy/lm56jez1o=",
  "./src/js/application/data-schema.js":
    "sha256-Fknl56xMeUfPgj5LyRjvE7apLx4I/uqBskX9Ril6Vto=",
  "./src/js/application/display-name.js":
    "sha256-lplIYik3sELHvuWYS1jEF23jpeJkL+l566OQptyiBNI=",
  "./src/js/application/education.js":
    "sha256-aE7HtCV6t4vpNz7WoaCQKGTUAjswEJ76a2/O6akkFFE=",
  "./src/js/application/feedback.js":
    "sha256-DC6PiEAIaScTVe5k5uqqzgHeEus6QtFgQJqnKIaPCPI=",
  "./src/js/application/feedback-transport.js":
    "sha256-dM+proKA73tMU50vdQn3+kWWH35C39ZZvxcOw3sIGOo=",
  "./src/js/application/routine-seeding.js":
    "sha256-9yxTXB2HePTarylnjvvxhf7jiczDtynJY0Gn0hKrTCg=",
  "./src/js/application/schedule.js":
    "sha256-jrwDSsGhMpKywFoeseyHsdoIsR9pTJ1fjMGsVCDMv0E=",
  "./src/js/core/constants.js":
    "sha256-A9mjoAcrxkj7SC+ZeQATdMfseNQjGMUllyfYOXtyRMI=",
  "./src/js/core/globals.js":
    "sha256-JS3YYn5GJ7SnX6lCEmpSiQy/y+4LGOIPjmeyIHjXB58=",
  "./src/js/core/utils.js":
    "sha256-sl1Fb6ZdHyTWihIDmqa7Ztlm+gaYaVN6eEYee7eo5sw=",
  "./src/js/core/settings.js":
    "sha256-HkDJc77w1D2HkRp4N1Edayp6/ZQam5kAZPFphXIjsLk=",
  "./src/js/core/release.js":
    "sha256-YgJ+eB4KH5rdn+3N9Vma0i0T7h177Rr9Ng+imvC/giU=",
  "./src/js/storage/local.js":
    "sha256-0B9Uqzs2UFgbzQHfqr4vLZZZpzXKAe+SWTqS+V3KXBY=",
  "./src/js/storage/feedback-outbox.js":
    "sha256-VE5fAloeb2iQZxnRXHk9p3eSDUt4xUHef7mohKOirWs=",
  "./src/js/storage/indexed-db.js":
    "sha256-qTd2zmHcmLruzfcrgv0QpCiFn8UbvFO9PLZtXvgzYs0=",
  "./src/js/storage/transaction.js":
    "sha256-vvbXO9xlgKIx2iehaJm+iXAsURvNDv7Hmy7BJHL4frc=",
  "./src/js/storage/connection.js":
    "sha256-rUedegEsro3LCuRfb1dojbzkSaQUK4Ffp/j9Y9zhhpE=",
  "./src/js/storage/operation.js":
    "sha256-pCRaVRC8b8312mKMa0fFSIhsz3DURJkTW66zDoN57dQ=",
  "./src/js/storage/rescue.js":
    "sha256-rE1MaAtzouXv4uX4UtSYL1C87c0e24leG35qc8oDcMY=",
  "./src/js/screens/rescue.js":
    "sha256-RJmFzqrTEntbZkgwYDG+wy/eQ4BL6d4QXGx/7pSGJCg=",
  "./src/js/screens/settings.js":
    "sha256-CpaQaYEXosM+1tOXIxaiTsf0nNgvQOQ12nJKLCFIluA=",
  "./src/js/domain/record-id.js":
    "sha256-zG2TDvCv2VQF15p5FjBONmQVmJqpjpolkORNgW8X66o=",
  "./src/js/domain/routine-seeding.js":
    "sha256-JF4Hl2Yglcjadq0J8cQ0f/KqHcSgiNJr4cBxgmF0HzU=",
  "./src/js/components/icons.js":
    "sha256-hQJ1wzq3l31rn7Aid8NbGxYWngDMGNoN2HD/dSWUgTA=",
  "./src/js/components/exercise-picker.js":
    "sha256-GkfJRYjjNf0dtfZ/7QSEmgZgYzdNOjxhzeHqklCXvK0=",
  "./src/js/components/coach-mark.js":
    "sha256-2cxzWVQDjrl++SXsBRpIz5pgDMTJFn7NYK3Y5bngn+s=",
  "./src/js/components/routine-selectors.js":
    "sha256-3QfSmtaVhnF8k1Pjv2HmWEtMKDrxETj/B/pQWP31l+E=",
  "./src/js/catalog/catalog-contract.js":
    "sha256-jmUlGCvDBc7Iu2Ge+fhg3pcoo89YD0Xt5QIX2LFB1qk=",
  "./src/js/catalog/catalog-loader.js":
    "sha256-MMVBDHqTnIL6oBfru9XDtq5syaxffGpCyyuw5Oyl1/o=",
  "./src/js/catalog/catalog-search.js":
    "sha256-tEX11OaaTYDg4Hbd1SU7GLskk+nOmx4E4yqB7WnjY7w=",
  "./src/js/catalog/exercise-aliases.js":
    "sha256-YgmkJkNWaEFcFOCSf/fx13Emda+zz0wvnMbKgCdRox4=",
  "./src/js/catalog/exercise-catalog-resolver.js":
    "sha256-X4wT0jL8TMqI1PJXmcK2dBVndbaAd4jA/bZXGc/WZE0=",
  "./src/js/catalog/exercise-guide-adapter.js":
    "sha256-IdDcLQGfy2i1dXJfGRWD+AdRfX6anbsQYcpYT3sX/Cw=",
  "./src/js/catalog/free-exercise-db-adapter.js":
    "sha256-lJ95hDRMu06nDYssz+i7g75J8xH8haPpW9WbkH7cCsw=",
  "./src/js/catalog/starter-routines.js":
    "sha256-v77Z2pO8HBUGUwy2OT3jXUi10QwVJMak9AnbXLJ8ZI8=",
  "./src/data/exercise-catalog.json":
    "sha256-RCFcJas9HpQ3txbNk+Nf/NJbaCjfuf7SY9uchkSu2Zc=",
  "./src/js/domain/exercise-options.js":
    "sha256-9y6QWh3LpiJ017WCdVWxGBE1vwknQb+8suU8A47Wp1A=",
  "./src/js/domain/education.js":
    "sha256-Gz35qqmne1R3uCcX3OhSSJ8WlFDuk2x4cOsXjl9u4r8=",
  "./src/js/domain/input-guardrails.js":
    "sha256-0SQSeuOUBBC+v27/T7ppGrTeIoPUy9ckf2gIZKrWC+U=",
  "./src/js/domain/feedback.js":
    "sha256-ZbXtXi32hpKznwIhWn09L3n9oa+C+cxb783oGoTzzHE=",
  "./src/js/domain/routine-draft.js":
    "sha256-slyNxDLnZlibTrQ+puSs69T7zJipx/iPXkWzWsBT5ZM=",
  "./src/js/domain/schedule.js":
    "sha256-ez9CGBqLAidfIL6edoztwyeEewnqD59/zRdf3r3X41s=",
  "./src/js/domain/training-rules.js":
    "sha256-nQbqVy2dZ86MKHVumlDZEJ7malETV2naHyxS517+28s=",
  "./src/js/domain/workout-metrics.js":
    "sha256-dtAedEdzZFZjaYqZs1iSqomY4Q4zXVkyi2ldynLWInI=",
  "./src/js/domain/workout-session.js":
    "sha256-KQSf1+K9KC/v7aLViBGx1edLSj7FZ9n+G9bUA9GdT7I=",
  "./src/js/schema/contracts.js":
    "sha256-J2LIc+ag/f8DWw9oB3VTpl8LCWux3Kcoh9aiZHDc8sc=",
  "./src/js/schema/errors.js":
    "sha256-s1mJ53GdQEXlZRmuj722dDKOb9jpdkbSLx2iM7tcPGo=",
  "./src/js/schema/migrations.js":
    "sha256-bMNubQ6yRK1VyOdqqRus8OB9+1NAVsEpWbi3VZKB1hE=",
  "./src/js/schema/normalize.js":
    "sha256-CR2QS4BDvTtnMcNSxP7MXXFLtujMjnCWr2W+KTETcRY=",
  "./src/js/schema/validators.js":
    "sha256-f4RYjxYn20jgYT5Fl+RO4na2GPAt//0IlHBWUGHWcVg=",
  "./src/js/schema/versions.js":
    "sha256-6txYVwZL9fzYTIVVv6g4XZbZOshHl1wYtrFiJnxfobY=",
  "./src/js/screens/today.js":
    "sha256-KL8sZdMleq9VitUX/AlDNhkyGSUtbIiUgTLCQUys6e0=",
  "./src/js/screens/active-workout.js":
    "sha256-41nILHy8g6wzI2zyaZdgi45Zp2WMvr/DqOrQO1MEFc4=",
  "./src/js/screens/progress.js":
    "sha256-IPYdvSXLzbsYqbXWQiUK7Xw6XZItNJSqtU6YTx6rpY4=",
  "./src/js/screens/history.js":
    "sha256-PGP2NxXjuOVjxAra5b8cJsV+TZloHcYd/0SVGEwzhx8=",
  "./src/js/screens/routines.js":
    "sha256-gs+5vB9v5MwEfrFDIpWQdp+OBjRzuaXZxZddDr4xY+8=",
  "./src/js/screens/backup.js":
    "sha256-I4nZ86qJ0k+Ze1dl5QKzOPapXNIV/0WDYh2JK1PmxEU=",
  "./src/js/screens/feedback.js":
    "sha256-evC9iV6Y/CIsFDqj0oHLTZwLbk5i1ionb8MrUvc9phQ=",
  "./src/js/screens/profile.js":
    "sha256-Bxt1TdMGTF3YujsTISoXJxWhW/gEPB5LWFLEXTW5vEI=",
  "./src/js/screens/onboarding.js":
    "sha256-LUQruGuWDx9uWFS/PTK9evq5qPZ/gZSmfwPfhGwhqM0=",
};

const CACHE_PREFIX = "hector-workout-tracker-pwa-";
const SHELL_URLS = new Set(
  APP_SHELL.map((path) => new URL(path, self.registration.scope).href),
);

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE_NAME);
      try {
        await cache.addAll(
          APP_SHELL.map(
            (path) =>
              new Request(new URL(path, self.registration.scope), {
                cache: "reload",
                integrity: SHELL_INTEGRITY[path],
              }),
          ),
        );
      } catch (error) {
        await caches.delete(CACHE_NAME);
        throw error;
      }
    })(),
  );
  // Waiting is intentional: existing workout tabs keep their installed shell.
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
          .map((key) => caches.delete(key)),
      );
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  const navigation = event.request.mode === "navigate";
  // Unknown resources (including external verification) never enter the shell.
  if (!navigation && !SHELL_URLS.has(url.href)) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE_NAME);
      const key = navigation
        ? new URL("index.html", self.registration.scope).href
        : url.href;
      const cached = await cache.match(key);
      if (cached) return cached;
      // A damaged generation must not silently mix new scripts with old HTML.
      return new Response(
        "The offline app is incomplete. Close all tracker windows and reopen online to update.",
        {
          status: 503,
          headers: { "content-type": "text/plain; charset=utf-8" },
        },
      );
    })(),
  );
});
