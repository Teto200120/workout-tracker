CREATE TABLE IF NOT EXISTS feedback_reports (
  id TEXT PRIMARY KEY NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('bug', 'idea', 'other')),
  message TEXT NOT NULL,
  diagnostics_json TEXT,
  screenshot_key TEXT,
  screenshot_mime_type TEXT,
  screenshot_width INTEGER,
  screenshot_height INTEGER,
  client_created_at TEXT NOT NULL,
  received_at TEXT NOT NULL
);
