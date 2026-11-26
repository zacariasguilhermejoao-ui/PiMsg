-- Anti-abuse: retain only a one-hour rolling counter keyed by a one-way hash of the client IP.
CREATE TABLE IF NOT EXISTS signup_ip_limits (
  ip_hash TEXT PRIMARY KEY,
  window_started_at INTEGER NOT NULL,
  signup_count INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
