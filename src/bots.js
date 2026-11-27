// PiMsg Bots — all inference runs through Cloudflare Workers AI.
const MODEL = "@cf/meta/llama-3.1-8b-instruct-fp8-fast";
const FREE_TRANSLATIONS_PER_DAY = 4;
const PLAN_LIMITS = {
  plus: { ai: 5, translations: 25, customBots: 0, business: false },
  pro: { ai: 20, translations: 75, customBots: 5, business: false },
  business: { ai: 50, translations: 150, customBots: 20, business: true },
};
function tierFor(plan) {
  if (String(plan || "").startsWith("business")) return "business";
  if (String(plan || "").startsWith("plus")) return "plus";
  return "pro"; // Preserves existing pro_weekly and pro_monthly purchases.
}
function limitsFor(plan) { return PLAN_LIMITS[tierFor(plan)] || PLAN_LIMITS.pro; }

const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});
const fail = (status, error, extra = {}) => json({ error, ...extra }, status);

function localDay(timeZone) {
  try { return new Intl.DateTimeFormat("en-CA", { timeZone }).format(new Date()); }
  catch { return new Date().toISOString().slice(0, 10); }
}
async function subscriptionFor(env, userId, now = Date.now()) {
  return env.DB.prepare(
    "SELECT plan FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? ORDER BY expires_at DESC LIMIT 1"
  ).bind(userId, now).first();
}
async function reservePaidAi(env, me, now) {
  const sub = await subscriptionFor(env, me.id, now);
  if (!sub) return { error: fail(402, "Este bot está disponível apenas no plano pago do PiMsg.", { reason: "paid_plan_required" }) };
  const day = localDay(me.timezone || "Africa/Luanda");
  const limits = limitsFor(sub.plan);
  const usage = await env.DB.prepare(
    `INSERT INTO bot_daily_usage (user_id, day, requests, updated_at) VALUES (?, ?, 1, ?)
     ON CONFLICT(user_id, day) DO UPDATE SET requests = requests + 1, updated_at = excluded.updated_at
     WHERE requests < ? RETURNING requests`
  ).bind(me.id, day, now, limits.ai).first();
  if (!usage) return { error: fail(429, "Atingiste o limite diário de utilização dos bots com IA do teu plano.", { reason: "bot_daily_limit", limit: limits.ai, plan: sub.plan }) };
  return { sub, day, requests: usage.requests, limit: limits.ai };
}
async function releasePaidAi(env, me, day) {
  await env.DB.prepare(
    "UPDATE bot_daily_usage SET requests = CASE WHEN requests > 0 THEN requests - 1 ELSE 0 END, updated_at = ? WHERE user_id = ? AND day = ?"
  ).bind(Date.now(), me.id, day).run();
}
async function runModel(env, messages, maxTokens = 450) {
  const result = await env.AI.run(MODEL, { messages, max_tokens: maxTokens, temperature: 0.45, top_p: 0.9 });
  const reply = typeof result?.response === "string" ? result.response.trim()
    : typeof result?.choices?.[0]?.message?.content === "string" ? result.choices[0].message.content.trim() : "";
  if (!reply) throw new Error("empty model response");
  return reply;
}
function aiSystem() {
  return "És um assistente do PiMsg. Responde em português usado em Angola, de forma clara, humana e profissional. Usa grafia portuguesa: contacto, utilizador, ficheiro, ecrã e telemóvel. Não inventes dados, preços, políticas nem ações que não executaste. Nunca peças palavras-passe, códigos de autenticação ou dados bancários.";
}

export async function handleBots(request, env, me, path, method) {
  const now = Date.now();

  if (path === "/v1/bots" && method === "GET") {
    const sub = await subscriptionFor(env, me.id, now);
    const paid = !!sub;
    const tier = paid ? tierFor(sub.plan) : "free";
    const limits = paid ? limitsFor(sub.plan) : { ai: 0, translations: FREE_TRANSLATIONS_PER_DAY, customBots: 0, business: false };
    const day = localDay(me.timezone || "Africa/Luanda");
    const translation = await env.DB.prepare(
      "SELECT translations FROM bot_translation_usage WHERE user_id = ? AND day = ?"
    ).bind(me.id, day).first();
    const aiUsage = paid ? await env.DB.prepare(
      "SELECT requests FROM bot_daily_usage WHERE user_id = ? AND day = ?"
    ).bind(me.id, day).first() : null;
    const business = await env.DB.prepare("SELECT business_name, enabled FROM bot_business_profiles WHERE user_id = ?").bind(me.id).first();
    const reminders = await env.DB.prepare("SELECT COUNT(*) AS n FROM bot_reminders WHERE user_id = ? AND status = 'active'").bind(me.id).first();
    const custom = await env.DB.prepare("SELECT COUNT(*) AS n FROM custom_bots WHERE user_id = ?").bind(me.id).first();
    return json({
      plan: paid ? sub.plan : "free",
      tier,
      paid_access: paid,
      ai_limit_today: limits.ai,
      ai_remaining_today: paid ? Math.max(0, limits.ai - (aiUsage?.requests || 0)) : 0,
      translation_limit_today: limits.translations,
      translation_remaining_today: Math.max(0, limits.translations - (translation?.translations || 0)),
      bots: [
        { id: "pi-ai", name: "Pi AI", description: "Assistente inteligente para perguntas, escrita, ideias e análise.", available: paid, requires_paid: true },
        { id: "pi-business", name: "Pi Business", description: "Ferramentas para vender, organizar clientes e preparar respostas comerciais.", available: !!limits.business, requires_paid: true, configured: !!business, business_name: business?.business_name || null },
        { id: "pi-reminder", name: "Pi Reminder", description: "Organiza tarefas, compromissos, aniversários e lembretes.", available: paid, requires_paid: true, active_reminders: reminders?.n || 0 },
        { id: "pi-translate", name: "Pi Translate", description: "Tradução automática de mensagens com aviso junto à mensagem.", available: true, requires_paid: false, remaining_today: Math.max(0, limits.translations - (translation?.translations || 0)) },
        { id: "pi-bots", name: "Pi Bots", description: "Cria e gere assistentes personalizados para o teu negócio ou trabalho.", available: limits.customBots > 0, requires_paid: true, custom_bots: custom?.n || 0, custom_bot_limit: limits.customBots },
      ],
    });
  }

  const translationMatch = path.match(/^\/v1\/chats\/([\w-]+)\/messages\/([\w-]+)\/translate$/);
  if (translationMatch && method === "POST") {
    if (!env.AI) return fail(503, "O serviço de tradução ainda não está disponível.");
    const conversationId = translationMatch[1], messageId = translationMatch[2];
    const body = await request.json().catch(() => ({}));
    const language = ["pt", "en", "es", "fr"].includes(body.language) ? body.language : "pt";
    const membership = await env.DB.prepare(
      "SELECT 1 AS ok FROM conversation_members WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL"
    ).bind(conversationId, me.id).first();
    if (!membership) return fail(404, "conversa não encontrada");
    const message = await env.DB.prepare(
      "SELECT body, sender_id, kind FROM messages WHERE id = ? AND conversation_id = ? AND deleted_at IS NULL"
    ).bind(messageId, conversationId).first();
    if (!message || message.sender_id === me.id || message.kind !== "text" || !message.body) return fail(404, "mensagem não disponível para tradução");
    const cached = await env.DB.prepare(
      "SELECT translated_body FROM message_translations WHERE user_id = ? AND message_id = ? AND language = ?"
    ).bind(me.id, messageId, language).first();
    if (cached) return json({ translated: cached.translated_body !== "__SAME_LANGUAGE__", translation: cached.translated_body === "__SAME_LANGUAGE__" ? null : cached.translated_body, cached: true });
    const sub = await subscriptionFor(env, me.id, now);
    const day = localDay(me.timezone || "Africa/Luanda");
    const translationLimit = sub ? limitsFor(sub.plan).translations : FREE_TRANSLATIONS_PER_DAY;
    const usage = await env.DB.prepare(
      `INSERT INTO bot_translation_usage (user_id, day, translations, updated_at) VALUES (?, ?, 1, ?)
       ON CONFLICT(user_id, day) DO UPDATE SET translations = translations + 1, updated_at = excluded.updated_at
       WHERE translations < ? RETURNING translations`
    ).bind(me.id, day, now, translationLimit).first();
    if (!usage) return fail(429, `Atingiste o limite de ${translationLimit} traduções de hoje do teu plano. O limite será renovado à meia-noite (hora de Angola).`, { reason: "translation_daily_limit", limit: translationLimit, remaining: 0 });
    const languageName = ({ pt: "português", en: "inglês", es: "espanhol", fr: "francês" })[language];
    try {
      const translated = await runModel(env, [
        { role: "system", content: "És um tradutor de mensagens. Detecta o idioma do texto. Se já estiver no idioma de destino, responde exatamente __SAME_LANGUAGE__. Caso contrário, traduz preservando significado, tom, nomes, emojis e formatação. Responde apenas com a tradução, sem comentários, rótulos ou aspas." },
        { role: "user", content: "Idioma de destino: " + languageName + "\nMensagem: " + message.body.slice(0, 4000) },
      ], 512);
      await env.DB.prepare(
        "INSERT OR REPLACE INTO message_translations (user_id, message_id, language, translated_body, created_at) VALUES (?,?,?,?,?)"
      ).bind(me.id, messageId, language, translated, now).run();
      return json({ translated: translated !== "__SAME_LANGUAGE__", translation: translated === "__SAME_LANGUAGE__" ? null : translated, cached: false, remaining_today: Math.max(0, translationLimit - usage.translations) });
    } catch (error) {
      await env.DB.prepare("UPDATE bot_translation_usage SET translations = CASE WHEN translations > 0 THEN translations - 1 ELSE 0 END, updated_at = ? WHERE user_id = ? AND day = ?").bind(Date.now(), me.id, day).run();
      console.error("PiMsg translation failed:", String(error));
      return fail(502, "Não foi possível traduzir esta mensagem.");
    }
  }

  // Pi Reminder: paid users can create and manage their own reminders.
  if (path === "/v1/bots/reminders" && method === "GET") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub) return fail(402, "O Pi Reminder está disponível no plano pago.", { reason: "paid_plan_required" });
    const rows = await env.DB.prepare("SELECT id, title, details, due_at, status, created_at FROM bot_reminders WHERE user_id = ? ORDER BY due_at ASC LIMIT 200").bind(me.id).all();
    return json({ reminders: rows.results || [] });
  }
  if (path === "/v1/bots/reminders" && method === "POST") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub) return fail(402, "O Pi Reminder está disponível no plano pago.", { reason: "paid_plan_required" });
    const b = await request.json().catch(() => ({}));
    const title = typeof b.title === "string" ? b.title.trim().slice(0, 120) : "";
    const details = typeof b.details === "string" ? b.details.trim().slice(0, 500) : null;
    const dueAt = Number(b.due_at);
    if (!title || !Number.isFinite(dueAt) || dueAt < now - 60000 || dueAt > now + 366 * 86400000) return fail(400, "Indica um título e uma data válida até 12 meses.");
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO bot_reminders (id,user_id,title,details,due_at,status,created_at,updated_at) VALUES (?,?,?,?,?,'active',?,?)")
      .bind(id, me.id, title, details, dueAt, now, now).run();
    return json({ id, title, details, due_at: dueAt, status: "active" }, 201);
  }
  const reminderMatch = path.match(/^\/v1\/bots\/reminders\/([\w-]+)$/);
  if (reminderMatch && ["PATCH", "DELETE"].includes(method)) {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub) return fail(402, "O Pi Reminder está disponível no plano pago.", { reason: "paid_plan_required" });
    if (method === "DELETE") {
      await env.DB.prepare("DELETE FROM bot_reminders WHERE id = ? AND user_id = ?").bind(reminderMatch[1], me.id).run();
      return json({ ok: true });
    }
    const b = await request.json().catch(() => ({}));
    const title = typeof b.title === "string" ? b.title.trim().slice(0, 120) : null;
    const details = typeof b.details === "string" ? b.details.trim().slice(0, 500) : null;
    const dueAt = b.due_at === undefined ? null : Number(b.due_at);
    const status = ["active", "done"].includes(b.status) ? b.status : null;
    await env.DB.prepare("UPDATE bot_reminders SET title=COALESCE(?,title), details=COALESCE(?,details), due_at=COALESCE(?,due_at), status=COALESCE(?,status), updated_at=? WHERE id=? AND user_id=?")
      .bind(title, details, Number.isFinite(dueAt) ? dueAt : null, status, now, reminderMatch[1], me.id).run();
    return json({ ok: true });
  }

  // Pi Business: private business knowledge and AI customer-service replies.
  if (path === "/v1/bots/business" && method === "GET") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub || !limitsFor(sub.plan).business) return fail(402, "O Pi Business está incluído no plano Business.", { reason: "business_plan_required" });
    const row = await env.DB.prepare("SELECT business_name, description, greeting, faq, hours, contact, enabled, updated_at FROM bot_business_profiles WHERE user_id = ?").bind(me.id).first();
    return json({ profile: row || null });
  }
  if (path === "/v1/bots/business" && method === "PUT") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub || !limitsFor(sub.plan).business) return fail(402, "O Pi Business está incluído no plano Business.", { reason: "business_plan_required" });
    const b = await request.json().catch(() => ({}));
    const businessName = typeof b.business_name === "string" ? b.business_name.trim().slice(0, 100) : "";
    if (!businessName) return fail(400, "Indica o nome do negócio.");
    const fields = [
      typeof b.description === "string" ? b.description.slice(0, 1500) : "",
      typeof b.greeting === "string" ? b.greeting.slice(0, 500) : "",
      typeof b.faq === "string" ? b.faq.slice(0, 5000) : "",
      typeof b.hours === "string" ? b.hours.slice(0, 500) : "",
      typeof b.contact === "string" ? b.contact.slice(0, 300) : "",
    ];
    const enabled = b.enabled === false ? 0 : 1;
    await env.DB.prepare(
      `INSERT INTO bot_business_profiles (user_id,business_name,description,greeting,faq,hours,contact,enabled,updated_at)
       VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET business_name=excluded.business_name,
       description=excluded.description,greeting=excluded.greeting,faq=excluded.faq,hours=excluded.hours,
       contact=excluded.contact,enabled=excluded.enabled,updated_at=excluded.updated_at`
    ).bind(me.id, businessName, ...fields, enabled, now).run();
    return json({ ok: true });
  }
  if (path === "/v1/bots/business/chat" && method === "POST") {
    const businessSub = await subscriptionFor(env, me.id, now);
    if (!businessSub || !limitsFor(businessSub.plan).business) return fail(402, "O atendimento Pi Business está incluído apenas no plano Business.", { reason: "business_plan_required" });
    if (!env.AI) return fail(503, "A IA da Cloudflare não está disponível.");
    const reservation = await reservePaidAi(env, me, now);
    if (reservation.error) return reservation.error;
    const b = await request.json().catch(() => ({}));
    const prompt = typeof b.message === "string" ? b.message.trim().slice(0, 1500) : "";
    if (!prompt) { await releasePaidAi(env, me, reservation.day); return fail(400, "Escreve a mensagem do cliente."); }
    try {
      const p = await env.DB.prepare("SELECT * FROM bot_business_profiles WHERE user_id = ? AND enabled = 1").bind(me.id).first();
      if (!p) { await releasePaidAi(env, me, reservation.day); return fail(409, "Configura e activa primeiro o teu Pi Business.", { reason: "business_not_configured" }); }
      const reply = await runModel(env, [
        { role: "system", content: aiSystem() + " És o assistente de atendimento do negócio " + p.business_name + ". Responde apenas com base nos dados seguintes; se não souberes, informa que a equipa precisa confirmar. Nunca inventes preços, stock ou prazos.\nDescrição: " + (p.description || "") + "\nFAQ: " + (p.faq || "") + "\nHorário: " + (p.hours || "") + "\nContacto: " + (p.contact || "") + "\nSaudação: " + (p.greeting || "") },
        { role: "user", content: prompt },
      ]);
      return json({ reply, remaining: Math.max(0, reservation.limit - reservation.requests), plan: reservation.sub.plan });
    } catch (error) {
      await releasePaidAi(env, me, reservation.day);
      console.error("Pi Business failed:", String(error));
      return fail(502, "O Pi Business não conseguiu responder. Tenta novamente.");
    }
  }

  // Pi Bots: user-owned custom assistants.
  if (path === "/v1/bots/custom" && method === "GET") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub || limitsFor(sub.plan).customBots < 1) return fail(402, "Os bots personalizados estão incluídos nos planos Pro e Business.", { reason: "pro_plan_required" });
    const rows = await env.DB.prepare("SELECT id,name,description,created_at,updated_at FROM custom_bots WHERE user_id = ? ORDER BY created_at DESC").bind(me.id).all();
    return json({ bots: rows.results || [] });
  }
  if (path === "/v1/bots/custom" && method === "POST") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub || limitsFor(sub.plan).customBots < 1) return fail(402, "Os bots personalizados estão incluídos nos planos Pro e Business.", { reason: "pro_plan_required" });
    const b = await request.json().catch(() => ({}));
    const name = typeof b.name === "string" ? b.name.trim().slice(0, 60) : "";
    const description = typeof b.description === "string" ? b.description.trim().slice(0, 240) : "";
    const instructions = typeof b.instructions === "string" ? b.instructions.trim().slice(0, 4000) : "";
    if (!name || !instructions) return fail(400, "O nome e as instruções do bot são obrigatórios.");
    const count = await env.DB.prepare("SELECT COUNT(*) AS n FROM custom_bots WHERE user_id = ?").bind(me.id).first();
    if ((count?.n || 0) >= limitsFor(sub.plan).customBots) return fail(409, `O teu plano permite até ${limitsFor(sub.plan).customBots} bots personalizados.`);
    const id = crypto.randomUUID();
    await env.DB.prepare("INSERT INTO custom_bots (id,user_id,name,description,instructions,created_at,updated_at) VALUES (?,?,?,?,?,?,?)")
      .bind(id, me.id, name, description, instructions, now, now).run();
    return json({ id, name, description, created_at: now }, 201);
  }
  const customMatch = path.match(/^\/v1\/bots\/custom\/([\w-]+)$/);
  if (customMatch && method === "DELETE") {
    const sub = await subscriptionFor(env, me.id, now);
    if (!sub || limitsFor(sub.plan).customBots < 1) return fail(402, "Os bots personalizados estão incluídos nos planos Pro e Business.", { reason: "pro_plan_required" });
    await env.DB.prepare("DELETE FROM custom_bots WHERE id = ? AND user_id = ?").bind(customMatch[1], me.id).run();
    return json({ ok: true });
  }
  const customChatMatch = path.match(/^\/v1\/bots\/custom\/([\w-]+)\/chat$/);
  if (customChatMatch && method === "POST") {
    if (!env.AI) return fail(503, "A IA da Cloudflare não está disponível.");
    const reservation = await reservePaidAi(env, me, now);
    if (reservation.error) return reservation.error;
    const bot = await env.DB.prepare("SELECT * FROM custom_bots WHERE id = ? AND user_id = ?").bind(customChatMatch[1], me.id).first();
    const b = await request.json().catch(() => ({}));
    const prompt = typeof b.message === "string" ? b.message.trim().slice(0, 2000) : "";
    if (!bot || !prompt) { await releasePaidAi(env, me, reservation.day); return fail(bot ? 400 : 404, bot ? "Escreve uma mensagem." : "Bot não encontrado."); }
    try {
      const reply = await runModel(env, [
        { role: "system", content: aiSystem() + "\nNome: " + bot.name + "\nDescrição: " + (bot.description || "") + "\nInstruções do criador: " + bot.instructions },
        { role: "user", content: prompt },
      ]);
      return json({ reply, remaining: Math.max(0, reservation.limit - reservation.requests), plan: reservation.sub.plan });
    } catch (error) {
      await releasePaidAi(env, me, reservation.day);
      console.error("Custom Pi Bot failed:", String(error));
      return fail(502, "Este bot não conseguiu responder. Tenta novamente.");
    }
  }

  if (path === "/v1/bots/pi-ai/chat" && method === "POST") {
    if (!env.AI) return fail(503, "O serviço de IA ainda não está configurado na Cloudflare.");
    const reservation = await reservePaidAi(env, me, now);
    if (reservation.error) return reservation.error;
    const body = await request.json().catch(() => null);
    if (!body || typeof body !== "object") { await releasePaidAi(env, me, reservation.day); return fail(400, "pedido inválido"); }
    const prompt = typeof body.message === "string" ? body.message.trim() : "";
    if (!prompt) { await releasePaidAi(env, me, reservation.day); return fail(400, "Escreve uma mensagem."); }
    if (prompt.length > 2000) { await releasePaidAi(env, me, reservation.day); return fail(413, "A mensagem não pode ultrapassar 2.000 caracteres."); }
    const history = Array.isArray(body.history) ? body.history.slice(-10).filter(x => x && ["user","assistant"].includes(x.role) && typeof x.content === "string").map(x => ({ role: x.role, content: x.content.trim().slice(0,2500) })).filter(x => x.content) : [];
    try {
      const reply = await runModel(env, [
        { role: "system", content: aiSystem() + " És o Pi AI, o assistente oficial do PiMsg. Ajuda com perguntas gerais, escrita, ideias, explicações e utilização do PiMsg. Não afirmes que executaste ações fora desta conversa." },
        ...history,
        { role: "user", content: prompt },
      ], 700);
      return json({ reply, model: MODEL, plan: reservation.sub.plan, limit: reservation.limit, remaining: Math.max(0, PAID_DAILY_AI_LIMIT - reservation.requests) });
    } catch (error) {
      await releasePaidAi(env, me, reservation.day);
      console.error("Pi AI inference failed:", String(error));
      return fail(502, "O Pi AI não conseguiu responder neste momento. Tenta novamente.");
    }
  }

  return null;
}
