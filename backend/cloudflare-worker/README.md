# Beta feedback receiver

This is a separately deployed Cloudflare Worker receiver. It is not bundled
with the PWA and exposes no report-reading, operator, or admin endpoint.

## Contract

`POST` requests must be JSON objects with exactly `report` (the existing PWA
v1 transport payload) and `turnstileToken`. The Worker validates the complete
payload before persistence, accepts only the existing diagnostics allowlist,
and returns only delivery status. It never returns saved feedback data.

The Worker requires these deployment-time bindings. The non-secret bindings
are declared in `wrangler.jsonc`; the Turnstile secret is configured only in
the deployed Worker:

- `FEEDBACK_DB`: D1 database binding for metadata and message text.
- `FEEDBACK_SCREENSHOTS`: private R2 bucket binding for optional screenshot
  objects.
- `FEEDBACK_RATE_LIMITER`: Worker rate-limit binding. Configure conservatively
  (recommended starting point: 3 submissions per 60 seconds per ephemeral
  request IP). The IP is passed only to the binding and is never written to D1
  or R2.
- `ALLOWED_ORIGIN`: the exact HTTPS PWA origin; no wildcard origins.
- `TURNSTILE_SECRET`: a Worker secret for server-side Siteverify. Never place
  it in the PWA, source repository, or client request.

The client needs a separately configured public Turnstile site key before the
mock transport can be replaced. That endpoint/site-key wiring remains out of
scope here.

## Storage and retention

D1 stores only the structured report metadata/text and the R2 object key. R2
stores only the decoded optional screenshot under a deterministic report ID
key. Neither object storage nor D1 is public through this Worker.

Reports are retained until an authorized future operator deletes both the D1
row and any corresponding R2 object. Do not configure automatic deletion.
Thirty days is a future operator-review reminder, not a purge policy.

If an R2 write succeeds but the D1 insert fails, the Worker first attempts to
delete the private screenshot object, then returns an error and the PWA retains
its local outbox entry for retry. If that compensating delete also fails, a
later retry overwrites only the same deterministic private object key; no
successful response is returned until D1 has accepted the report.

## Future PWA integration path

The PWA remains on its mocked transport in this change. When the operator has
created the Worker and Turnstile widget, a separate approved client change must:

1. supply the public Turnstile site key through deployment-safe app
   configuration (never a secret);
2. obtain a fresh Turnstile token for each queued send attempt;
3. wrap the existing `createFeedbackTransportPayload(report)` result as
   `{ report, turnstileToken }` and POST it to the approved HTTPS Worker URL;
4. keep the current queue-before-send semantics, treating non-2xx or malformed
   responses as retryable and removing the local report only after `{ ok: true
}`.

The endpoint URL, public site key delivery, and Worker binding values are
operational decisions and are intentionally not present in the PWA or this
repository configuration.

## Deployment boundary

The selected deployment uses the account's default `workers.dev` hostname,
automatic Cloudflare placement, a D1 database named `workout-tracker-beta-feedback`,
and a private R2 bucket named `workout-tracker-beta-feedback-screenshots`. The
Turnstile secret must be set through `wrangler secret put TURNSTILE_SECRET`,
never committed. Do not create R2 lifecycle rules: reports require explicit
operator deletion.
