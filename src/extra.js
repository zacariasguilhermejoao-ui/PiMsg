
const SLUG_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status, error) => json({ error }, status);
const slug = (n) =>
  Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => SLUG_ALPHABET[b % SLUG_ALPHABET.length]).join("");

async function isPro(env, userId, now) {
  const r = await env.DB.prepare(
    "SELECT 1 AS ok FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? LIMIT 1"
  ).bind(userId, now).first();
  return !!r;
}

export async function handleExtra(request, env, me, path, method) {
  const now = Date.now();

  if (path === "/v1/devices" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const hash = String(b.device_hash || "").slice(0, 100);
    const token = b.fcm_token ? String(b.fcm_token).slice(0, 400) : null;
    if (!hash) return fail(400, "dispositivo invalido");
    const stmts = [
      env.DB.prepare(
        `INSERT INTO devices (id, user_id, device_hash, platform, fcm_token, created_at, last_seen_at)
         VALUES (?,?,?,'android',?,?,?)
         ON CONFLICT(user_id, device_hash) DO UPDATE SET fcm_token = excluded.fcm_token, last_seen_at = excluded.last_seen_at`
      ).bind(crypto.randomUUID(), me.id, hash, token, now, now),
    ];
    if (token) stmts.push(env.DB.prepare("UPDATE devices SET fcm_token = NULL WHERE fcm_token = ? AND user_id <> ?").bind(token, me.id));
    await env.DB.batch(stmts);
    return json({ ok: true });
  }

  const profile = path.match(/^\/v1\/users\/([\w-]+)$/);
  if (profile && method === "GET") {
    const u = await env.DB.prepare("SELECT * FROM users WHERE id = ? AND status = 'active'").bind(profile[1]).first();
    if (!u) return fail(404, "utilizador nao encontrado");
    const blocked = await env.DB.prepare("SELECT 1 AS ok FROM blocks WHERE blocker_id = ? AND blocked_id = ?").bind(u.id, me.id).first();
    if (blocked) return fail(404, "utilizador nao encontrado");
    return json({
      id: u.id, name: u.display_name, bio: u.bio, avatar_key: u.avatar_key,
      intro_video_key: u.intro_video_key, ring_color: u.ring_color, phone: u.show_phone ? u.phone_e164 : null,
    });
  }

  if (path === "/v1/me/media" && method === "PATCH") {
    const b = await request.json().catch(() => ({}));
    const owns = async (id) => id && (await env.DB.prepare("SELECT 1 AS ok FROM media WHERE id = ? AND owner_id = ?").bind(id, me.id).first());
    if (b.avatar_media_id !== undefined) {
      if (b.avatar_media_id !== null && !(await owns(b.avatar_media_id))) return fail(400, "ficheiro invalido");
      await env.DB.prepare("UPDATE users SET avatar_key = ? WHERE id = ?").bind(b.avatar_media_id, me.id).run();
    }
    if (b.intro_video_media_id !== undefined) {
      if (b.intro_video_media_id !== null) {
        if (!(await isPro(env, me.id, now))) return json({ error: "video de apresentacao so no plano Pro", reason: "pro_required" }, 402);
        if (!(await owns(b.intro_video_media_id))) return fail(400, "ficheiro invalido");
      }
      await env.DB.prepare("UPDATE users SET intro_video_key = ? WHERE id = ?").bind(b.intro_video_media_id, me.id).run();
    }
    return json({ ok: true });
  }

  if (path === "/v1/links" && method === "GET") {
    const r = await env.DB.prepare(
      "SELECT id, slug, subject, intro_text, opens_count, created_at FROM contact_links WHERE user_id = ? AND is_active = 1 ORDER BY created_at DESC"
    ).bind(me.id).all();
    return json({ links: r.results.map((l) => ({ ...l, url: `https://pimsg.tchilopop.com/u/${l.slug}` })) });
  }
  if (path === "/v1/links" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const subject = typeof b.subject === "string" ? b.subject.trim().slice(0, 80) : "";
    if (!subject) return fail(400, "assunto obrigatorio");
    const c = await env.DB.prepare("SELECT COUNT(*) AS n FROM contact_links WHERE user_id = ? AND is_active = 1").bind(me.id).first();
    if (c.n >= 20) return fail(400, "limite de 20 links atingido");
    const id = crypto.randomUUID();
    const s = slug(8);
    await env.DB.prepare(
      "INSERT INTO contact_links (id, user_id, slug, subject, intro_text, created_at) VALUES (?,?,?,?,?,?)"
    ).bind(id, me.id, s, subject, typeof b.intro_text === "string" ? b.intro_text.trim().slice(0, 400) : null, now).run();
    return json({ id, slug: s, url: `https://pimsg.tchilopop.com/u/${s}` }, 201);
  }
  const linkDel = path.match(/^\/v1\/links\/([\w-]+)$/);
  if (linkDel && method === "DELETE") {
    await env.DB.prepare("UPDATE contact_links SET is_active = 0 WHERE id = ? AND user_id = ?").bind(linkDel[1], me.id).run();
    return json({ ok: true });
  }

  if (path === "/v1/stories" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const m = await env.DB.prepare("SELECT mime FROM media WHERE id = ? AND owner_id = ?").bind(b.media_id || "", me.id).first();
    if (!m || !(m.mime.startsWith("image/") || m.mime.startsWith("video/"))) return fail(400, "ficheiro invalido");
    const id = crypto.randomUUID();
    await env.DB.prepare(
      "INSERT INTO stories (id, user_id, media_id, caption, created_at, expires_at) VALUES (?,?,?,?,?,?)"
    ).bind(id, me.id, b.media_id, typeof b.caption === "string" ? b.caption.slice(0, 200) : null, now, now + 24 * 3600 * 1000).run();
    return json({ id }, 201);
  }

  if (path === "/v1/stories" && method === "GET") {
    const r = await env.DB.prepare(
      `SELECT s.id, s.user_id, u.display_name AS user_name, u.ring_color, s.media_id, s.caption, s.created_at,
         EXISTS (SELECT 1 FROM story_views v WHERE v.story_id = s.id AND v.viewer_id = ?1) AS viewed,
         (SELECT m.mime FROM media m WHERE m.id = s.media_id) AS mime
       FROM stories s JOIN users u ON u.id = s.user_id
       WHERE s.expires_at > ?2 AND u.status = 'active'
         AND (s.user_id = ?1 OR s.user_id IN (
           SELECT CASE WHEN c.user_a_id = ?1 THEN c.user_b_id ELSE c.user_a_id END
           FROM conversations c JOIN conversation_members cm ON cm.conversation_id = c.id
           WHERE c.type = 'direct' AND cm.user_id = ?1 AND cm.left_at IS NULL AND c.last_message_at IS NOT NULL))
         AND s.user_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?1)
         AND s.user_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?1)
       ORDER BY s.created_at ASC LIMIT 300`
    ).bind(me.id, now).all();
    return json({ stories: r.results.map((s) => ({ ...s, viewed: !!s.viewed })) });
  }
  const storyView = path.match(/^\/v1\/stories\/([\w-]+)\/view$/);
  if (storyView && method === "POST") {
    await env.DB.prepare("INSERT OR IGNORE INTO story_views (story_id, viewer_id, viewed_at) VALUES (?,?,?)").bind(storyView[1], me.id, now).run();
    return json({ ok: true });
  }
  const storyDel = path.match(/^\/v1\/stories\/([\w-]+)$/);
  if (storyDel && method === "DELETE") {
    await env.DB.prepare("DELETE FROM stories WHERE id = ? AND user_id = ?").bind(storyDel[1], me.id).run();
    return json({ ok: true });
  }

  return null;
}
