-- PiMsg (ISAB Group) - esquema inicial D1
-- Convencoes: ids TEXT (uuid), datas em INTEGER (milissegundos Unix, UTC).

-- ============ UTILIZADORES ============

CREATE TABLE users (
  id               TEXT PRIMARY KEY,
  firebase_uid     TEXT NOT NULL UNIQUE,
  email            TEXT,
  phone_e164       TEXT UNIQUE,               -- nunca usado em pesquisa
  phone_verified   INTEGER NOT NULL DEFAULT 0,
  show_phone       INTEGER NOT NULL DEFAULT 0, -- mostrar numero no perfil
  display_name     TEXT NOT NULL,
  bio              TEXT,
  avatar_key       TEXT,                       -- chave no R2
  intro_video_key  TEXT,                       -- so planos Pro
  ring_color       TEXT NOT NULL DEFAULT '#8E8E93', -- cor do circulo de story
  timezone         TEXT NOT NULL DEFAULT 'Africa/Luanda',
  status           TEXT NOT NULL DEFAULT 'active'
                   CHECK (status IN ('active','suspended','banned','deleted')),
  created_at       INTEGER NOT NULL,
  last_seen_at     INTEGER,
  deleted_at       INTEGER
);
CREATE INDEX idx_users_name ON users(display_name);

-- Codigo de pesquisa pessoal. Ao atualizar, o anterior expira.
CREATE TABLE user_codes (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code        TEXT NOT NULL UNIQUE,
  is_active   INTEGER NOT NULL DEFAULT 1,
  created_at  INTEGER NOT NULL,
  expired_at  INTEGER
);
CREATE UNIQUE INDEX idx_user_codes_one_active
  ON user_codes(user_id) WHERE is_active = 1;

CREATE TABLE devices (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_hash  TEXT NOT NULL,                  -- identificador do aparelho
  platform     TEXT NOT NULL DEFAULT 'android',
  fcm_token    TEXT,
  created_at   INTEGER NOT NULL,
  last_seen_at INTEGER,
  UNIQUE (user_id, device_hash)
);
CREATE INDEX idx_devices_hash ON devices(device_hash);

-- ============ PLANOS E CREDITOS ============

CREATE TABLE subscriptions (
  id                   TEXT PRIMARY KEY,
  user_id              TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan                 TEXT NOT NULL CHECK (plan IN ('pro_weekly','pro_monthly')),
  status               TEXT NOT NULL
                       CHECK (status IN ('active','grace','on_hold','canceled','expired','refunded')),
  play_purchase_token  TEXT NOT NULL UNIQUE,
  auto_renewing        INTEGER NOT NULL DEFAULT 1,
  started_at           INTEGER NOT NULL,
  expires_at           INTEGER NOT NULL,
  updated_at           INTEGER NOT NULL
);
CREATE INDEX idx_subs_user ON subscriptions(user_id, status, expires_at);

-- Compras avulsas: 10 mensagens por 1$ ou 15 horas ilimitadas
CREATE TABLE credit_purchases (
  id                   TEXT PRIMARY KEY,
  user_id              TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind                 TEXT NOT NULL CHECK (kind IN ('messages_10','unlimited_15h')),
  play_purchase_token  TEXT NOT NULL UNIQUE,
  refunded             INTEGER NOT NULL DEFAULT 0,
  created_at           INTEGER NOT NULL
);

CREATE TABLE user_credits (
  user_id          TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  extra_messages   INTEGER NOT NULL DEFAULT 0,
  unlimited_until  INTEGER NOT NULL DEFAULT 0,
  updated_at       INTEGER NOT NULL
);

-- Contador diario de mensagens (dia no fuso do utilizador, formato AAAA-MM-DD)
CREATE TABLE daily_usage (
  user_id        TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  day            TEXT NOT NULL,
  messages_sent  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);

-- ============ LINKS PERSONALIZADOS E PEDIDOS DE CONTACTO ============

CREATE TABLE contact_links (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  slug        TEXT NOT NULL UNIQUE,
  subject     TEXT NOT NULL,                   -- assunto ou produto
  intro_text  TEXT,                            -- texto da pagina do link
  is_active   INTEGER NOT NULL DEFAULT 1,
  opens_count INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);
CREATE INDEX idx_links_user ON contact_links(user_id);

-- Visitante informa o nome, gera um codigo, e o dono recebe notificacao
CREATE TABLE contact_requests (
  id              TEXT PRIMARY KEY,
  link_id         TEXT NOT NULL REFERENCES contact_links(id) ON DELETE CASCADE,
  visitor_name    TEXT NOT NULL,
  request_code    TEXT NOT NULL UNIQUE,
  visitor_user_id TEXT REFERENCES users(id) ON DELETE SET NULL,
  status          TEXT NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending','used','expired')),
  created_at      INTEGER NOT NULL,
  expires_at      INTEGER NOT NULL
);
CREATE INDEX idx_requests_link ON contact_requests(link_id, created_at);

-- ============ CONVERSAS, GRUPOS E MENSAGENS ============

CREATE TABLE conversations (
  id               TEXT PRIMARY KEY,
  type             TEXT NOT NULL CHECK (type IN ('direct','group')),
  created_by       TEXT NOT NULL REFERENCES users(id),
  created_at       INTEGER NOT NULL,
  last_message_at  INTEGER,
  -- conversa direta
  user_a_id        TEXT REFERENCES users(id),  -- id menor
  user_b_id        TEXT REFERENCES users(id),  -- id maior
  link_id          TEXT REFERENCES contact_links(id) ON DELETE SET NULL,
  -- grupo
  name             TEXT,
  description      TEXT,
  avatar_key       TEXT,
  invite_code      TEXT UNIQUE,
  CHECK ((type = 'direct' AND user_a_id IS NOT NULL AND user_b_id IS NOT NULL AND user_a_id < user_b_id)
      OR (type = 'group'  AND name IS NOT NULL AND invite_code IS NOT NULL))
);
CREATE UNIQUE INDEX idx_conv_direct_pair
  ON conversations(user_a_id, user_b_id) WHERE type = 'direct';

CREATE TABLE conversation_members (
  conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id         TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role            TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner','admin','member')),
  is_initiator    INTEGER NOT NULL DEFAULT 0,  -- quem iniciou (conta para o limite de 30 pessoas)
  sees_history    INTEGER NOT NULL DEFAULT 1,  -- escolha ao entrar no grupo
  joined_at       INTEGER NOT NULL,
  left_at         INTEGER,
  last_read_at    INTEGER NOT NULL DEFAULT 0,
  is_favorite     INTEGER NOT NULL DEFAULT 0,
  is_muted        INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_members_user ON conversation_members(user_id, left_at);

CREATE TABLE media (
  id           TEXT PRIMARY KEY,
  owner_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  r2_key       TEXT NOT NULL UNIQUE,
  mime         TEXT NOT NULL,
  size_bytes   INTEGER NOT NULL,
  width        INTEGER,
  height       INTEGER,
  duration_ms  INTEGER,
  created_at   INTEGER NOT NULL
);

CREATE TABLE messages (
  id               TEXT PRIMARY KEY,
  conversation_id  TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id        TEXT NOT NULL REFERENCES users(id),
  kind             TEXT NOT NULL DEFAULT 'text'
                   CHECK (kind IN ('text','image','video','audio','music','file','system')),
  body             TEXT,
  media_id         TEXT REFERENCES media(id) ON DELETE SET NULL,
  reply_to_id      TEXT REFERENCES messages(id) ON DELETE SET NULL,
  created_at       INTEGER NOT NULL,
  edited_at        INTEGER,
  deleted_at       INTEGER
);
CREATE INDEX idx_messages_conv ON messages(conversation_id, created_at);

-- ============ STORIES ============

CREATE TABLE stories (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  media_id    TEXT NOT NULL REFERENCES media(id),
  caption     TEXT,
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL
);
CREATE INDEX idx_stories_user ON stories(user_id, expires_at);

CREATE TABLE story_views (
  story_id   TEXT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  viewer_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  viewed_at  INTEGER NOT NULL,
  PRIMARY KEY (story_id, viewer_id)
);

-- ============ SEGURANCA, MODERACAO E LEGAL ============

CREATE TABLE blocks (
  blocker_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (blocker_id, blocked_id),
  CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_blocks_blocked ON blocks(blocked_id);

CREATE TABLE reports (
  id            TEXT PRIMARY KEY,
  reporter_id   TEXT NOT NULL REFERENCES users(id),
  target_type   TEXT NOT NULL CHECK (target_type IN ('user','message','group','story')),
  target_id     TEXT NOT NULL,
  reason        TEXT NOT NULL
                CHECK (reason IN ('spam','scam','harassment','inappropriate','illegal','impersonation','other')),
  details       TEXT,
  status        TEXT NOT NULL DEFAULT 'open'
                CHECK (status IN ('open','reviewing','actioned','dismissed')),
  reviewed_by   TEXT REFERENCES users(id),
  resolution    TEXT,
  created_at    INTEGER NOT NULL,
  resolved_at   INTEGER
);
CREATE INDEX idx_reports_status ON reports(status, created_at);
CREATE INDEX idx_reports_target ON reports(target_type, target_id);

CREATE TABLE admins (
  user_id    TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  role       TEXT NOT NULL DEFAULT 'moderator' CHECK (role IN ('moderator','admin')),
  created_at INTEGER NOT NULL
);

-- Aviso, suspensao temporaria, banimento e apelo ficam registados aqui
CREATE TABLE moderation_actions (
  id              TEXT PRIMARY KEY,
  admin_id        TEXT NOT NULL REFERENCES users(id),
  target_user_id  TEXT NOT NULL REFERENCES users(id),
  report_id       TEXT REFERENCES reports(id) ON DELETE SET NULL,
  action          TEXT NOT NULL CHECK (action IN ('warn','suspend','ban','restore','remove_content')),
  reason          TEXT NOT NULL,
  appeal_status   TEXT NOT NULL DEFAULT 'none' CHECK (appeal_status IN ('none','pending','accepted','rejected')),
  created_at      INTEGER NOT NULL,
  expires_at      INTEGER
);
CREATE INDEX idx_modact_target ON moderation_actions(target_user_id, created_at);

-- Pedidos de apagar conta ou exportar dados
CREATE TABLE data_requests (
  id            TEXT PRIMARY KEY,
  user_id       TEXT REFERENCES users(id) ON DELETE SET NULL,
  type          TEXT NOT NULL CHECK (type IN ('delete','export')),
  source        TEXT NOT NULL DEFAULT 'app' CHECK (source IN ('app','web')),
  contact_email TEXT,
  status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','done','canceled')),
  requested_at  INTEGER NOT NULL,
  scheduled_for INTEGER,
  completed_at  INTEGER
);

-- Aceitacao de termos e politicas (guarda a versao aceite)
CREATE TABLE legal_acceptances (
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  doc_type     TEXT NOT NULL CHECK (doc_type IN ('terms','privacy','refund','community')),
  version      TEXT NOT NULL,
  accepted_at  INTEGER NOT NULL,
  PRIMARY KEY (user_id, doc_type, version)
);
