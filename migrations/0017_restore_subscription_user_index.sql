-- Restore the per-user subscription lookup index after the subscription table rebuild.
CREATE INDEX IF NOT EXISTS idx_subs_user ON subscriptions(user_id, status, expires_at);
