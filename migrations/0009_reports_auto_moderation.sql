-- Suspensao e banimento temporarios por denuncias distintas de contas
ALTER TABLE users ADD COLUMN status_until INTEGER;

CREATE TABLE IF NOT EXISTS automated_moderation_events (
  id TEXT PRIMARY KEY,
  target_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  action TEXT NOT NULL CHECK (action IN ('suspend_24h','ban_30d')),
  distinct_reports INTEGER NOT NULL,
  window_started_at INTEGER NOT NULL,
  window_ended_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auto_mod_target ON automated_moderation_events(target_user_id, created_at);
