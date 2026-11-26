ALTER TABLE conversations ADD COLUMN disappearing_seconds INTEGER NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN expires_at INTEGER;
