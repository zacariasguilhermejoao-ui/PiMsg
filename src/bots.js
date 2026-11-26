// PiMsg Pi AI — inference exclusively through Cloudflare Workers AI.
const MODEL = "@cf/meta/llama-3.2-3b-instruct";
const FREE_DAILY_LIMIT = 5;
const PRO_DAILY_LIMIT = 30;

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
const fail = (status, error, extra = {}) => json({ error, ...extra }, status);

function localDay(timeZone) {
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

async function isPro(env, userId, now) {
  const result = await env.DB.prepare(
    "SELECT plan FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? ORDER BY expires_at DESC LIMIT 1"
  ).bind(userId, now).first();
  return result || null;
}

export async function handleBots(request, env, me, path, method) {
  if (path === "/v1/bots" && method === "GET") {
    return json({
      bots: [{
        id: "pi-ai",
        name: "Pi AI",
        description: "Assistente para perguntas, escrita, ideias e explicações.",
        available: true,
      }],
    });
  }

  if (path !== "/v1/bots/pi-ai/chat") return null;
  if (method !== "POST") return fail(405, "metodo nao permitido");
  if (!env.AI) return fail(503, "O serviço de IA ainda não está configurado na Cloudflare.");

  const body = await request.json().catch(() => null);
  if (!body || typeof body !== "object") return fail(400, "pedido invalido");
  const prompt = typeof body.message === "string" ? body.message.trim() : "";
  if (!prompt) return fail(400, "Escreva uma mensagem.");
  if (prompt.length > 2000) return fail(413, "A mensagem não pode ultrapassar 2.000 caracteres.");

  const history = Array.isArray(body.history) ? body.history.slice(-10) : [];
  const normalizedHistory = [];
  for (const item of history) {
    if (!item || !["user", "assistant"].includes(item.role) || typeof item.content !== "string") continue;
    const content = item.content.trim().slice(0, 2500);
    if (content) normalizedHistory.push({ role: item.role, content });
  }

  const now = Date.now();
  const subscription = await isPro(env, me.id, now);
  const limit = subscription ? PRO_DAILY_LIMIT : FREE_DAILY_LIMIT;
  const day = localDay(me.timezone || "Africa/Luanda");
  const quota = await env.DB.prepare(
    `INSERT INTO bot_daily_usage (user_id, day, requests, updated_at) VALUES (?, ?, 1, ?)
     ON CONFLICT(user_id, day) DO UPDATE SET requests = requests + 1, updated_at = excluded.updated_at
     WHERE requests < ?
     RETURNING requests`
  ).bind(me.id, day, now, limit).first();

  if (!quota) {
    return fail(429, "Atingiu o limite diário do Pi AI.", {
      reason: "bot_daily_limit",
      limit,
      plan: subscription ? subscription.plan : "free",
    });
  }

  const messages = [
    {
      role: "system",
      content: "És o Pi AI, o assistente oficial do PiMsg. Responde em português europeu usado em Angola, com linguagem humana, clara, profissional e direta. Usa grafia como “contacto”, “utilizador”, “ficheiro”, “ecrã” e “telemóvel”; evita brasileirismos como “contato”. Evita frases feitas, entusiasmo artificial, emojis desnecessários e listas longas quando uma resposta curta basta. Ajuda com perguntas gerais, escrita, ideias, explicações e utilização do PiMsg. Não inventes funcionalidades, preços, políticas ou dados do PiMsg. Não afirmes que enviaste mensagens, alteraste contas, fizeste compras ou executaste ações fora desta conversa. Se não souberes algo, diz claramente. Não peças palavras-passe, códigos de autenticação ou dados bancários."
    },
    ...normalizedHistory,
    { role: "user", content: prompt },
  ];

  try {
    const result = await env.AI.run(MODEL, {
      messages,
      max_tokens: 512,
      temperature: 0.55,
      top_p: 0.9,
    });
    const reply = typeof result?.response === "string"
      ? result.response.trim()
      : typeof result?.choices?.[0]?.message?.content === "string"
        ? result.choices[0].message.content.trim()
        : "";
    if (!reply) throw new Error("empty model response");
    return json({
      reply,
      model: MODEL,
      plan: subscription ? subscription.plan : "free",
      limit,
      remaining: Math.max(0, limit - quota.requests),
    });
  } catch (error) {
    await env.DB.prepare(
      "UPDATE bot_daily_usage SET requests = CASE WHEN requests > 0 THEN requests - 1 ELSE 0 END, updated_at = ? WHERE user_id = ? AND day = ?"
    ).bind(Date.now(), me.id, day).run();
    console.error("Pi AI inference failed:", String(error));
    return fail(502, "O Pi AI não conseguiu responder neste momento. Tente novamente.");
  }
}
