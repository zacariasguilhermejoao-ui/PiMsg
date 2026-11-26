
import { handleChat } from "./chat.js";
import { handleExtra } from "./extra.js";
import { handlePublic } from "./pages.js";

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
        "INSERT INTO users (id, firebase_uid, email, phone_e164, phone_verified, display_name, created_at, last_seen_at) VALUES (?,?,?,?,?,?,?,?)"
      ).bind(id, claims.sub, claims.email || null, phone, phone ? 1 : 0, name, now, now),
      env.DB.prepare(
        "INSERT INTO user_codes (id, user_id, code, is_active, created_at) VALUES (?,?,?,1,?)"
      ).bind(crypto.randomUUID(), id, randomCode(8), now),
    ]);
    user = await env.DB.prepare("SELECT * FROM users WHERE id = ?").bind(id).first();
  }
  if (user.status !== "active") return { blocked: user.status };
  return user;
}

const publicUser = (u) => ({
  id: u.id,
  name: u.display_name,
  bio: u.bio,
  avatar_key: u.avatar_key,
  ring_color: u.ring_color,
  phone: u.show_phone ? u.phone_e164 : null,
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
  if (me.blocked) return fail(me.blocked === "signup_rate_limited" ? 429 : 403, me.blocked === "signup_rate_limited" ? "limite de criacao de contas nesta rede; tente mais tarde" : "conta " + me.blocked);

  if (path === "/v1/me" && method === "GET") {
    const code = await activeCode(env, me.id);
    return json({
      id: me.id, name: me.display_name, bio: me.bio, avatar_key: me.avatar_key,
      instagram_url: me.instagram_url, facebook_url: me.facebook_url,
      tiktok_url: me.tiktok_url, youtube_url: me.youtube_url, website_url: me.website_url,
      ring_color: me.ring_color, phone_verified: !!me.phone_verified,
      show_phone: !!me.show_phone, timezone: me.timezone, code: code?.code,
    });
  }
  if (path === "/v1/me" && method === "PATCH") {
    const b = await request.json().catch(() => ({}));
    const name = typeof b.name === "string" ? b.name.trim().slice(0, 60) : me.display_name;
    const bio = typeof b.bio === "string" ? b.bio.slice(0, 300) : me.bio;
    const show = typeof b.show_phone === "boolean" ? (b.show_phone && me.phone_verified ? 1 : 0) : me.show_phone;
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
      "UPDATE users SET display_name=?, bio=?, show_phone=?, timezone=?, instagram_url=?, facebook_url=?, tiktok_url=?, youtube_url=?, website_url=?, ring_color=? WHERE id=?"
    ).bind(name, bio, show, tz, ...values, ringColor || "#8E8E93", me.id).run();
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
    const since = Date.now() - 86400000;
    const c = await env.DB.prepare("SELECT COUNT(*) AS n FROM reports WHERE reporter_id = ? AND created_at > ?").bind(me.id, since).first();
    if (c.n >= 20) return fail(429, "limite de denuncias atingido");
    await env.DB.prepare(
      "INSERT INTO reports (id, reporter_id, target_type, target_id, reason, details, created_at) VALUES (?,?,?,?,?,?,?)"
    ).bind(crypto.randomUUID(), me.id, b.target_type, String(b.target_id), b.reason,
      typeof b.details === "string" ? b.details.slice(0, 500) : null, Date.now()).run();
    return json({ ok: true });
  }

  if (path === "/v1/me/delete" && method === "POST") {
    const now = Date.now();
    await env.DB.prepare(
      "INSERT INTO data_requests (id, user_id, type, source, status, requested_at, scheduled_for) VALUES (?,?,?,?,?,?,?)"
    ).bind(crypto.randomUUID(), me.id, "delete", "app", "pending", now, now + 14 * 86400000).run();
    return json({ ok: true, scheduled_for: now + 14 * 86400000 });
  }

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
};
