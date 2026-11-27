CREATE TABLE IF NOT EXISTS bot_global_usage (
  day TEXT PRIMARY KEY,
  requests INTEGER NOT NULL DEFAULT 0 CHECK (requests >= 0),
  updated_at INTEGER NOT NULL
);