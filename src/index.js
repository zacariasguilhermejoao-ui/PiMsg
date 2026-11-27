
import { handleChat } from "./chat.js";
import { handleBots } from "./bots.js";
import { handleExtra } from "./extra.js";
import { handlePublic } from "./pages.js";
import { handleBilling, refreshExpiringSubscriptions } from "./billing.js";
import { pushToUsers } from "./push.js";

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status, error) => json({ error }, status);

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(s + "=".repeat((4 - (s.length % 4)) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

function randomCode(len) {
  const bytes = crypto.getRandomValues(new Uint8Array(len));
  return Array.from(bytes, (b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

async function verifyFirebaseToken(token, env) {
  const [h, p, s] = token.split(".");
  if (!h || !p || !s) throw new Error("token invalido");
  const header = JSON.parse(new TextDecoder().decode(b64urlToBytes(h)));
  const payload = JSON.parse(new TextDecoder().decode(b64urlToBytes(p)));
  if (header.alg !== "RS256") throw new Error("algoritmo invalido");

  const res = await fetch(JWKS_URL, { cf: { cacheTtl: 3600, cacheEverything: true } });
  const { keys } = await res.json();
  const jwk = keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error("chave desconhecida");

  const key = await crypto.subtle.importKey(
    "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
  );
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", key, b64urlToBytes(s), new TextEncoder().encode(`${h}.${p}`)
  );
  if (!ok) throw new Error("assinatura invalida");

  const now = Math.floor(Date.now() / 1000);
  const pid = env.FIREBASE_PROJECT_ID;
  if (!pid) throw new Error("FIREBASE_PROJECT_ID nao configurado");
  if (payload.aud !== pid || payload.iss !== `https://securetoken.google.com/${pid}`) throw new Error("projeto invalido");
  if (!payload.sub || payload.exp < now || payload.iat > now + 60) throw new Error("token expirado");
  return payload;
}

async function authenticate(request, env) {
  const auth = request.headers.get("authorization") || "";
  if (!auth.startsWith("Bearer ")) return null;
  let claims;
  try {
    claims = await verifyFirebaseToken(auth.slice(7), env);
  } catch {
    return null;
  }
  const now = Date.now();
  let user = await env.DB.prepare("SELECT * FROM users WHERE firebase_uid = ?").bind(claims.sub).first();
  if (!user) {
    const clientIp = request.headers.get("CF-Connecting-IP");
    if (clientIp) {
      const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(clientIp));
      const ipHash = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
      // Remove expired hashed IP counters; retain no raw IP address.
      await env.DB.prepare("DELETE FROM signup_ip_limits WHERE updated_at < ?").bind(now - 24 * 60 * 60 * 1000).run();
      const windowStart = now - 60 * 60 * 1000;
      const rate = await env.DB.prepare(
        `INSERT INTO signup_ip_limits (ip_hash, window_started_at, signup_count, updated_at)
         VALUES (?, ?, 1, ?)
         ON CONFLICT(ip_hash) DO UPDATE SET
           signup_count = CASE WHEN signup_ip_limits.window_started_at <= ? THEN 1 ELSE signup_ip_limits.signup_count + 1 END,
           window_started_at = CASE WHEN signup_ip_limits.window_started_at <= ? THEN excluded.window_started_at ELSE signup_ip_limits.window_started_at END,
           updated_at = excluded.updated_at
         RETURNING signup_count`
      ).bind(ipHash, now, now, windowStart, windowStart).first();
      if ((rate?.signup_count || 0) > 10) return { blocked: "signup_rate_limited" };
    }
    const id = crypto.randomUUID();
    const phone = claims.phone_number || null;
    const name = claims.name || (claims.email ? claims.email.split("@")[0] : "Utilizador");
    await env.DB.batch([
      env.DB.prepare(
        "INSERT INTO users (id, firebase_uid, email, email_verified, phone_e164, phone_verified, display_name, created_at, last_seen_at) VALUES (?,?,?,?,?,?,?,?,?)"
      ).bind(id, claims.sub, claims.email || null, claims.email_verified ? 1 : 0, phone, phone ? 1 : 0, name, now, now),
      env.DB.prepare(
        "INSERT INTO user_codes (id, user_id, code, is_active, created_at) VALUES (?,?,?,1,?)"
      ).bind(crypto.randomUUID(), id, randomCode(8), now),
    ]);
    user = await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(id).first();
  }
  if (user.status !== "active") {
    if ((user.status === "suspended" || user.status === "banned") && user.status_until && user.status_until <= now) {
      await env.DB.prepare("UPDATE users SET status = 'active', status_until = NULL WHERE id = ?").bind(user.id).run();
      user.status = "active";
      user.status_until = null;
    } else {
      return { blocked: user.status, status_until: user.status_until || null };
    }
  }
  if (claims.email) {
    const verified = claims.email_verified ? 1 : 0;
    if (user.email !== claims.email || Number(user.email_verified || 0) !== verified) {
      await env.DB.prepare("UPDATE users SET email = ?, email_verified = ? WHERE id = ?")
        .bind(claims.email, verified, user.id).run();
      user.email = claims.email;
      user.email_verified = verified;
    }
  }
  await env.DB.prepare("UPDATE users SET last_seen_at = ? WHERE id = ? AND (last_seen_at IS NULL OR last_seen_at < ?)").bind(now, user.id, now - 30000).run();
  user.last_seen_at = now;
  return user;
}


async function monthlyLinkReplyUsage(env, userId, now) {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: "Africa/Luanda", year: "numeric", month: "2-digit" }).formatToParts(new Date(now));
  const year = Number(parts.find((p) => p.type === "year")?.value);
  const month = Number(parts.find((p) => p.type === "month")?.value);
  const start = Date.UTC(year, month - 1, 1) - 3600000;
  const resetsAt = Date.UTC(year, month, 1) - 3600000;
  const row = await env.DB.prepare(
    `SELECT COUNT(*) AS n FROM messages m JOIN conversations c ON c.id = m.conversation_id
     JOIN contact_links l ON l.id = c.link_id
     WHERE l.user_id = ? AND m.sender_id = ? AND m.kind <> 'system'
       AND m.created_at >= ? AND m.created_at < ?`
  ).bind(userId, userId, start, resetsAt).first();
  return { used: row?.n || 0, resets_at: resetsAt };
}

const publicUser = (u) => ({
  id: u.id,
  name: u.display_name,
  bio: u.bio,
  avatar_key: u.avatar_key,
  ring_color: u.ring_color,
  phone: u.show_phone ? (u.phone_contact || u.phone_e164 || null) : null,
  online: !!u.show_online_status && !!u.last_seen_at && u.last_seen_at > Date.now() - 90000,
});

async function activeCode(env, userId) {
  return env.DB.prepare("SELECT code, created_at FROM user_codes WHERE user_id = ? AND is_active = 1").bind(userId).first();
}

async function handle(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method === "POST" && request.headers.get("x-http-method-override") === "PATCH" ? "PATCH" : request.method;

  if (path === "/health") return json({ ok: true, app: "PiMsg" });

  const publicResponse = await handlePublic(request, env);
  if (publicResponse) return publicResponse;

  const me = await authenticate(request, env);
  if (!me) return fail(401, "nao autenticado");
  if (me.blocked) {
    if (me.blocked === "signup_rate_limited") return fail(429, "limite de criacao de contas nesta rede; tente mais tarde");
    const until = me.status_until ? new Date(me.status_until).toLocaleString("pt-PT", { timeZone: "Africa/Luanda" }) : null;
    const message = me.blocked === "suspended"
      ? "A tua conta está suspensa temporariamente" + (until ? " até " + until + " (hora de Angola)." : ".")
      : "A tua conta está banida temporariamente" + (until ? " até " + until + " (hora de Angola)." : ".");
    return fail(403, message);
  }

  if (path === "/v1/legal/accept" && method === "POST") {
    const body = await request.json().catch(() => ({}));
    const now = Date.now();
    if (body.terms_version !== "1.3" || body.privacy_version !== "1.3") {
      return fail(400, "versão dos documentos inválida; actualize a aplicação");
    }
    await env.DB.batch([
      env.DB.prepare("INSERT OR IGNORE INTO legal_acceptances (user_id, doc_type, version, accepted_at) VALUES (?,?,?,?)")
        .bind(me.id, "terms", "1.3", now),
      env.DB.prepare("INSERT OR IGNORE INTO legal_acceptances (user_id, doc_type, version, accepted_at) VALUES (?,?,?,?)")
        .bind(me.id, "privacy", "1.3", now),
    ]);
    return json({ ok: true, accepted_at: now, version: "1.3" });
  }

  if (path === "/v1/plan" && method === "GET") {
    const now = Date.now();
    const sub = await env.DB.prepare(
      "SELECT plan, status, expires_at, auto_renewing FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? ORDER BY expires_at DESC LIMIT 1"
    ).bind(me.id, now).first();
    let day;
    try {
      day = new Intl.DateTimeFormat("en-CA", { timeZone: me.timezone || "Africa/Luanda" }).format(new Date());
    } catch {
      day = new Date().toISOString().slice(0, 10);
    }
    const usage = await env.DB.prepare(
      "SELECT messages_sent FROM daily_usage WHERE user_id = ? AND day = ?"
    ).bind(me.id, day).first();
    const linkUsage = await monthlyLinkReplyUsage(env, me.id, now);
    const aiUsage = await env.DB.prepare(
      "SELECT requests FROM bot_daily_usage WHERE user_id = ? AND day = ?"
    ).bind(me.id, day).first();
    const translationUsage = await env.DB.prepare(
      "SELECT translations FROM bot_translation_usage WHERE user_id = ? AND day = ?"
    ).bind(me.id, day).first();
    const tier = !sub ? "free" : String(sub.plan).startsWith("business") ? "business" : String(sub.plan).startsWith("plus") ? "plus" : "pro";
    const tierLimits = {
      free: { ai: 0, translations: 4, custom_bots: 0, business: false },
      plus: { ai: 5, translations: 25, custom_bots: 0, reminders: 20, business: false },
      pro: { ai: 20, translations: 75, custom_bots: 5, reminders: 100, business: false },
      business: { ai: 50, translations: 150, custom_bots: 20, reminders: 500, business: true },
    }[tier];
    const mediaUsage = await env.DB.prepare("SELECT COALESCE(SUM(size_bytes), 0) AS used FROM media WHERE owner_id = ?").bind(me.id).first();
    const storageLimits = { free: 250, plus: 2048, pro: 5120, business: 20480 };
    const storageLimitBytes = storageLimits[tier] * 1024 * 1024;
    return json({
      current_plan: sub ? sub.plan : "free",
      current_tier: tier,
      billing_ready: !!env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON,
      email: me.email || null,
      email_verified: !!me.email_verified,
      subscription_status: sub?.status || "free",
      expires_at: sub?.expires_at || null,
      auto_renewing: sub ? !!sub.auto_renewing : false,
      message_usage: usage?.messages_sent || 0,
      message_limit: sub ? null : 30,
      personalized_link_reply_usage: linkUsage.used,
      personalized_link_reply_limit: sub ? null : 500,
      personalized_link_reply_remaining: sub ? null : Math.max(0, 500 - linkUsage.used),
      personalized_link_reply_resets_at: linkUsage.resets_at,
      ai_usage: sub ? (aiUsage?.requests || 0) : 0,
      ai_limit: tierLimits.ai,
      translation_usage: translationUsage?.translations || 0,
      translation_limit: tierLimits.translations,
      translation_remaining: Math.max(0, tierLimits.translations - (translationUsage?.translations || 0)),
      custom_bot_limit: tierLimits.custom_bots,
      storage_used_bytes: Number(mediaUsage?.used || 0),
      storage_limit_bytes: storageLimitBytes,
      business_tools: tierLimits.business,
      bots_available: !!sub,
      plans: {
        free: { contacts: 30, messages_per_day: 30, personalized_link_replies_per_month: 500, translations_per_day: 4, ai_requests_per_day: 0, storage_mb: 250 },
        plus: { messages_per_day: null, personalized_link_replies_per_month: null, translations_per_day: 25, ai_requests_per_day: 5, reminders: 20, storage_gb: 2 },
        pro: { messages_per_day: null, personalized_link_replies_per_month: null, translations_per_day: 75, ai_requests_per_day: 20, reminders: 100, custom_bots: 5, storage_gb: 5, profile_video: true },
        business: { messages_per_day: null, personalized_link_replies_per_month: null, translations_per_day: 150, ai_requests_per_day: 50, reminders: 500, custom_bots: 20, storage_gb: 20, pi_business: true, profile_video: true }
      }
    });
  }

  if (path === "/v1/me" && method === "GET") {
    const code = await activeCode(env, me.id);
    return json({
      id: me.id, name: me.display_name, bio: me.bio, avatar_key: me.avatar_key, profile_cover_key: me.profile_cover_key,
      instagram_url: me.instagram_url, facebook_url: me.facebook_url,
      tiktok_url: me.tiktok_url, youtube_url: me.youtube_url, website_url: me.website_url,
      email: me.email, email_verified: !!me.email_verified, professional_profile: !!me.email_verified, phone_verified: !!me.phone_verified,
      phone_contact: me.phone_contact || null, show_phone: !!me.show_phone, show_online_status: me.show_online_status !== 0, intro_video_key: me.intro_video_key, timezone: me.timezone, code: code?.code,
    });
  }
  const publicProfileMatch = path.match(/^\/v1\/users\/([\w-]+)$/);
  if (publicProfileMatch && method === "GET") {
    const targetId = publicProfileMatch[1];
    const target = await env.DB.prepare(
      "SELECT id, display_name, bio, avatar_key, intro_video_key, ring_color, phone_contact, phone_e164, show_phone, show_online_status, last_seen_at, status, instagram_url, facebook_url, tiktok_url, youtube_url, website_url FROM users WHERE id = ? AND status = 'active'"
    ).bind(targetId).first();
    if (!target) return fail(404, "utilizador nao encontrado");
    const blocked = await env.DB.prepare(
      "SELECT 1 AS ok FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
    ).bind(me.id, targetId, targetId, me.id).first();
    if (blocked) return fail(403, "perfil indisponivel");
    const customLinks = await env.DB.prepare(
      "SELECT id, label, url FROM user_social_links WHERE user_id = ? ORDER BY created_at DESC"
    ).bind(targetId).all();
    const socialLinks = (customLinks.results || []).slice();
    for (const [key, label] of [
      ["instagram_url", "Instagram"], ["facebook_url", "Facebook"],
      ["tiktok_url", "TikTok"], ["youtube_url", "YouTube"], ["website_url", "Website"]
    ]) {
      const value = target[key];
      if (value && !socialLinks.some((item) => item.url === value)) {
        socialLinks.push({ id: key, label, url: value });
      }
    }
    return json({
      id: target.id, name: target.display_name, bio: target.bio,
      avatar_key: target.avatar_key, intro_video_key: target.intro_video_key, profile_cover_key: target.profile_cover_key,
      ring_color: target.ring_color,
      phone: target.show_phone ? (target.phone_contact || target.phone_e164 || null) : null,
      online: !!target.show_online_status && !!target.last_seen_at && target.last_seen_at > Date.now() - 90000,
      social_links: socialLinks,
    });
  }

  if (path === "/v1/me" && method === "PATCH") {
    const b = await request.json().catch(() => ({}));
    const name = typeof b.name === "string" ? b.name.trim().slice(0, 60) : me.display_name;
    const bio = typeof b.bio === "string" ? b.bio.slice(0, 300) : me.bio;
    const show = typeof b.show_phone === "boolean" ? (b.show_phone && (me.phone_verified || me.phone_contact) ? 1 : 0) : me.show_phone;
    const showOnline = typeof b.show_online_status === "boolean" ? (b.show_online_status ? 1 : 0) : (me.show_online_status ?? 1);
    const tz = typeof b.timezone === "string" ? b.timezone.slice(0, 60) : me.timezone;
    const hasPhone = Object.prototype.hasOwnProperty.call(b, "phone_e164");
    const phone = hasPhone && typeof b.phone_e164 === "string" ? b.phone_e164.trim() : null;
    if (!name) return fail(400, "nome obrigatorio");
    if (hasPhone) {
      if (!phone || !/^\+[1-9]\d{7,14}$/.test(phone)) return fail(400, "numero de telefone invalido");
      // Unverified numbers are contact details only. Never reserve the verified phone_e164
      // field or let an unverified claim block the real owner from registering later.
      await env.DB.prepare("UPDATE users SET phone_contact = ? WHERE id = ?").bind(phone, me.id).run();
    }
    const social = ["instagram_url", "facebook_url", "tiktok_url", "youtube_url", "website_url"];
    const values = social.map((key) => {
      const raw = typeof b[key] === "string" ? b[key].trim().slice(0, 200) : me[key];
      return raw || null;
    });
    if (values.some((value) => value && !/^https:\/\//i.test(value))) {
      return fail(400, "As ligações sociais devem começar por https://");
    }
    const ringColor = typeof b.ring_color === "string" ? b.ring_color.trim().slice(0, 7) : me.ring_color;
    if (ringColor && !/^#[0-9a-fA-F]{6}$/.test(ringColor)) return fail(400, "cor do perfil invalida");
    await env.DB.prepare(
      "UPDATE users SET display_name=?, bio=?, show_phone=?, show_online_status=?, timezone=?, instagram_url=?, facebook_url=?, tiktok_url=?, youtube_url=?, website_url=?, ring_color=? WHERE id=?"
    ).bind(name, bio, show, showOnline, tz, ...values, ringColor || "#8E8E93", me.id).run();
    return json({ ok: true, phone_verified: !!me.phone_verified });
  }

  if (path === "/v1/me/code/regenerate" && method === "POST") {
    const now = Date.now();
    const newCode = randomCode(8);
    await env.DB.batch([
      env.DB.prepare("UPDATE user_codes SET is_active = 0, expired_at = ? WHERE user_id = ? AND is_active = 1").bind(now, me.id),
      env.DB.prepare("INSERT INTO user_codes (id, user_id, code, is_active, created_at) VALUES (?,?,?,1,?)")
        .bind(crypto.randomUUID(), me.id, newCode, now),
    ]);
    return json({ code: newCode });
  }

  if (path === "/v1/search" && method === "GET") {
    const q = (url.searchParams.get("q") || "").trim();
    if (q.length < 2) return json({ users: [], groups: [] });
    const up = q.toUpperCase();
    const byCode = await env.DB.prepare(
      `SELECT u.* FROM user_codes c JOIN users u ON u.id = c.user_id
       WHERE c.code = ? AND c.is_active = 1 AND u.status = 'active'
       AND u.id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)`
    ).bind(up, me.id).all();
    const group = await env.DB.prepare(
      "SELECT id, name, description, avatar_key FROM conversations WHERE type = 'group' AND invite_code = ?"
    ).bind(up).all();
    const like = q.replace(/[%_]/g, "") + "%";
    const byName = await env.DB.prepare(
      `SELECT * FROM users WHERE display_name LIKE ? AND status = 'active' AND id <> ?
       AND id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?) LIMIT 20`
    ).bind(like, me.id, me.id).all();
    const seen = new Set();
    const rq = await env.DB.prepare(
      `SELECT u.*, l.id AS link_id, l.subject AS link_subject FROM contact_requests r
       JOIN contact_links l ON l.id = r.link_id JOIN users u ON u.id = l.user_id
       WHERE r.request_code = ? AND r.status <> 'expired' AND r.expires_at > ? AND l.is_active = 1 AND u.status = 'active'
       AND u.id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)`
    ).bind(up, Date.now(), me.id).first();
    const users = [];
    if (rq) {
      seen.add(rq.id);
      users.push({ ...publicUser(rq), link_id: rq.link_id, link_subject: rq.link_subject, request_code: up });
    }
    for (const u of [...byCode.results, ...byName.results]) if (!seen.has(u.id) && seen.add(u.id)) users.push(publicUser(u));
    return json({ users, groups: group.results });
  }

  if (path === "/v1/blocks" && method === "GET") {
    const r = await env.DB.prepare(
      "SELECT u.* FROM blocks b JOIN users u ON u.id = b.blocked_id WHERE b.blocker_id = ? ORDER BY b.created_at DESC"
    ).bind(me.id).all();
    return json({ users: r.results.map(publicUser) });
  }
  const blockMatch = path.match(/^\/v1\/blocks\/([\w-]+)$/);
  if (blockMatch && method === "PUT") {
    if (blockMatch[1] === me.id) return fail(400, "nao podes bloquear-te");
    await env.DB.prepare("INSERT OR IGNORE INTO blocks (blocker_id, blocked_id, created_at) VALUES (?,?,?)")
      .bind(me.id, blockMatch[1], Date.now()).run();
    return json({ ok: true });
  }
  if (blockMatch && method === "DELETE") {
    await env.DB.prepare("DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?").bind(me.id, blockMatch[1]).run();
    return json({ ok: true });
  }

  if (path === "/v1/reports" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const types = ["user", "message", "group", "story"];
    const reasons = ["spam", "scam", "harassment", "inappropriate", "illegal", "impersonation", "other"];
    if (!types.includes(b.target_type) || !b.target_id || !reasons.includes(b.reason)) return fail(400, "dados invalidos");
    const now = Date.now();
    const since = now - 86400000;
    const c = await env.DB.prepare("SELECT COUNT(*) AS n FROM reports WHERE reporter_id = ? AND created_at > ?").bind(me.id, since).first();
    if (c.n >= 20) return fail(429, "limite de denuncias atingido");

    const targetId = String(b.target_id).slice(0, 100);
    if (b.target_type === "user") {
      if (targetId === me.id) return fail(400, "nao podes denunciar a tua propria conta");
      const target = await env.DB.prepare("SELECT id, status, status_until FROM users WHERE id = ?").bind(targetId).first();
      if (!target || target.status === "deleted") return fail(404, "conta nao encontrada");
      const duplicate = await env.DB.prepare(
        "SELECT id FROM reports WHERE reporter_id = ? AND target_type = 'user' AND target_id = ? AND created_at > ? LIMIT 1"
      ).bind(me.id, targetId, now - 7 * 86400000).first();
      if (duplicate) return fail(409, "ja denunciaste esta conta nos ultimos 7 dias");
    }

    await env.DB.prepare(
      "INSERT INTO reports (id, reporter_id, target_type, target_id, reason, details, created_at) VALUES (?,?,?,?,?,?,?)"
    ).bind(crypto.randomUUID(), me.id, b.target_type, targetId, b.reason,
      typeof b.details === "string" ? b.details.slice(0, 500) : null, now).run();

    let moderation = null;
    if (b.target_type === "user") {
      const dayCount = await env.DB.prepare(
        "SELECT COUNT(DISTINCT reporter_id) AS n FROM reports WHERE target_type = 'user' AND target_id = ? AND created_at > ?"
      ).bind(targetId, now - 86400000).first();
      const weekCount = await env.DB.prepare(
        "SELECT COUNT(DISTINCT reporter_id) AS n FROM reports WHERE target_type = 'user' AND target_id = ? AND created_at > ?"
      ).bind(targetId, now - 7 * 86400000).first();
      const weekly = weekCount?.n || 0;
      const daily = dayCount?.n || 0;
      const target = await env.DB.prepare("SELECT status, status_until FROM users WHERE id = ?").bind(targetId).first();
      if (weekly > 100 && !(target.status === "banned" && target.status_until > now)) {
        const expires = now + 30 * 86400000;
        await env.DB.batch([
          env.DB.prepare("UPDATE users SET status = 'banned', status_until = ? WHERE id = ?").bind(expires, targetId),
          env.DB.prepare("INSERT INTO automated_moderation_events (id, target_user_id, action, distinct_reports, window_started_at, window_ended_at, created_at, expires_at) VALUES (?,?,?,?,?,?,?,?)")
            .bind(crypto.randomUUID(), targetId, "ban_30d", weekly, now - 7 * 86400000, now, now, expires),
        ]);
        moderation = { action: "banned", duration_hours: 720, distinct_reports: weekly };
      } else if (daily > 15 && !(target.status === "suspended" && target.status_until > now) && !(target.status === "banned" && target.status_until > now)) {
        const expires = now + 86400000;
        await env.DB.batch([
          env.DB.prepare("UPDATE users SET status = 'suspended', status_until = ? WHERE id = ?").bind(expires, targetId),
          env.DB.prepare("INSERT INTO automated_moderation_events (id, target_user_id, action, distinct_reports, window_started_at, window_ended_at, created_at, expires_at) VALUES (?,?,?,?,?,?,?,?)")
            .bind(crypto.randomUUID(), targetId, "suspend_24h", daily, now - 86400000, now, now, expires),
        ]);
        moderation = { action: "suspended", duration_hours: 24, distinct_reports: daily };
      }
    }
    return json({ ok: true, moderation });
  }

  if (path === "/v1/me/delete" && method === "POST") {
    const now = Date.now();
    await env.DB.prepare(
      "INSERT INTO data_requests (id, user_id, type, source, status, requested_at, scheduled_for) VALUES (?,?,?,?,?,?,?)"
    ).bind(crypto.randomUUID(), me.id, "delete", "app", "pending", now, null).run();
    return json({ ok: true, scheduled_for: now + 14 * 86400000 });
  }

  const billingResponse = await handleBilling(request, env, me, path, method);
  if (billingResponse) return billingResponse;

  const botResponse = await handleBots(request, env, me, path, method);
  if (botResponse) return botResponse;

  const extraResponse = await handleExtra(request, env, me, path, method);
  if (extraResponse) return extraResponse;

  const chatResponse = await handleChat(request, env, me, path, method);
  if (chatResponse) return chatResponse;

  return fail(404, "nao encontrado");
}

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch (err) {
      console.error(JSON.stringify({ msg: "erro", error: String(err) }));
      return fail(500, "erro interno");
    }
  },
  async scheduled(controller, env) {
    const now = Date.now();
    try { await refreshExpiringSubscriptions(env, now); }
    catch (error) { console.error("PiMsg scheduled subscription refresh failed:", String(error)); }
    const due = await env.DB.prepare(
      "SELECT id, user_id, title FROM bot_reminders WHERE status = 'active' AND notified_at IS NULL AND due_at <= ? ORDER BY due_at ASC LIMIT 100"
    ).bind(now).all();
    for (const reminder of due.results || []) {
      const sent = await pushToUsers(env, [reminder.user_id], "Lembrete PiMsg", reminder.title, {
        type: "bot_reminder", reminder_id: reminder.id
      });
      if (sent) {
        await env.DB.prepare("UPDATE bot_reminders SET notified_at = ? WHERE id = ? AND notified_at IS NULL")
          .bind(now, reminder.id).run();
      }
    }
    // Remove abandoned uploads and files no longer referenced by messages, active stories or profiles.
    const orphaned = await env.DB.prepare(
      `SELECT m.id, m.owner_id, m.r2_key, m.size_bytes FROM media m
       WHERE m.created_at < ?
         AND NOT EXISTS (SELECT 1 FROM messages msg WHERE msg.media_id = m.id AND msg.deleted_at IS NULL)
         AND NOT EXISTS (SELECT 1 FROM stories s WHERE s.media_id = m.id AND s.expires_at > ?)
         AND NOT EXISTS (SELECT 1 FROM users u WHERE u.avatar_key = m.id OR u.intro_video_key = m.id OR u.profile_cover_key = m.id)
       ORDER BY m.created_at ASC LIMIT 100`
    ).bind(now - 24 * 60 * 60 * 1000, now).all();
    for (const media of orphaned.results || []) {
      try {
        await env.MEDIA.delete(media.r2_key);
        await env.DB.batch([
          env.DB.prepare("DELETE FROM media WHERE id = ?").bind(media.id),
          env.DB.prepare("UPDATE user_storage_usage SET used_bytes = CASE WHEN used_bytes >= ? THEN used_bytes - ? ELSE 0 END, updated_at = ? WHERE user_id = ?")
            .bind(media.size_bytes, media.size_bytes, now, media.owner_id),
        ]);
      } catch (error) {
        console.error("PiMsg orphan media cleanup failed:", String(error));
      }
    }
  },
};
