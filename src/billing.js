const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
const fail = (status, error) => json({ error }, status);
const PACKAGE_NAME = "com.pimsg.isabstudio";
const PRODUCTS = {
  pimsg_pro_weekly: "pro_weekly",
  pimsg_pro_monthly: "pro_monthly",
};
let cachedGoogleToken = null;

function b64url(value) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToBytes(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const binary = atob(body);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

async function googleAccessToken(env) {
  if (cachedGoogleToken && cachedGoogleToken.expiresAt > Date.now() + 60000) return cachedGoogleToken.value;
  if (!env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) throw new Error("google_play_service_account_missing");
  const account = JSON.parse(env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON);
  if (!account.client_email || !account.private_key) throw new Error("google_play_service_account_invalid");
  const now = Math.floor(Date.now() / 1000);
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + b64url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: account.token_uri || "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey("pkcs8", pemToBytes(account.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const assertion = unsigned + "." + b64url(new Uint8Array(signature));
  const tokenResponse = await fetch(account.token_uri || "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  const tokenBody = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenBody.access_token) throw new Error("google_play_oauth_failed");
  cachedGoogleToken = { value: tokenBody.access_token, expiresAt: Date.now() + (tokenBody.expires_in || 3600) * 1000 };
  return cachedGoogleToken.value;
}

async function googleApi(env, path, options = {}) {
  const token = await googleAccessToken(env);
  const response = await fetch("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE_NAME + path, {
    ...options,
    headers: { authorization: "Bearer " + token, "content-type": "application/json", ...(options.headers || {}) },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error("google_play_api_" + response.status);
  return body;
}

export async function handleBilling(request, env, me, path, method) {
  if (path !== "/v1/billing/verify" || method !== "POST") return null;
  const body = await request.json().catch(() => ({}));
  const purchaseToken = typeof body.purchase_token === "string" ? body.purchase_token.trim() : "";
  const productId = typeof body.product_id === "string" ? body.product_id : "";
  const plan = PRODUCTS[productId];
  if (!purchaseToken || purchaseToken.length > 4096 || !plan) return fail(400, "produto ou token de compra inválido");
  try {
    const existing = await env.DB.prepare("SELECT user_id FROM subscriptions WHERE play_purchase_token = ?").bind(purchaseToken).first();
    if (existing && existing.user_id !== me.id) return fail(409, "esta compra já está associada a outra conta PiMsg");

    const purchase = await googleApi(env, "/purchases/subscriptionsv2/tokens/" + encodeURIComponent(purchaseToken));
    const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
    const line = lineItems.find((item) => item.productId === productId);
    if (!line) return fail(403, "a Google Play não confirmou este produto");
    const expiresAt = Date.parse(line.expiryTime || "");
    if (!Number.isFinite(expiresAt)) return fail(403, "a Google Play não devolveu a validade da subscrição");
    const now = Date.now();
    const state = purchase.subscriptionState || "";
    if (state === "SUBSCRIPTION_STATE_PENDING" || state === "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED") {
      return fail(409, "compra pendente; aguarde a confirmação da Google Play");
    }
    let status = "expired";
    if (state === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD") status = "grace";
    else if (state === "SUBSCRIPTION_STATE_ON_HOLD" || state === "SUBSCRIPTION_STATE_PAUSED") status = "on_hold";
    else if (expiresAt > now && ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_CANCELED"].includes(state)) status = "active";
    if (expiresAt <= now || state === "SUBSCRIPTION_STATE_EXPIRED") status = "expired";
    const autoRenewing = !!line.autoRenewingPlan?.autoRenewEnabled;
    const startedAt = Date.parse(purchase.startTime || "") || now;
    const subscriptionId = existing
      ? await env.DB.prepare("SELECT id FROM subscriptions WHERE play_purchase_token = ?").bind(purchaseToken).first()
      : null;
    const id = subscriptionId?.id || crypto.randomUUID();

    await env.DB.prepare(
      `INSERT INTO subscriptions (id, user_id, plan, status, play_purchase_token, auto_renewing, started_at, expires_at, updated_at)
       VALUES (?,?,?,?,?,?,?,?,?)
       ON CONFLICT(play_purchase_token) DO UPDATE SET
         plan = excluded.plan, status = excluded.status, auto_renewing = excluded.auto_renewing,
         started_at = excluded.started_at, expires_at = excluded.expires_at, updated_at = excluded.updated_at
       WHERE subscriptions.user_id = excluded.user_id`
    ).bind(id, me.id, plan, status, purchaseToken, autoRenewing ? 1 : 0, startedAt, expiresAt, now).run();

    if (purchase.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING" && status !== "expired") {
      await googleApi(env, "/purchases/subscriptions/" + encodeURIComponent(productId) + "/tokens/" + encodeURIComponent(purchaseToken) + ":acknowledge", {
        method: "POST",
        body: JSON.stringify({ developerPayload: "PiMsg " + me.id }),
      });
    }
    return json({ ok: true, plan, status, expires_at: expiresAt, auto_renewing: autoRenewing });
  } catch (error) {
    const reason = String(error?.message || "");
    if (reason === "google_play_service_account_missing") return fail(503, "validação de compras ainda não configurada no servidor");
    return fail(502, "não foi possível validar a compra com a Google Play");
  }
}
