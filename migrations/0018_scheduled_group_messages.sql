-- Scheduled text messages for group conversations.
CREATE TABLE scheduled_messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  scheduled_at INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','sending','sent','cancelled','failed')),
  created_at INTEGER NOT NULL,
  sent_at INTEGER
);
CREATE INDEX idx_scheduled_due ON scheduled_messages(status, scheduled_at);
CREATE INDEX idx_scheduled_sender ON scheduled_messages(sender_id, conversation_id, status);
