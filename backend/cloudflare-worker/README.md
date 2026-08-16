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

The live PWA holds only the public receiver URL and public Turnstile site key.
It keeps its local outbox entry until this receiver confirms `{ ok: true }`.

## Storage and retention

D1 stores only the structured report metadata/text and the R2 object key. R2
stores only the decoded optional screenshot under a private, per-attempt key.
Neither object storage nor D1 is public through this Worker.

Reports are retained until an authorized future operator deletes both the D1
row and any corresponding R2 object. Do not configure automatic deletion.
Thirty days is a future operator-review reminder, not a purge policy.

An in-flight D1 claim serializes attempts with the same report ID. It expires
after five minutes so an interrupted invocation cannot block the user's local
retry forever. If an R2 write succeeds but the D1 insert fails, the Worker
first attempts to delete that attempt's private screenshot object, then returns
an error and the PWA retains its local outbox entry for retry. A reclaimed
retry uses a different private object key, so compensation cannot delete a
separate in-flight attempt's screenshot; no successful response is returned
until D1 has accepted the report.

## PWA integration

The PWA now uses the public endpoint and public site key in
`src/js/application/feedback-transport.js`. Any later endpoint change must:

1. supply the public Turnstile site key through deployment-safe app
   configuration (never a secret);
2. obtain a fresh Turnstile token for each queued send attempt;
3. wrap the existing `createFeedbackTransportPayload(report)` result as
   `{ report, turnstileToken }` and POST it to the approved HTTPS Worker URL;
4. keep the current queue-before-send semantics, treating non-2xx or malformed
   responses as retryable and removing the local report only after `{ ok: true
}`.

Only the endpoint URL and public site key may be supplied to the PWA; Worker
secrets and bindings remain deployment-only configuration.

## Deployment boundary

The selected deployment uses the account's default `workers.dev` hostname,
automatic Cloudflare placement, a D1 database named `workout-tracker-beta-feedback`,
and a private R2 bucket named `workout-tracker-beta-feedback-screenshots`. The
Turnstile secret must be set through `wrangler secret put TURNSTILE_SECRET`,
never committed. Do not create R2 lifecycle rules: reports require explicit
operator deletion.

## Daily digest

Cron windows run at 23:00 and 00:00 UTC; the handler checks
`America/New_York` and sends only at local 7 PM, including DST changes. Set
`RESEND_FROM`, `DIGEST_RECIPIENT`, `D1_REVIEW_URL`, and `R2_REVIEW_URL` as
Worker vars; set `RESEND_API_KEY` only as a Worker secret. Resend sender
verification is user-owned. The digest contains only counts, category totals,
screenshot count, and configured private review links. It never includes
report content, IDs, diagnostics, screenshots, or object keys. Delivery state
is written only after a successful provider response.

For one controlled immediate test after configuration, use `wrangler dev --test-scheduled`
against a staging/controlled Worker and invoke the scheduled handler once; it
uses the same logic and durable delivery markers, so repeating it cannot email
already delivered reports. Do not run this against production or send mail
until the user confirms the final test.
