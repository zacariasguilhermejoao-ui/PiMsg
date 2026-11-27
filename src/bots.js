// PiMsg Pi AI — inference exclusively through Cloudflare Workers AI.
const MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
const PRO_DAILY_LIMIT = 30;
const FREE_STARTING_AI_CREDITS = 15;

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
    await env.DB.prepare("INSERT OR IGNORE INTO ai_credits (user_id, balance, updated_at) VALUES (?, ?, ?)")
      .bind(me.id, FREE_STARTING_AI_CREDITS, Date.now()).run();
    const creditRow = await env.DB.prepare("SELECT balance FROM ai_credits WHERE user_id = ?").bind(me.id).first();
    return json({
      ai_credits: creditRow?.balance ?? FREE_STARTING_AI_CREDITS,
      bots: [{
        id: "pi-ai",
        name: "Pi AI",
        description: "Assistente para perguntas, escrita, ideias e explicações.",
        available: !!env.AI,
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
  const limit = subscription ? PRO_DAILY_LIMIT : null;
  const day = localDay(me.timezone || "Africa/Luanda");
  let quota = null;
  let creditCost = 0;
  let remainingCredits = null;
  if (subscription) {
    quota = await env.DB.prepare(
      `INSERT INTO bot_daily_usage (user_id, day, requests, updated_at) VALUES (?, ?, 1, ?)
       ON CONFLICT(user_id, day) DO UPDATE SET requests = requests + 1, updated_at = excluded.updated_at
       WHERE requests < ?
       RETURNING requests`
    ).bind(me.id, day, now, PRO_DAILY_LIMIT).first();
    if (!quota) return fail(429, "Atingiu o limite diário do Pi AI.", { reason: "bot_daily_limit", limit: PRO_DAILY_LIMIT, plan: subscription.plan });
  } else {
    creditCost = 2 + (prompt.length > 800 ? 1 : 0) + (prompt.length > 1600 ? 2 : 0) + (normalizedHistory.length > 8 ? 1 : 0);
    await env.DB.prepare("INSERT OR IGNORE INTO ai_credits (user_id, balance, updated_at) VALUES (?, ?, ?)")
      .bind(me.id, FREE_STARTING_AI_CREDITS, now).run();
    const debit = await env.DB.prepare(
      "UPDATE ai_credits SET balance = balance - ?, updated_at = ? WHERE user_id = ? AND balance >= ? RETURNING balance"
    ).bind(creditCost, now, me.id, creditCost).first();
    if (!debit) {
      const current = await env.DB.prepare("SELECT balance FROM ai_credits WHERE user_id = ?").bind(me.id).first();
      return fail(402, "Os créditos de IA terminaram. Ative o Pro ou aguarde a disponibilização de créditos.", {
        reason: "ai_credits_empty", balance: current?.balance || 0, required: creditCost,
      });
    }
    remainingCredits = debit.balance;
  }

  const messages = [
    {
      role: "system",
      content: "És o Pi AI, o assistente oficial do PiMsg. Responde em português europeu usado em Angola, com linguagem humana, clara, profissional e direta. Usa sempre grafia portuguesa: “contacto”, “utilizador”, “ficheiro”, “ecrã”, “telemóvel” e “equipa”. Nunca uses “contato”, “usuário”, “arquivo” ou “celular”. Não coloques respostas entre aspas, a menos que sejam citações. Evita frases feitas, entusiasmo artificial e emojis desnecessários. Ajuda com perguntas gerais, escrita, ideias, explicações e utilização do PiMsg. Não inventes funcionalidades, preços, políticas ou dados do PiMsg. Não afirmes que enviaste mensagens, alteraste contas, fizeste compras ou executaste ações fora desta conversa. Se não souberes algo, diz claramente. Não peças palavras-passe, códigos de autenticação ou dados bancários."
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
      remaining: subscription ? Math.max(0, limit - quota.requests) : remainingCredits,
      credits_used: creditCost,
    });
  } catch (error) {
    if (subscription) {
      await env.DB.prepare(
        "UPDATE bot_daily_usage SET requests = CASE WHEN requests > 0 THEN requests - 1 ELSE 0 END, updated_at = ? WHERE user_id = ? AND day = ?"
      ).bind(Date.now(), me.id, day).run();
    } else if (creditCost > 0) {
      await env.DB.prepare("UPDATE ai_credits SET balance = balance + ?, updated_at = ? WHERE user_id = ?")
        .bind(creditCost, Date.now(), me.id).run();
    }
    console.error("Pi AI inference failed:", String(error));
    return fail(502, "O Pi AI não conseguiu responder neste momento. Tente novamente.");
  }
}
