
import { pushToUsers } from "./push.js";

const SLUG_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status, error) => json({ error }, status);
const slug = (n) =>
  Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => SLUG_ALPHABET[b % SLUG_ALPHABET.length]).join("");
const REQUEST_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const requestCode = (n) =>
  Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => REQUEST_ALPHABET[b % REQUEST_ALPHABET.length]).join("");

async function isPro(env, userId, now) {
  const r = await env.DB.prepare(
    "SELECT 1 AS ok FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? LIMIT 1"
  ).bind(userId, now).first();
  return !!r;
}


async function monthlyLinkReplies(env, userId, now) {
  const parts = new Intl.DateTimeFormat("en-US", { timeZone: "Africa/Luanda", year: "numeric", month: "2-digit" }).formatToParts(new Date(now));
  const year = Number(parts.find((p) => p.type === "year")?.value);
  const month = Number(parts.find((p) => p.type === "month")?.value);
  const start = Date.UTC(year, month - 1, 1) - 3600000;
  const end = Date.UTC(year, month, 1) - 3600000;
  const row = await env.DB.prepare(
    `SELECT COUNT(*) AS n FROM messages m JOIN conversations c ON c.id = m.conversation_id
     JOIN contact_links l ON l.id = c.link_id
     WHERE l.user_id = ? AND m.sender_id = ? AND m.kind <> 'system'
       AND m.created_at >= ? AND m.created_at < ?`
  ).bind(userId, userId, start, end).first();
  return row?.n || 0;
}

export async function handleExtra(request, env, me, path, method) {
  const now = Date.now();
  const url = new URL(request.url);

  if (path === "/v1/me/notifications" && method === "GET") {
    const prefs = await env.DB.prepare("SELECT push_enabled, popup_enabled FROM users WHERE id = ?").bind(me.id).first();
    return json({ push_enabled: !!prefs?.push_enabled, popup_enabled: !!prefs?.popup_enabled });
  }
  if (path === "/v1/me/notifications" && method === "PUT") {
    const b = await request.json().catch(() => ({}));
    const push = typeof b.push_enabled === "boolean" ? (b.push_enabled ? 1 : 0) : null;
    const popup = typeof b.popup_enabled === "boolean" ? (b.popup_enabled ? 1 : 0) : null;
    if (push === null && popup === null) return fail(400, "Preferências inválidas.");
    await env.DB.prepare("UPDATE users SET push_enabled = COALESCE(?, push_enabled), popup_enabled = COALESCE(?, popup_enabled) WHERE id = ?")
      .bind(push, popup, me.id).run();
    const prefs = await env.DB.prepare("SELECT push_enabled, popup_enabled FROM users WHERE id = ?").bind(me.id).first();
    return json({ push_enabled: !!prefs.push_enabled, popup_enabled: !!prefs.popup_enabled });
  }

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

  if (path === "/v1/me/social-links" && method === "GET") {
    const rows = await env.DB.prepare("SELECT id, label, url, created_at FROM user_social_links WHERE user_id = ? ORDER BY created_at DESC")
      .bind(me.id).all();
    return json({ links: rows.results || [] });
  }
  if (path === "/v1/me/social-links" && method === "POST") {
    const b = await request.json().catch(() => ({}));
    const raw = typeof b.url === "string" ? b.url.trim().slice(0, 500) : "";
    let parsed;
    try { parsed = new URL(raw); } catch { return fail(400, "Ligação inválida."); }
    if (parsed.protocol !== "https:" || !parsed.hostname || parsed.username || parsed.password) return fail(400, "Utilize uma ligação HTTPS válida.");
    const label = (typeof b.label === "string" && b.label.trim() ? b.label.trim().slice(0, 80) : parsed.hostname.replace(/^www\./i, "")).slice(0, 80);
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO user_social_links (id, user_id, label, url, created_at) VALUES (?,?,?,?,?)")
      .bind(id, me.id, label, parsed.toString(), now).run();
    return json({ id, label, url: parsed.toString() }, 201);
  }
  const socialLinkDelete = path.match(/^\/v1\/me\/social-links\/([\w-]+)$/);
  if (socialLinkDelete && method === "DELETE") {
    await env.DB.prepare("DELETE FROM user_social_links WHERE id = ? AND user_id = ?")
      .bind(socialLinkDelete[1], me.id).run();
    return json({ ok: true });
  }

  if (path === "/v1/favicon" && method === "GET") {
    const raw = url.searchParams.get("url") || "";
    let target;
    try { target = new URL(raw); } catch { return fail(400, "Ligação inválida."); }
    if (target.protocol !== "https:" || !target.hostname || target.username || target.password) return fail(400, "Ligação inválida.");
    const host = target.hostname.toLowerCase();
    if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(":")) return fail(400, "Domínio inválido.");
    const timeout = AbortSignal.timeout(5000);
    let iconUrl = new URL("/favicon.ico", target.origin);
    const page = await fetch(target.toString(), { redirect: "follow", headers: { accept: "text/html" }, signal: timeout }).catch(() => null);
    if (page && page.ok && (page.headers.get("content-type") || "").includes("text/html")) {
      const html = (await page.text()).slice(0, 200000);
      const tags = html.match(/<link\\b[^>]*>/gi) || [];
      for (const tag of tags) {
        const rel = tag.match(/\\brel\\s*=\\s*["']([^"']+)["']/i)?.[1] || "";
        const href = tag.match(/\\bhref\\s*=\\s*["']([^"']+)["']/i)?.[1];
        if (/icon/i.test(rel) && href) {
          try {
            const candidate = new URL(href, page.url || target.toString());
            if (candidate.protocol === "https:" && candidate.hostname && !candidate.hostname.includes(":")) { iconUrl = candidate; break; }
          } catch { }
        }
      }
    }
    const upstream = await fetch(iconUrl.toString(), { redirect: "follow", signal: timeout }).catch(() => null);
    if (!upstream || !upstream.ok || !(upstream.headers.get("content-type") || "").startsWith("image/")) return new Response(null, { status: 404 });
    const bytes = await upstream.arrayBuffer();
    if (bytes.byteLength > 131072) return new Response(null, { status: 413 });
    return new Response(bytes, { headers: { "content-type": upstream.headers.get("content-type"), "cache-control": "public, max-age=86400", "x-content-type-options": "nosniff" } });
  }

  const profile = path.match(/^\/v1\/users\/([\w-]+)$/);
  if (profile && method === "GET") {
    const u = await env.DB.prepare("SELECT * FROM users WHERE id = ? AND status = 'active'").bind(profile[1]).first();
    if (!u) return fail(404, "utilizador nao encontrado");
    const blocked = await env.DB.prepare("SELECT 1 AS ok FROM blocks WHERE blocker_id = ? AND blocked_id = ?").bind(u.id, me.id).first();
    if (blocked) return fail(404, "utilizador nao encontrado");
    const socialRows = await env.DB.prepare("SELECT id, label, url FROM user_social_links WHERE user_id = ? ORDER BY created_at DESC").bind(u.id).all();
    const verifiedPro = await isPro(env, u.id, now);
    return json({
      id: u.id, name: u.display_name, bio: u.bio, avatar_key: u.avatar_key,
      email_verified: !!u.email_verified, professional_profile: !!u.email_verified,
      verified_badge: verifiedPro, social_links: socialRows.results || [],
      instagram_url: u.instagram_url, facebook_url: u.facebook_url,
      tiktok_url: u.tiktok_url, youtube_url: u.youtube_url, website_url: u.website_url,
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

  const resolveRequest = path.match(/^\/v1\/links\/requests\/([A-Z0-9]{8})$/i);
  if (resolveRequest && method === "GET") {
    const requestCodeValue = resolveRequest[1].toUpperCase();
    const contact = await env.DB.prepare(
      `SELECT cr.id AS request_id, cr.link_id, l.user_id, l.subject, u.display_name, u.ring_color
       FROM contact_requests cr
       JOIN contact_links l ON l.id = cr.link_id
       JOIN users u ON u.id = l.user_id
       WHERE cr.request_code = ? AND cr.status = 'pending' AND cr.expires_at > ?
         AND l.is_active = 1 AND u.status = 'active'`
    ).bind(requestCodeValue, now).first();
    if (!contact) return fail(404, "codigo expirado ou indisponivel");
    if (contact.user_id === me.id) return fail(400, "este convite pertence a sua conta");
    const blocked = await env.DB.prepare(
      "SELECT 1 AS ok FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
    ).bind(me.id, contact.user_id, contact.user_id, me.id).first();
    if (blocked) return fail(403, "nao e possivel iniciar esta conversa");
    const claim = await env.DB.prepare(
      `UPDATE contact_requests SET visitor_user_id = ?
       WHERE id = ? AND status = 'pending' AND expires_at > ?
         AND (visitor_user_id IS NULL OR visitor_user_id = ?)`
    ).bind(me.id, contact.request_id, now, me.id).run();
    if (!claim.meta || claim.meta.changes !== 1) return fail(409, "codigo ja utilizado por outra conta");
    return json({
      user: { id: contact.user_id, name: contact.display_name, ring_color: contact.ring_color },
      link_id: contact.link_id,
      request_code: requestCodeValue,
      subject: contact.subject,
    });
  }

  const openLink = path.match(/^\/v1\/links\/([a-z0-9]{6,16})\/open$/);
  if (openLink && method === "POST") {
    const link = await env.DB.prepare(
      `SELECT l.id, l.user_id, l.subject, u.display_name
       FROM contact_links l JOIN users u ON u.id = l.user_id
       WHERE l.slug = ? AND l.is_active = 1 AND u.status = 'active'`
    ).bind(openLink[1]).first();
    if (!link) return fail(404, "link indisponivel");
    if (link.user_id === me.id) return fail(400, "este link pertence a sua conta");
    const blocked = await env.DB.prepare(
      "SELECT 1 AS ok FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
    ).bind(me.id, link.user_id, link.user_id, me.id).first();
    if (blocked) return fail(403, "nao e possivel iniciar esta conversa");
    const recent = await env.DB.prepare(
      "SELECT COUNT(*) AS n FROM contact_requests WHERE link_id = ? AND created_at > ?"
    ).bind(link.id, now - 3600_000).first();
    if (recent.n >= 200) return fail(429, "muitos pedidos neste momento");
    const rc = requestCode(8);
    await env.DB.prepare(
      "INSERT INTO contact_requests (id, link_id, visitor_name, request_code, visitor_user_id, status, created_at, expires_at) VALUES (?,?,?,?,?,'pending',?,?)"
    ).bind(crypto.randomUUID(), link.id, me.display_name, rc, me.id, now, now + 7 * 86400_000).run();
    await pushToUsers(env, [link.user_id], "Novo interessado", `${me.display_name} pretende conversar sobre ${link.subject}`, { type: "contact_request", link_id: link.id });
    return json({ user: { id: link.user_id, name: link.display_name }, link_id: link.id, request_code: rc, subject: link.subject });
  }

  if (path === "/v1/links" && method === "GET") {
    const r = await env.DB.prepare(
      "SELECT id, slug, subject, intro_text, image_media_id, opens_count, created_at FROM contact_links WHERE user_id = ? AND is_active = 1 ORDER BY created_at DESC"
    ).bind(me.id).all();
    return json({ links: r.results.map((l) => ({ ...l, url: `https://pimsg.tchilopop.com/u/${l.slug}` })) });
  }
  if (path === "/v1/links" && method === "POST") {
    if (!(await isPro(env, me.id, now))) {
      const used = await monthlyLinkReplies(env, me.id, now);
      if (used >= 500) return fail(402, "Atingiu as 500 respostas mensais dos links personalizados. Poderá criar novos links novamente no dia 1, às 00h00 (hora de Angola), ou ativar o plano Pro.", { reason: "limit_link_replies", limit: 500, used });
    }
    const b = await request.json().catch(() => ({}));
    const subject = typeof b.subject === "string" ? b.subject.trim().slice(0, 80) : "";
    if (!subject) return fail(400, "assunto obrigatorio");
    const c = await env.DB.prepare("SELECT COUNT(*) AS n FROM contact_links WHERE user_id = ? AND is_active = 1").bind(me.id).first();
    if (c.n >= 20) return fail(400, "limite de 20 links atingido");
    let imageMediaId = null;
    if (b.image_media_id) {
      const media = await env.DB.prepare("SELECT id, mime FROM media WHERE id = ? AND owner_id = ?").bind(String(b.image_media_id), me.id).first();
      if (!media || !String(media.mime).startsWith("image/")) return fail(400, "imagem do link invalida");
      imageMediaId = media.id;
    }
    const id = crypto.randomUUID();
    const s = slug(8);
    await env.DB.prepare(
      "INSERT INTO contact_links (id, user_id, slug, subject, intro_text, image_media_id, created_at) VALUES (?,?,?,?,?,?,?)"
    ).bind(id, me.id, s, subject, typeof b.intro_text === "string" ? b.intro_text.trim().slice(0, 400) : null, imageMediaId, now).run();
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
