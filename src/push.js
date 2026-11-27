
let cached = { token: null, exp: 0 };

const b64url = (buf) =>
  btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlStr = (s) => b64url(new TextEncoder().encode(s));

function pemToBytes(pem) {
  const b64 = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

async function accessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  if (cached.token && cached.exp > now + 60) return cached.token;
  const header = b64urlStr(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = b64urlStr(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToBytes(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${payload}`));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${header}.${payload}.${b64url(sig)}`,
  });
  const j = await res.json();
  if (!j.access_token) throw new Error("token fcm indisponivel");
  cached = { token: j.access_token, exp: now + (j.expires_in || 3600) };
  return cached.token;
}

export async function pushToUsers(env, userIds, title, body, data = {}) {
  if (!env.FCM_SERVICE_ACCOUNT || !userIds.length) return;
  try {
    const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT);
    const marks = userIds.map(() => "?").join(",");
    const rows = await env.DB.prepare(
      `SELECT d.id, d.fcm_token, u.popup_enabled FROM devices d JOIN users u ON u.id = d.user_id WHERE d.user_id IN (${marks}) AND d.fcm_token IS NOT NULL AND u.push_enabled = 1 AND u.status = 'active' LIMIT 100`
    ).bind(...userIds).all();
    if (!rows.results.length) return;
    const at = await accessToken(sa);
    const payloadData = { title: String(title), body: String(body) };
    for (const [k, v] of Object.entries(data)) payloadData[k] = String(v);
    const send = rows.results.map(async (d) => {
      const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
        method: "POST",
        headers: { authorization: `Bearer ${at}`, "content-type": "application/json" },
        body: JSON.stringify({ message: { token: d.fcm_token, data: { ...payloadData, popup_enabled: String(!!d.popup_enabled) }, android: { priority: "HIGH" } } }),
      });
      if (r.status === 404) await env.DB.prepare("UPDATE devices SET fcm_token = NULL WHERE id = ?").bind(d.id).run();
    });
    await Promise.race([Promise.allSettled(send), new Promise((res) => setTimeout(res, 2500))]);
  } catch (err) {
    console.error(JSON.stringify({ msg: "push falhou", error: String(err) }));
  }
}
