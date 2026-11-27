-- PiMsg: notification preferences, typing presence and AI credits
ALTER TABLE users ADD COLUMN push_enabled INTEGER NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN popup_enabled INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS typing_states (
  conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_typing_expiry ON typing_states(conversation_id, updated_at);

CREATE TABLE IF NOT EXISTS ai_credits (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  balance INTEGER NOT NULL DEFAULT 15 CHECK (balance >= 0),
  updated_at INTEGER NOT NULL
);
INSERT OR IGNORE INTO ai_credits (user_id, balance, updated_at)
SELECT id, 15, CAST(strftime('%s','now') AS INTEGER) * 1000 FROM users;

CREATE TABLE IF NOT EXISTS message_translations (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  language TEXT NOT NULL CHECK (language IN ('pt','en','es','fr')),
  translated_body TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, message_id, language)
);
