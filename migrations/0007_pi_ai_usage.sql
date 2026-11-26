-- Contador diário de pedidos ao Pi AI, separado das mensagens entre utilizadores.
CREATE TABLE IF NOT EXISTS bot_daily_usage (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  requests INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, day)
);
