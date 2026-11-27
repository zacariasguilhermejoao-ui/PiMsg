-- Expand subscription plan values to support all current PiMsg tiers.
CREATE TABLE subscriptions_new (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan TEXT NOT NULL CHECK (plan IN ('plus','pro_weekly','pro_monthly','pro','business')),
  status TEXT NOT NULL CHECK (status IN ('active','grace','on_hold','canceled','expired','refunded')),
  play_purchase_token TEXT NOT NULL UNIQUE,
  auto_renewing INTEGER NOT NULL DEFAULT 1,
  started_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
INSERT INTO subscriptions_new
  (id, user_id, plan, status, play_purchase_token, auto_renewing, started_at, expires_at, updated_at)
SELECT
  id, user_id, plan, status, play_purchase_token, auto_renewing, started_at, expires_at, updated_at
FROM subscriptions;
DROP TABLE subscriptions;
ALTER TABLE subscriptions_new RENAME TO subscriptions;
