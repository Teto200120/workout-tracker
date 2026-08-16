/* global fetch */

const RESEND_URL = "https://api.resend.com/emails";

function atNewYorkSevenPm(date) {
  return new Intl.DateTimeFormat("en-US", {
    hour: "numeric", hour12: false, timeZone: "America/New_York",
  }).format(date) === "19";
}

function configured(env) {
  return ["RESEND_API_KEY", "RESEND_FROM", "DIGEST_RECIPIENT", "D1_REVIEW_URL", "R2_REVIEW_URL"].every((key) => typeof env[key] === "string" && env[key]);
}

function email(rows, env) {
  const counts = Object.fromEntries(["bug", "idea", "other"].map((category) => [category, 0]));
  let screenshots = 0;
  for (const row of rows) { counts[row.category] += 1; if (row.has_screenshot) screenshots += 1; }
  return {
    from: env.RESEND_FROM, to: [env.DIGEST_RECIPIENT],
    subject: `Workout Tracker feedback digest: ${rows.length} new`,
    text: `New feedback reports: ${rows.length}\nBug: ${counts.bug}\nIdea: ${counts.idea}\nOther: ${counts.other}\nWith screenshots: ${screenshots}\n\nReview reports: ${env.D1_REVIEW_URL}\nReview screenshots: ${env.R2_REVIEW_URL}`,
  };
}

export async function runFeedbackDigest(env, { now = () => new Date(), fetcher = fetch } = {}) {
  if (!atNewYorkSevenPm(now()) || !configured(env)) return { skipped: true };
  const pending = await env.FEEDBACK_DB.prepare("SELECT r.id, r.category, CASE WHEN r.screenshot_key IS NOT NULL THEN 1 ELSE 0 END AS has_screenshot FROM feedback_reports r LEFT JOIN feedback_digest_deliveries d ON d.report_id = r.id WHERE d.report_id IS NULL").all();
  const rows = pending.results || [];
  if (!rows.length) return { empty: true };
  const response = await fetcher(RESEND_URL, { method: "POST", headers: { authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" }, body: JSON.stringify(email(rows, env)) });
  if (!response.ok) return { delivered: false };
  await env.FEEDBACK_DB.batch(rows.map((row) => env.FEEDBACK_DB.prepare("INSERT INTO feedback_digest_deliveries (report_id, delivered_at) VALUES (?, ?)").bind(row.id, now().toISOString())));
  return { delivered: true, count: rows.length };
}
