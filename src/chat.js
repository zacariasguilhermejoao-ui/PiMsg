
import { pushToUsers } from "./push.js";

const FREE_CONTACT_LIMIT = 30;
const FREE_DAILY_MESSAGES = 30;
const MAX_MEDIA_BYTES = 50 * 1024 * 1024;
const KINDS = ["text", "image", "video", "audio", "music", "file"];
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status, error, extra = {}) => json({ error, ...extra }, status);

const code = (n) =>
  Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => ALPHABET[b % ALPHABET.length]).join("");

function localDay(tz) {
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone: tz }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

async function isPro(env, userId, now) {
  const r = await env.DB.prepare(
    "SELECT 1 AS ok FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? LIMIT 1"
  ).bind(userId, now).first();
  return !!r;
}


function localMonthWindow(now) {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: "Africa/Luanda", year: "numeric", month: "2-digit" }).formatToParts(new Date(now));
  const year = Number(parts.find((p) => p.type === "year")?.value);
  const month = Number(parts.find((p) => p.type === "month")?.value);
  // Angola is UTC+1 year-round: local midnight on day 1 is 23:00 UTC on the previous day.
  return { key: `${year}-${String(month).padStart(2, "0")}`, start: Date.UTC(year, month - 1, 1) - 3600000, end: Date.UTC(year, month, 1) - 3600000 };
}

async function linkReplyUsage(env, userId, now) {
  const month = localMonthWindow(now);
  const row = await env.DB.prepare(
    `SELECT COUNT(*) AS n FROM messages m
     JOIN conversations c ON c.id = m.conversation_id
     JOIN contact_links l ON l.id = c.link_id
     WHERE l.user_id = ? AND m.sender_id = ? AND m.kind <> 'system'
       AND m.created_at >= ? AND m.created_at < ?`
  ).bind(userId, userId, month.start, month.end).first();
  return { used: row?.n || 0, resets_at: month.end, month: month.key };
}

async function membership(env, convId, userId) {
  return env.DB.prepare(
    "SELECT * FROM conversation_members WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL"
  ).bind(convId, userId).first();
}

async function isBlockedBetween(env, a, b) {
  const r = await env.DB.prepare(
    "SELECT 1 AS ok FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
  ).bind(a, b, b, a).first();
  return !!r;
}

async function planSend(env, me, now) {
  if (await isPro(env, me.id, now)) return { ok: true, stmts: [] };
  const day = localDay(me.timezone);
  const credits = await env.DB.prepare("SELECT * FROM user_credits WHERE user_id = ?").bind(me.id).first();
  const usage = await env.DB.prepare("SELECT messages_sent FROM daily_usage WHERE user_id = ? AND day = ?")
    .bind(me.id, day).first();
  const sent = usage?.messages_sent || 0;
  const bump = env.DB.prepare(
    "INSERT INTO daily_usage (user_id, day, messages_sent) VALUES (?,?,1) ON CONFLICT(user_id, day) DO UPDATE SET messages_sent = messages_sent + 1"
  ).bind(me.id, day);
  if (credits && credits.unlimited_until > now) return { ok: true, stmts: [bump] };
  if (sent < FREE_DAILY_MESSAGES) return { ok: true, stmts: [bump] };
  if (credits && credits.extra_messages > 0) {
    return {
      ok: true,
      stmts: [
        bump,
        env.DB.prepare("UPDATE user_credits SET extra_messages = extra_messages - 1, updated_at = ? WHERE user_id = ? AND extra_messages > 0")
          .bind(now, me.id),
      ],
    };
  }
  return { ok: false };
}

export async function handleChat(request, env, me, path, method) {
  const url = new URL(request.url);
  const now = Date.now();

  if (path === "/v1/chats" && method === "GET") {
    const tab = url.searchParams.get("tab") || "all";
    const rows = await env.DB.prepare(
      `SELECT c.id, c.type, c.name, c.avatar_key, c.invite_code, c.last_message_at, cm.is_favorite, cm.is_muted,
        u.id AS other_id, u.display_name AS other_name, u.avatar_key AS other_avatar, u.ring_color AS other_ring,
        CASE WHEN u.show_online_status = 1 AND u.last_seen_at > ?3 THEN 1 ELSE 0 END AS other_online,
        (SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.created_at > cm.last_read_at
           AND m.sender_id <> ?1 AND m.deleted_at IS NULL) AS unread,
        (SELECT kind FROM messages m WHERE m.conversation_id = c.id AND m.deleted_at IS NULL ORDER BY m.created_at DESC LIMIT 1) AS last_kind,
        (SELECT body FROM messages m WHERE m.conversation_id = c.id AND m.deleted_at IS NULL ORDER BY m.created_at DESC LIMIT 1) AS last_body,
        EXISTS (SELECT 1 FROM typing_states ts WHERE ts.conversation_id = c.id AND ts.user_id <> ?1 AND ts.updated_at > ?2) AS other_typing
       FROM conversation_members cm
       JOIN conversations c ON c.id = cm.conversation_id
       LEFT JOIN users u ON c.type = 'direct' AND u.id = (CASE WHEN c.user_a_id = ?1 THEN c.user_b_id ELSE c.user_a_id END)
       WHERE cm.user_id = ?1 AND cm.left_at IS NULL
       ORDER BY other_typing DESC, COALESCE(c.last_message_at, c.created_at) DESC LIMIT 100`
    ).bind(me.id, now - 6000, now - 90000).all();
    let list = rows.results;
    if (tab === "new") list = list.filter((r) => r.unread > 0);
    else if (tab === "favorites") list = list.filter((r) => r.is_favorite);
    else if (tab === "groups") list = list.filter((r) => r.type === "group");
    return json({ chats: list });
  }

  if (path === "/v1/chats/direct" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const otherId = String(b.user_id || "");
    if (!otherId || otherId === me.id) return fail(400, "utilizador invalido");
    const other = await env.DB.prepare("SELECT id, status FROM users WHERE id = ?").bind(otherId).first();
    if (!other || other.status !== "active") return fail(404, "utilizador nao encontrado");
    if (await isBlockedBetween(env, me.id, otherId)) return fail(403, "nao e possivel conversar com este utilizador");

    let contactRequest = null;
    if (b.request_code) {
      contactRequest = await env.DB.prepare(
        `SELECT cr.id, cr.link_id, l.subject
         FROM contact_requests cr
         JOIN contact_links l ON l.id = cr.link_id
         WHERE cr.request_code = ? AND cr.status = 'pending' AND cr.expires_at > ?
           AND cr.visitor_user_id = ? AND l.user_id = ? AND l.is_active = 1`
      ).bind(String(b.request_code).toUpperCase(), now, me.id, otherId).first();
      if (!contactRequest) return fail(404, "codigo expirado ou indisponivel");
    }

    const [a, bId] = me.id < otherId ? [me.id, otherId] : [otherId, me.id];
    const existing = await env.DB.prepare(
      "SELECT id FROM conversations WHERE type = 'direct' AND user_a_id = ? AND user_b_id = ?"
    ).bind(a, bId).first();
    if (existing) {
      await env.DB.batch([
        env.DB.prepare("UPDATE conversation_members SET left_at = NULL WHERE conversation_id = ? AND user_id = ?")
          .bind(existing.id, me.id),
        ...(contactRequest ? [
          env.DB.prepare("UPDATE conversations SET link_id = COALESCE(link_id, ?) WHERE id = ?")
            .bind(contactRequest.link_id, existing.id),
          env.DB.prepare("UPDATE contact_requests SET status = 'used' WHERE id = ? AND status = 'pending'")
            .bind(contactRequest.id),
        ] : []),
      ]);
      return json({ id: existing.id });
    }

    if (!(await isPro(env, me.id, now))) {
      const count = await env.DB.prepare(
        `SELECT COUNT(*) AS n FROM conversation_members cm JOIN conversations c ON c.id = cm.conversation_id
         WHERE cm.user_id = ? AND cm.is_initiator = 1 AND c.type = 'direct'`
      ).bind(me.id).first();
      if (count.n >= FREE_CONTACT_LIMIT) {
        return fail(402, "limite de conversas do plano Free atingido", { reason: "limit_contacts", limit: FREE_CONTACT_LIMIT });
      }
    }

    const linkId = contactRequest?.link_id || null;
    const linkSubject = contactRequest?.subject || null;
    const id = crypto.randomUUID();
    await env.DB.batch([
      env.DB.prepare(
        "INSERT INTO conversations (id, type, created_by, created_at, user_a_id, user_b_id, link_id) VALUES (?, 'direct', ?, ?, ?, ?, ?)"
      ).bind(id, me.id, now, a, bId, linkId),
      env.DB.prepare(
        "INSERT INTO conversation_members (conversation_id, user_id, is_initiator, joined_at) VALUES (?,?,1,?)"
      ).bind(id, me.id, now),
      env.DB.prepare(
        "INSERT INTO conversation_members (conversation_id, user_id, is_initiator, joined_at) VALUES (?,?,0,?)"
      ).bind(id, otherId, now),
      ...(linkSubject ? [
        env.DB.prepare("INSERT INTO messages (id, conversation_id, sender_id, kind, body, created_at) VALUES (?,?,?,'system',?,?)")
          .bind(crypto.randomUUID(), id, me.id, "Interessado em: " + linkSubject, now),
        env.DB.prepare("UPDATE conversations SET last_message_at = ? WHERE id = ?").bind(now, id),
      ] : []),
      ...(contactRequest ? [
        env.DB.prepare("UPDATE contact_requests SET status = 'used' WHERE id = ? AND status = 'pending'")
          .bind(contactRequest.id),
      ] : []),
    ]);
    return json({ id }, 201);
  }

  if (path === "/v1/groups" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const name = typeof b.name === "string" ? b.name.trim().slice(0, 60) : "";
    if (!name) return fail(400, "nome do grupo obrigatorio");
    const id = crypto.randomUUID();
    const invite = code(6);
    await env.DB.batch([
      env.DB.prepare(
        "INSERT INTO conversations (id, type, created_by, created_at, name, description, invite_code) VALUES (?, 'group', ?, ?, ?, ?, ?)"
      ).bind(id, me.id, now, name, typeof b.description === "string" ? b.description.slice(0, 300) : null, invite),
      env.DB.prepare(
        "INSERT INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?, ?, 'owner', ?)"
      ).bind(id, me.id, now),
    ]);
    return json({ id, invite_code: invite }, 201);
  }

  const preview = path.match(/^\/v1\/groups\/preview\/([A-Za-z0-9]+)$/);
  if (preview && method === "GET") {
    const g = await env.DB.prepare(
      `SELECT c.id, c.name, c.description, c.avatar_key,
        (SELECT COUNT(*) FROM conversation_members WHERE conversation_id = c.id AND left_at IS NULL) AS members
       FROM conversations c WHERE c.type = 'group' AND c.invite_code = ?`
    ).bind(preview[1].toUpperCase()).first();
    return g ? json(g) : fail(404, "grupo nao encontrado");
  }

  if (path === "/v1/groups/join" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const g = await env.DB.prepare("SELECT id FROM conversations WHERE type = 'group' AND invite_code = ?")
      .bind(String(b.code || "").toUpperCase()).first();
    if (!g) return fail(404, "grupo nao encontrado");
    const sees = b.see_history === false ? 0 : 1;
    await env.DB.prepare(
      `INSERT INTO conversation_members (conversation_id, user_id, sees_history, joined_at)
       VALUES (?,?,?,?)
       ON CONFLICT(conversation_id, user_id) DO UPDATE SET left_at = NULL, sees_history = excluded.sees_history, joined_at = excluded.joined_at`
    ).bind(g.id, me.id, sees, now).run();
    return json({ id: g.id });
  }

  const sub = path.match(/^\/v1\/chats\/([\w-]+)\/(messages|read|favorite|leave|disappearing|typing)$/);
  if (sub) {
    const convId = sub[1];
    const action = sub[2];
    const mem = await membership(env, convId, me.id);
    if (!mem) return fail(404, "conversa nao encontrada");

    if (action === "typing" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      if (b.typing === true) {
        await env.DB.prepare("INSERT INTO typing_states (conversation_id, user_id, updated_at) VALUES (?,?,?) ON CONFLICT(conversation_id,user_id) DO UPDATE SET updated_at = excluded.updated_at")
          .bind(convId, me.id, now).run();
      } else {
        await env.DB.prepare("DELETE FROM typing_states WHERE conversation_id = ? AND user_id = ?").bind(convId, me.id).run();
      }
      return json({ ok: true });
    }
    if (action === "typing" && method === "GET") {
      const rows = await env.DB.prepare(
        "SELECT u.id, u.display_name FROM typing_states t JOIN users u ON u.id = t.user_id WHERE t.conversation_id = ? AND t.user_id <> ? AND t.updated_at > ? ORDER BY t.updated_at DESC LIMIT 10"
      ).bind(convId, me.id, now - 6000).all();
      return json({ typing: rows.results || [] });
    }

    if (action === "disappearing" && method === "GET") {
      const setting = await env.DB.prepare("SELECT disappearing_seconds FROM conversations WHERE id = ?")
        .bind(convId).first();
      return json({ seconds: setting?.disappearing_seconds || 0 });
    }

    if (action === "disappearing" && method === "PUT") {
      const b = await request.json().catch(() => ({}));
      const seconds = Number(b.seconds);
      if (![0, 86400, 604800, 7776000].includes(seconds)) return fail(400, "duracao invalida");
      await env.DB.prepare("UPDATE conversations SET disappearing_seconds = ? WHERE id = ?")
        .bind(seconds, convId).run();
      return json({ ok: true, seconds });
    }

    if (action === "messages" && method === "GET") {
      const before = Number(url.searchParams.get("before")) || now + 1;
      const limit = Math.min(Number(url.searchParams.get("limit")) || 40, 100);
      const floor = mem.sees_history ? 0 : mem.joined_at;
      await env.DB.prepare(
        "DELETE FROM messages WHERE conversation_id = ? AND expires_at IS NOT NULL AND expires_at <= ?"
      ).bind(convId, now).run();
      const r = await env.DB.prepare(
        `SELECT id, sender_id, kind, body, media_id, reply_to_id, created_at, edited_at, expires_at
         FROM messages WHERE conversation_id = ? AND deleted_at IS NULL
           AND (expires_at IS NULL OR expires_at > ?) AND created_at < ? AND created_at >= ?
         ORDER BY created_at DESC LIMIT ?`
      ).bind(convId, now, before, floor, limit).all();
      return json({ messages: r.results });
    }

    if (action === "messages" && method === "POST") {
      const b = await request.json().catch(() => ({}));
      const kind = KINDS.includes(b.kind) ? b.kind : "text";
      const body = typeof b.body === "string" ? b.body.slice(0, 4000) : null;
      if (kind === "text" && !body?.trim()) return fail(400, "mensagem vazia");
      if (kind !== "text") {
        const m = await env.DB.prepare("SELECT id FROM media WHERE id = ? AND owner_id = ?").bind(b.media_id || "", me.id).first();
        if (!m) return fail(400, "ficheiro invalido");
      }
      const conv = await env.DB.prepare("SELECT type, user_a_id, user_b_id, disappearing_seconds, link_id FROM conversations WHERE id = ?").bind(convId).first();
      if (conv.type === "direct") {
        const otherId = conv.user_a_id === me.id ? conv.user_b_id : conv.user_a_id;
        if (await isBlockedBetween(env, me.id, otherId)) return fail(403, "mensagem nao enviada");
      }
      if (conv.link_id && conv.type === "direct" && !(await isPro(env, me.id, now))) {
        const ownedLink = await env.DB.prepare("SELECT id FROM contact_links WHERE id = ? AND user_id = ?").bind(conv.link_id, me.id).first();
        if (ownedLink) {
          const linkUsage = await linkReplyUsage(env, me.id, now);
          if (linkUsage.used >= 500) {
            return fail(402, "Atingiu o limite mensal de 500 respostas através dos links personalizados. O limite renova no dia 1, às 00h00 (hora de Angola).", {
              reason: "limit_link_replies", limit: 500, used: linkUsage.used, resets_at: linkUsage.resets_at
            });
          }
        }
      }
      const plan = await planSend(env, me, now);
      if (!plan.ok) {
        return fail(402, "limite diario de mensagens atingido", { reason: "limit_messages", limit: FREE_DAILY_MESSAGES });
      }
      const id = crypto.randomUUID();
      await env.DB.batch([
        env.DB.prepare(
          "INSERT INTO messages (id, conversation_id, sender_id, kind, body, media_id, reply_to_id, created_at, expires_at) VALUES (?,?,?,?,?,?,?,?,?)"
        ).bind(id, convId, me.id, kind, body, kind === "text" ? null : b.media_id, b.reply_to_id || null, now,
          conv.disappearing_seconds > 0 ? now + conv.disappearing_seconds * 1000 : null),
        env.DB.prepare("UPDATE conversations SET last_message_at = ? WHERE id = ?").bind(now, convId),
        env.DB.prepare("UPDATE conversation_members SET last_read_at = ? WHERE conversation_id = ? AND user_id = ?").bind(now, convId, me.id),
        ...plan.stmts,
      ]);
      await notifyMessage(env, me, convId, kind, body);
      return json({ id, created_at: now }, 201);
    }

    if (action === "read" && method === "POST") {
      await env.DB.prepare("UPDATE conversation_members SET last_read_at = ? WHERE conversation_id = ? AND user_id = ?")
        .bind(now, convId, me.id).run();
      return json({ ok: true });
    }

    if (action === "favorite" && method === "PUT") {
      const b = await request.json().catch(() => ({}));
      await env.DB.prepare("UPDATE conversation_members SET is_favorite = ? WHERE conversation_id = ? AND user_id = ?")
        .bind(b.value ? 1 : 0, convId, me.id).run();
      return json({ ok: true });
    }

    if (action === "leave" && method === "POST") {
      await env.DB.prepare("UPDATE conversation_members SET left_at = ? WHERE conversation_id = ? AND user_id = ?")
        .bind(now, convId, me.id).run();
      return json({ ok: true });
    }
  }

  if (path === "/v1/media" && method === "PUT") {
    const size = Number(request.headers.get("content-length"));
    if (!size || size > MAX_MEDIA_BYTES) return fail(413, "ficheiro invalido ou demasiado grande");
    const mime = request.headers.get("content-type") || "application/octet-stream";
    const id = crypto.randomUUID();
    const key = `${me.id}/${id}`;
    await env.MEDIA.put(key, request.body, { httpMetadata: { contentType: mime } });
    await env.DB.prepare("INSERT INTO media (id, owner_id, r2_key, mime, size_bytes, created_at) VALUES (?,?,?,?,?,?)")
      .bind(id, me.id, key, mime, size, now).run();
    return json({ id }, 201);
  }

  const mediaGet = path.match(/^\/v1\/media\/([\w-]+)$/);
  if (mediaGet && method === "GET") {
    const m = await env.DB.prepare("SELECT * FROM media WHERE id = ?").bind(mediaGet[1]).first();
    if (!m) return fail(404, "ficheiro nao encontrado");
    let allowed = m.owner_id === me.id;
    if (!allowed) {
      const r = await env.DB.prepare(
        `SELECT 1 AS ok FROM messages m JOIN conversation_members cm ON cm.conversation_id = m.conversation_id
         WHERE m.media_id = ? AND cm.user_id = ? AND cm.left_at IS NULL LIMIT 1`
      ).bind(m.id, me.id).first();
      allowed = !!r;
    }
    if (!allowed) {
      const s = await env.DB.prepare(
        `SELECT 1 AS ok FROM stories s WHERE s.media_id = ?1 AND s.expires_at > ?2 AND s.user_id IN (
           SELECT CASE WHEN c.user_a_id = ?3 THEN c.user_b_id ELSE c.user_a_id END
           FROM conversations c JOIN conversation_members cm ON cm.conversation_id = c.id
           WHERE c.type = 'direct' AND cm.user_id = ?3 AND cm.left_at IS NULL) LIMIT 1`
      ).bind(m.id, now, me.id).first();
      const p = await env.DB.prepare("SELECT 1 AS ok FROM users WHERE avatar_key = ?1 OR intro_video_key = ?1 LIMIT 1").bind(m.id).first();
      allowed = !!(s || p);
    }
    if (!allowed) return fail(403, "sem acesso");
    const obj = await env.MEDIA.get(m.r2_key);
    if (!obj) return fail(404, "ficheiro nao encontrado");
    return new Response(obj.body, {
      headers: { "content-type": m.mime, "cache-control": "private, max-age=3600" },
    });
  }

  return null;
}

async function notifyMessage(env, me, convId, kind, body) {
  try {
    const r = await env.DB.prepare(
      `SELECT cm.user_id, c.type, c.name FROM conversation_members cm JOIN conversations c ON c.id = cm.conversation_id
       WHERE cm.conversation_id = ?1 AND cm.left_at IS NULL AND cm.user_id <> ?2 AND cm.is_muted = 0
       AND cm.user_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?2) LIMIT 100`
    ).bind(convId, me.id).all();
    if (!r.results.length) return;
    const labels = { image: "Foto", video: "Vídeo", audio: "Áudio", music: "Música", file: "Ficheiro" };
    const preview = kind === "text" ? (body || "").slice(0, 120) : labels[kind] || "Nova mensagem";
    const group = r.results[0].type === "group";
    await pushToUsers(
      env,
      r.results.map((x) => x.user_id),
      group ? r.results[0].name : me.display_name,
      group ? `${me.display_name}: ${preview}` : preview,
      { type: "message", conversation_id: convId }
    );
  } catch (err) {
    console.error(JSON.stringify({ msg: "notificacao falhou", error: String(err) }));
  }
}
