-- Controlo de custos: limite diario de traducoes por utilizador Pro.
CREATE TABLE translation_usage (
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  translations_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);
