/* global fetch */
import { withDeadline } from "../../../src/js/application/deadline.js";

const RESEND_URL = "https://api.resend.com/emails";

function atNewYorkSevenPm(date) {
  return (
    new Intl.DateTimeFormat("en-US", {
      hour: "numeric",
      hour12: false,
      timeZone: "America/New_York",
    }).format(date) === "19"
  );
}

function configured(env) {
  return [
    "RESEND_API_KEY",
    "RESEND_FROM",
    "DIGEST_RECIPIENT",
    "D1_REVIEW_URL",
    "R2_REVIEW_URL",
  ].every((key) => typeof env[key] === "string" && env[key]);
}

function email(rows, env) {
  const counts = Object.fromEntries(
    ["bug", "idea", "other"].map((category) => [category, 0]),
  );
  let screenshots = 0;
  for (const row of rows) {
    counts[row.category] += 1;
    if (row.has_screenshot) screenshots += 1;
  }
  return {
    from: env.RESEND_FROM,
    to: [env.DIGEST_RECIPIENT],
    subject: `Workout Tracker feedback digest: ${rows.length} new`,
    text: `New feedback reports: ${rows.length}\nBug: ${counts.bug}\nIdea: ${counts.idea}\nOther: ${counts.other}\nWith screenshots: ${screenshots}\n\nReview reports: ${env.D1_REVIEW_URL}\nReview screenshots: ${env.R2_REVIEW_URL}`,
  };
}

export async function runFeedbackDigest(
  env,
  { now = () => new Date(), fetcher = fetch } = {},
) {
  const started = now();
  if (!atNewYorkSevenPm(started) || !configured(env)) return { skipped: true };
  // One immutable batch per local calendar day. Retries reuse exactly the same
  // body and provider key, including when D1 marking failed after provider success.
  const day = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/New_York",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(started);
  const id = `workout-feedback/${day}`;
  let batch = await env.FEEDBACK_DB.prepare(
    "SELECT * FROM feedback_digest_batches WHERE id = ?",
  )
    .bind(id)
    .first();
  const unresolved = await env.FEEDBACK_DB.prepare(
    "SELECT id FROM feedback_digest_batches WHERE completed_at IS NULL AND id != ? LIMIT 1",
  )
    .bind(id)
    .first();
  // Provider keys expire after 24h. Don't automatically resend an ambiguous old
  // batch or absorb its reports into a new batch; require operator reconciliation.
  if (unresolved) return { delivered: false, code: "reconciliation_required" };
  if (!batch) {
    const pending = await env.FEEDBACK_DB.prepare(
      "SELECT r.id, r.category, CASE WHEN r.screenshot_key IS NOT NULL THEN 1 ELSE 0 END AS has_screenshot FROM feedback_reports r LEFT JOIN feedback_digest_deliveries d ON d.report_id = r.id WHERE d.report_id IS NULL ORDER BY r.id",
    ).all();
    const rows = pending.results || [];
    if (!rows.length) return { empty: true };
    await env.FEEDBACK_DB.prepare(
      "INSERT INTO feedback_digest_batches (id, payload_json, report_ids_json, created_at) VALUES (?, ?, ?, ?) ON CONFLICT(id) DO NOTHING",
    )
      .bind(
        id,
        JSON.stringify(email(rows, env)),
        JSON.stringify(rows.map((row) => row.id)),
        started.toISOString(),
      )
      .run();
    batch = await env.FEEDBACK_DB.prepare(
      "SELECT * FROM feedback_digest_batches WHERE id = ?",
    )
      .bind(id)
      .first();
  }
  if (batch.completed_at) return { skipped: true };
  if (started.getTime() - Date.parse(batch.created_at) >= 23 * 60 * 60 * 1000)
    return { delivered: false, code: "reconciliation_required" };
  let response;
  try {
    response = await withDeadline(
      (signal) =>
        fetcher(RESEND_URL, {
          method: "POST",
          signal,
          headers: {
            authorization: `Bearer ${env.RESEND_API_KEY}`,
            "content-type": "application/json",
            "Idempotency-Key": id,
          },
          body: batch.payload_json,
        }),
      20_000,
    );
  } catch {
    return { delivered: false };
  }
  if (!response.ok) return { delivered: false };
  const ids = JSON.parse(batch.report_ids_json);
  const deliveredAt = now().toISOString();
  await env.FEEDBACK_DB.batch([
    ...ids.map((reportId) =>
      env.FEEDBACK_DB.prepare(
        "INSERT INTO feedback_digest_deliveries (report_id, delivered_at) VALUES (?, ?) ON CONFLICT(report_id) DO NOTHING",
      ).bind(reportId, deliveredAt),
    ),
    env.FEEDBACK_DB.prepare(
      "UPDATE feedback_digest_batches SET completed_at = ? WHERE id = ?",
    ).bind(deliveredAt, id),
  ]);
  return { delivered: true, count: ids.length };
}
