// PiMsg - traducao de mensagens com Cloudflare Workers AI.
// Nao guarda o texto nem a traducao: o cliente recebe o resultado e decide como apresentar.

const MODEL = "@cf/meta/m2m100-1.2b";
const LANG = /^[a-z]{2,3}$/i;

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });

const DAILY_TRANSLATION_LIMIT = 50;

function localDay(timeZone) {
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

export async function handleTranslate(request, env, me) {
  if (request.method !== "POST") {
    return json({ error: "metodo nao permitido" }, 405);
  }
  const now = Date.now();
  const subscription = await env.DB.prepare(
    "SELECT 1 AS ok FROM subscriptions WHERE user_id = ? AND status IN ('active','grace') AND expires_at > ? LIMIT 1"
  ).bind(me.id, now).first();
  if (!subscription) {
    return json({ error: "A traducao automatica esta disponivel apenas no plano Pro.", reason: "pro_required" }, 402);
  }

  if (!env.AI) {
    return json({ error: "Cloudflare Workers AI nao esta configurado" }, 503);
  }

  const body = await request.json().catch(() => null);
  if (!body || typeof body !== "object") {
    return json({ error: "corpo JSON invalido" }, 400);
  }

  const text = typeof body.text === "string" ? body.text.trim() : "";
  const sourceLang = typeof body.source_lang === "string" ? body.source_lang.trim().toLowerCase() : "en";
  const targetLang = typeof body.target_lang === "string" ? body.target_lang.trim().toLowerCase() : "";

  if (!text) return json({ error: "texto obrigatorio" }, 400);
  if (text.length > 2000) return json({ error: "texto demasiado longo (maximo 2000 caracteres)" }, 413);
  if (!LANG.test(sourceLang) || !LANG.test(targetLang)) {
    return json({ error: "idioma de origem ou destino invalido" }, 400);
  }
  if (sourceLang === targetLang) {
    return json({ original_text: text, translated_text: text, translated: false, marker: null });
  }

  const day = localDay(me.timezone || "Africa/Luanda");
  const quota = await env.DB.prepare(
    `INSERT INTO translation_usage (user_id, day, translations_count) VALUES (?, ?, 1)
     ON CONFLICT(user_id, day) DO UPDATE SET translations_count = translations_count + 1
     WHERE translations_count < ?
     RETURNING translations_count`
  ).bind(me.id, day, DAILY_TRANSLATION_LIMIT).first();
  if (!quota) {
    return json({ error: "Atingiste o limite diario de 50 traducoes do plano Pro.", reason: "translation_daily_limit", limit: DAILY_TRANSLATION_LIMIT }, 429);
  }

  try {
    const result = await env.AI.run(MODEL, {
      text,
      source_lang: sourceLang,
      target_lang: targetLang,
    });

    const translatedText = result?.translated_text;
    if (typeof translatedText !== "string" || !translatedText.trim()) {
      console.error("Workers AI returned no translated_text");
      return json({ error: "nao foi possivel traduzir esta mensagem" }, 502);
    }

    return json({
      original_text: text,
      translated_text: translatedText,
      translated: true,
      marker: "TR",
      model: MODEL,
    });
  } catch (error) {
    console.error("PiMsg translation failed:", String(error));
    return json({ error: "servico de traducao temporariamente indisponivel" }, 502);
  }
}
