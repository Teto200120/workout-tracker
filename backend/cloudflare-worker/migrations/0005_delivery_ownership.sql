ALTER TABLE feedback_report_claims ADD COLUMN owner TEXT;
CREATE TABLE IF NOT EXISTS feedback_digest_batches (
  id TEXT PRIMARY KEY NOT NULL,
  payload_json TEXT NOT NULL,
  report_ids_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  completed_at TEXT
);
