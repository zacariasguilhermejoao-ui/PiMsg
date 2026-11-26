
import { pushToUsers } from "./push.js";

const PLAY_URL = "https://play.google.com/store/apps/details?id=com.pimsg.isabstudio";
const LEGAL_VERSION = "1.1";
const LEGAL_DATE = "26 de novembro de 2026";
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const code = (n) => Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => ALPHABET[b % ALPHABET.length]).join("");
const jsonRes = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });

const CSS = `:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#000;color:#fff;font:16px/1.65 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}main{max-width:680px;margin:0 auto;padding:32px 22px 64px}.brand{font-weight:700;font-size:30px;letter-spacing:-.5px;margin-bottom:28px}h1{font-size:26px;line-height:1.25;margin:0 0 10px}h2{font-size:18px;margin:28px 0 6px}p,li{color:#c9c9ce}ol,ul{padding-left:22px}a{color:#fff}.card{background:#17171a;border-radius:18px;padding:22px;margin:20px 0}.code{font:700 30px/1.2 ui-monospace,monospace;letter-spacing:4px;text-align:center;padding:16px;background:#232327;border-radius:14px;margin:14px 0}.btn{display:block;width:100%;text-align:center;border:0;border-radius:28px;padding:15px 20px;font:600 16px system-ui,sans-serif;text-decoration:none;cursor:pointer;margin-top:12px}.w{background:#fff;color:#000}.g{background:#232327;color:#fff}input{width:100%;background:#000;color:#fff;border:1px solid #3a3a3f;border-radius:14px;padding:14px;font:16px system-ui,sans-serif}.muted{color:#8e8e93;font-size:14px}.err{color:#e5484d}footer{margin-top:40px;font-size:14px;color:#8e8e93}footer a{color:#8e8e93;margin-right:14px;display:inline-block}.profile{display:flex;flex-direction:column;align-items:center;text-align:center;gap:8px;margin:8px 0 22px}.avatar{width:104px;height:104px;border-radius:50%;object-fit:cover;background:#232327}.link-photo{width:100%;max-height:300px;object-fit:cover;border-radius:16px;margin:14px 0}`;

function html(title, body, status = 200) {
  const page = `<!doctype html><html lang="pt"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)} | PiMsg</title><style>${CSS}</style></head><body><main><div class="brand">PiMsg</div>${body}<footer><a href="/terms">Termos de uso</a><a href="/privacy">Privacidade</a><a href="/refund">Reembolso</a><a href="/community">Regras da comunidade</a><a href="/support">Suporte</a><a href="/delete-account">Apagar conta</a><p>ISAB Group</p></footer></main></body></html>`;
  return new Response(page, {
    status,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-cache",
      "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
    },
  });
}

const COPY_JS = `<script>function copyCode(id,btn){var t=document.getElementById(id).textContent;(navigator.clipboard?navigator.clipboard.writeText(t):Promise.reject()).then(function(){btn.textContent='Copiado'}).catch(function(){btn.textContent='Selecione e copie o texto acima'})}</script>`;

const steps = `<ol><li>Instale o PiMsg na Play Store.</li><li>Entre com a sua conta Google ou com o seu número.</li><li>Toque na lupa, no topo, e cole o código.</li></ol>`;

const CONTACTS = [
  ["Suporte", "suporte@tchilopop.com"],
  ["Parcerias", "parceria@tchilopop.com"],
  ["Privacidade e dados pessoais", "privacidade@tchilopop.com"],
  ["Contas e faturação", "contas@tchilopop.com"],
  ["Denúncias", "denuncia@tchilopop.com"],
];

function contactsBlock() {
  return `<h2>Contactos oficiais</h2><ul>${CONTACTS.map(([label, email]) => `<li>${esc(label)}: <a href="mailto:${esc(email)}">${esc(email)}</a></li>`).join("")}</ul>`;
}

function legalDoc(title, intro, sections, env) {
  const body = sections.map(([h, t]) => `<h2>${esc(h)}</h2>${t}`).join("");
  return html(title, `<h1>${esc(title)}</h1><p class="muted">Versão ${LEGAL_VERSION}, atualizada em ${LEGAL_DATE}</p><p class="muted">A PiMsg é a aplicação de mensagens do Tchilo Pop, uma plataforma de rede social. A aplicação pertence à ISAB Group.</p><p>${intro}</p>${body}${contactsBlock()}`);
}

const LEGAL = {
  terms: (env) => legalDoc("Termos de uso", "Estes termos regulam o uso do PiMsg, a aplicação de mensagens do Tchilo Pop, uma plataforma de rede social, pertencente à ISAB Group. Ao criar conta ou usar o app, aceita estes termos.", [
    ["1. Conta e idade mínima", "<p>Para usar o PiMsg tem de ter pelo menos 16 anos. É responsável pela sua conta e por tudo o que é feito nela. Pode entrar com Google ou com o seu número de telefone, confirmado por SMS.</p>"],
    ["2. Planos e limites", "<p>O plano Free permite iniciar conversas com até 30 pessoas e enviar até 30 mensagens por dia. O plano Pro, semanal ou mensal, retira esses limites e permite o vídeo de apresentação no perfil. Quando o limite diário acaba, pode comprar créditos de 10 mensagens ou 15 horas de mensagens ilimitadas. Continua a poder receber mensagens. Os preços são mostrados na Google Play antes de comprar e podem variar conforme o país, a moeda e os impostos.</p>"],
    ["3. Uso permitido", "<p>Pode usar o PiMsg para conversar e para vender e negociar de forma honesta. É proibido:</p><ul><li>enganar ou aplicar golpes, incluindo vender o que não existe;</li><li>enviar spam ou mensagens em massa a quem não as pediu;</li><li>assediar, ameaçar ou discriminar;</li><li>partilhar conteúdo ilegal, violento ou sexual envolvendo menores, ou conteúdo sexual sem consentimento;</li><li>fazer-se passar por outra pessoa;</li><li>contornar os limites do app com contas falsas ou automatismos.</li></ul>"],
    ["4. Moderação", "<p>Qualquer pessoa pode bloquear e denunciar utilizadores, mensagens, grupos e stories. Analisamos as denúncias e podemos remover conteúdo, avisar, suspender temporariamente ou banir contas que quebrem estas regras. Quem for sancionado pode pedir revisão através do suporte. Os detalhes estão nas Regras da comunidade.</p>"],
    ["5. O seu conteúdo", "<p>O conteúdo que envia continua a ser seu. Dá-nos apenas a permissão necessária para o guardar, transmitir e mostrar aos destinatários que escolheu. É responsável pelo que envia e por ter direito a enviá-lo.</p>"],
    ["6. Negociações entre utilizadores", "<p>O PiMsg é uma ferramenta de conversa. Não somos parte nas vendas, pagamentos ou acordos feitos entre utilizadores e não garantimos a qualidade, a entrega ou a identidade de quem vende ou compra. Tenha cuidado com pagamentos adiantados.</p>"],
    ["7. Disponibilidade e alterações", "<p>Trabalhamos para manter o serviço disponível, mas podem existir interrupções. Podemos atualizar estes termos e, se a mudança for importante, avisamos no app.</p>"],
    ["8. Reclamações e contacto", "<p>Para questões de conta, suporte, denúncias ou privacidade, use os contactos oficiais apresentados no fim desta página. As reclamações serão registadas e analisadas pela equipa responsável.</p>"],
    ["9. Lei aplicável", "<p>Estes termos regem-se pela lei aplicável na República de Angola, sem prejuízo dos direitos imperativos dos consumidores no seu país.</p>"],
  ], env),

  privacy: (env) => legalDoc("Política de privacidade", "Esta política explica que dados o PiMsg, aplicação de mensagens do Tchilo Pop e pertencente à ISAB Group, recolhe, para que serve e quais são os seus direitos.", [
    ["1. Dados que recolhemos", "<ul><li>Conta: nome e e-mail da conta Google, ou número de telefone confirmado por SMS.</li><li>Perfil: nome, apresentação, foto, vídeo de apresentação e o código pessoal que gerar. O número de telefone só aparece no seu perfil se escolher mostrá-lo, e ninguém encontra outra pessoa pesquisando pelo número.</li><li>Conteúdo: mensagens, fotos, vídeos, áudios, ficheiros e stories que envia.</li><li>Dispositivo: identificador do aparelho, token de notificações e dados técnicos, usados para segurança e para limitar contas falsas.</li><li>Uso: contagens de mensagens e conversas necessárias para aplicar os limites dos planos, e registos de compras feitas na Google Play.</li><li>Denúncias e moderação: denúncias que faz ou recebe e as ações tomadas.</li></ul>"],
    ["2. Para que usamos", "<p>Para prestar o serviço, entregar mensagens e notificações, aplicar os planos e limites, prevenir fraude e abuso, tratar denúncias, cumprir obrigações legais e melhorar o app.</p>"],
    ["3. Mensagens", "<p>As mensagens e os ficheiros ficam guardados nos nossos servidores para poderem ser entregues e consultados. Neste momento não usam cifragem de ponta a ponta. Só os participantes da conversa têm acesso pelo app. A nossa equipa só consulta conteúdo quando necessário para tratar uma denúncia, por razões de segurança ou por obrigação legal.</p>"],
    ["4. Com quem partilhamos", "<p>Usamos prestadores que processam dados por nossa conta: Google (Firebase, para entrada na conta, confirmação por SMS e notificações; e Google Play, para pagamentos) e Cloudflare (alojamento, base de dados e armazenamento de ficheiros). Podemos divulgar dados quando a lei o exigir. Não vendemos os seus dados.</p>"],
    ["5. Quanto tempo guardamos", "<p>Guardamos os dados enquanto a conta existir. Os stories expiram ao fim de 24 horas. Depois de apagar a conta, removemos ou anonimizamos os seus dados pessoais, exceto o que for necessário manter por motivos legais ou de segurança, como registos de denúncias e de compras, durante o tempo estritamente necessário.</p>"],
    ["6. Os seus direitos", "<p>Pode aceder, corrigir e pedir a eliminação dos seus dados, e pedir uma cópia deles. Pode apagar a conta no app, em Menu, Definições, Apagar conta, ou pelo formulário em /delete-account. O pedido tem um período de 14 dias em que pode desistir.</p>"],
    ["7. Menores", "<p>O PiMsg não é destinado a menores de 16 anos. Se souber de uma conta de um menor dessa idade, contacte-nos para a removermos.</p>"],
    ["8. Segurança e transferências", "<p>Usamos ligações cifradas e controlo de acessos. Os prestadores indicados podem processar dados fora do seu país, com as proteções que oferecem. Nenhum sistema é totalmente seguro.</p>"],
    ["9. Direitos e pedidos", "<p>Para exercer direitos de acesso, retificação, oposição ou eliminação, contacte privacidade@tchilopop.com. Para questões de acesso à conta, contacte contas@tchilopop.com. Para denunciar conteúdo ou comportamento, use denuncia@tchilopop.com.</p>"],
    ["10. Alterações", "<p>Se esta política mudar de forma relevante, avisamos no app.</p>"],
  ], env),

  refund: (env) => legalDoc("Política de reembolso", "Esta política aplica-se às compras feitas no PiMsg: planos Pro e créditos de mensagens.", [
    ["1. Pagamentos pela Google Play", "<p>As compras digitais feitas na aplicação são processadas pela Google Play. O pedido de reembolso deve ser submetido na Google Play, através do histórico de encomendas da conta Google utilizada na compra. A decisão e o processamento do reembolso seguem as regras da Google Play e a legislação de defesa do consumidor aplicável.</p>"],
    ["2. Planos Pro", "<p>Os planos semanal e mensal podem renovar-se automaticamente, conforme indicado no ecrã de compra. Pode cancelar a renovação a qualquer momento na Google Play, em Pagamentos e subscrições. O cancelamento impede a próxima cobrança, mas normalmente mantém o acesso até ao fim do período pago. A elegibilidade para reembolso é avaliada pela Google Play e pela legislação aplicável; esta política não elimina direitos legais do consumidor.</p>"],
    ["3. Créditos de mensagens", "<p>Os créditos são disponibilizados após a confirmação do pagamento. Se os créditos não forem entregues, aparecerem duplicados ou existir uma falha técnica que impeça a sua utilização, contacte contas@tchilopop.com com o comprovativo da Google Play. Créditos consumidos não são normalmente reembolsáveis, sem prejuízo dos direitos que a lei reconhece ao consumidor.</p>"],
    ["4. Quando há reembolso", "<p>Quando um reembolso é aprovado, o acesso ou os créditos associados à compra podem ser ajustados para refletir o valor devolvido, sem afetar compras diferentes ou a conta PiMsg em si.</p>"],
    ["5. Problemas com uma compra", "<p>Se pagou e não recebeu o plano ou os créditos, contacte contas@tchilopop.com. Inclua o ID da encomenda da Google Play, a data e uma descrição do problema. Nunca envie a palavra-passe da sua conta Google.</p>"],
    ["6. Prazos e direitos legais", "<p>Os prazos de análise e processamento dependem da Google Play e do método de pagamento. Nada nesta política limita direitos de reembolso ou garantia que sejam obrigatórios segundo a legislação aplicável.</p>"],
  ], env),

  community: (env) => legalDoc("Regras da comunidade", "O PiMsg existe para facilitar conversas e vendas honestas. Estas regras mantêm o espaço seguro para todos.", [
    ["O que não é permitido", "<ul><li>Golpes, produtos falsos e pedidos de pagamento enganosos.</li><li>Spam e mensagens em massa para quem não pediu contacto.</li><li>Assédio, ameaças, ódio e discriminação.</li><li>Conteúdo sexual envolvendo menores, ou conteúdo sexual sem consentimento, e imagens de violência extrema.</li><li>Venda de produtos ilegais ou perigosos.</li><li>Roubar a identidade de outra pessoa e criar contas falsas para contornar limites.</li></ul>"],
    ["Como denunciar e bloquear", "<p>Toque na pessoa, mensagem, grupo ou story e escolha Denunciar ou Bloquear. Quem é bloqueado deixa de poder escrever-lhe e de ver os seus stories.</p>"],
    ["O que fazemos", "<p>Analisamos as denúncias. Conforme a gravidade, removemos o conteúdo, enviamos um aviso, suspendemos a conta por algum tempo ou banimos. Casos graves, como abuso de menores, levam a banimento imediato e podem ser comunicados às autoridades.</p>"],
    ["Pedir revisão", "<p>Se acha que foi sancionado por engano, contacte o suporte e reveremos o caso.</p>"],
  ], env),
};

function supportPage(env) {
  return html("Suporte", `<h1>Suporte</h1><p>Escolha o contacto adequado para receber ajuda mais rapidamente.</p>${contactsBlock()}<h2>Perguntas frequentes</h2><p><b>Como encontro alguém?</b> Toque na lupa e escreva o nome, o código ou o código de convite de um grupo. O número de telefone não serve para pesquisar.</p><p><b>Como mudo o meu código?</b> Em Perfil, toque em Gerar novo código. O código anterior deixa de valer.</p><p><b>Como cancelo o plano Pro?</b> Na Google Play, em Pagamentos e subscrições.</p><p><b>Como apago a conta?</b> No app, em Menu, Definições, Apagar conta, ou em <a href="/delete-account">pimsg.tchilopop.com/delete-account</a>. Para pedidos sobre a conta, escreva para contas@tchilopop.com.</p>`);
}

function deletePage() {
  return html("Apagar conta", `<h1>Apagar conta</h1><p>Pode apagar a sua conta PiMsg e os dados associados. O pedido fica pendente durante 14 dias e pode desistir nesse período entrando de novo no app.</p><p>Se ainda tem acesso ao app, o caminho mais rápido é Menu, Definições, Apagar conta.</p><div class="card"><p>Se não consegue entrar, indique o e-mail ou número usado na conta e pedimos a verificação por esse meio.</p><input id="c" placeholder="E-mail ou número da conta" autocomplete="off"><button class="btn w" id="b">Pedir eliminação</button><p id="m" class="muted"></p></div><script>document.getElementById('b').onclick=function(){var v=document.getElementById('c').value.trim(),m=document.getElementById('m');if(v.length<5){m.textContent='Indique o e-mail ou número da conta.';m.className='err';return}fetch('/delete-account',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({contact:v})}).then(function(r){return r.json()}).then(function(j){m.className=j.ok?'muted':'err';m.textContent=j.ok?'Pedido recebido. Vamos confirmar consigo pelo contacto indicado.':(j.error||'Não foi possível enviar o pedido.')}).catch(function(){m.className='err';m.textContent='Não foi possível enviar o pedido.'})}</script>`);
}

async function groupPage(env, groupCode) {
  const g = await env.DB.prepare(
    `SELECT c.name, c.description, (SELECT COUNT(*) FROM conversation_members WHERE conversation_id = c.id AND left_at IS NULL) AS members
     FROM conversations c WHERE c.type = 'group' AND c.invite_code = ?`
  ).bind(groupCode.toUpperCase()).first();
  if (!g) return html("Grupo não encontrado", `<h1>Grupo não encontrado</h1><p>O link pode estar errado ou o grupo já não existe.</p>`, 404);
  const c = esc(groupCode.toUpperCase());
  return html(g.name, `<h1>${esc(g.name)}</h1><p>${g.description ? esc(g.description) + " " : ""}<span class="muted">${g.members} membro${g.members === 1 ? "" : "s"}</span></p><div class="card"><p>Código de convite</p><div class="code" id="cd">${c}</div><button class="btn g" onclick="copyCode('cd',this)">Copiar código</button><a class="btn w" href="${PLAY_URL}&referrer=${encodeURIComponent(`pimsg_type=group&pimsg_code=${groupCode.toUpperCase()}`)}">Baixar na Play Store</a></div><h2>Como entrar</h2>${steps}${COPY_JS}`);
}

async function linkPage(env, slug) {
  const l = await env.DB.prepare(
    `SELECT l.subject, l.intro_text, l.image_media_id, u.display_name, u.avatar_key, u.ring_color
     FROM contact_links l JOIN users u ON u.id = l.user_id
     WHERE l.slug = ? AND l.is_active = 1 AND u.status = 'active'`
  ).bind(slug).first();
  if (!l) return html("Link não encontrado", `<h1>Link não encontrado</h1><p>Este link já não está ativo.</p>`, 404);
  await env.DB.prepare("UPDATE contact_links SET opens_count = opens_count + 1 WHERE slug = ?").bind(slug).run();
  const avatar = l.avatar_key ? `<img class="avatar" src="/public-media/${encodeURIComponent(l.avatar_key)}" alt="Fotografia de ${esc(l.display_name)}">` : "";
  const photo = l.image_media_id ? `<img class="link-photo" src="/public-media/${encodeURIComponent(l.image_media_id)}" alt="Imagem de ${esc(l.subject)}">` : "";
  return html(l.display_name, `<div class="profile">${avatar}<h1>Converse com ${esc(l.display_name)}</h1></div><p>Assunto: <b>${esc(l.subject)}</b></p>${photo}${l.intro_text ? `<p>${esc(l.intro_text)}</p>` : ""}<div class="card" id="box"><p>Escreva o seu nome para gerar o código de conversa.</p><input id="n" placeholder="O seu nome" maxlength="60" autocomplete="name"><button class="btn w" id="go">Gerar código</button><p id="e" class="err"></p></div><h2>Como falar</h2>${steps}${COPY_JS}<script>document.getElementById('go').onclick=function(){var n=document.getElementById('n').value.trim(),e=document.getElementById('e');if(n.length<2){e.textContent='Escreva o seu nome.';return}fetch('/u/${slug}/request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({name:n})}).then(function(r){return r.json()}).then(function(j){if(!j.code){e.textContent=j.error||'Não foi possível gerar o código.';return}document.getElementById('box').innerHTML='<p>O seu código de conversa</p><div class="code" id="cd">'+j.code+'</div><button class="btn g" onclick="copyCode(\\'cd\\',this)">Copiar código</button><a class="btn w" href="${PLAY_URL}&referrer='+encodeURIComponent('pimsg_type=contact&pimsg_code='+j.code)+'">Baixar na Play Store</a><p class="muted">O código vale por 7 dias.</p>'}).catch(function(){e.textContent='Não foi possível gerar o código.'})}</script>`);
}

async function requestCode(request, env, slug) {
  const b = await request.json().catch(() => ({}));
  const name = typeof b.name === "string" ? b.name.trim().slice(0, 60) : "";
  if (name.length < 2) return jsonRes({ error: "Escreva o seu nome." }, 400);
  const l = await env.DB.prepare(
    `SELECT l.id, l.user_id, l.subject FROM contact_links l JOIN users u ON u.id = l.user_id
     WHERE l.slug = ? AND l.is_active = 1 AND u.status = 'active'`
  ).bind(slug).first();
  if (!l) return jsonRes({ error: "Link indisponível." }, 404);
  const now = Date.now();
  const recent = await env.DB.prepare("SELECT COUNT(*) AS n FROM contact_requests WHERE link_id = ? AND created_at > ?").bind(l.id, now - 3600_000).first();
  if (recent.n >= 200) return jsonRes({ error: "Muitos pedidos neste momento. Tente mais tarde." }, 429);
  const rc = code(8);
  await env.DB.prepare(
    "INSERT INTO contact_requests (id, link_id, visitor_name, request_code, status, created_at, expires_at) VALUES (?,?,?,?, 'pending', ?, ?)"
  ).bind(crypto.randomUUID(), l.id, name, rc, now, now + 7 * 86400_000).run();
  await pushToUsers(env, [l.user_id], "Novo interessado", `${name} pretende enviar uma mensagem sobre ${l.subject}`, { type: "contact_request", link_id: l.id });
  return jsonRes({ code: rc });
}

async function deleteRequest(request, env) {
  const b = await request.json().catch(() => ({}));
  const contact = typeof b.contact === "string" ? b.contact.trim().slice(0, 120) : "";
  if (contact.length < 5) return jsonRes({ error: "Indique o e-mail ou número da conta." }, 400);
  const now = Date.now();
  const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM data_requests WHERE contact_email = ? AND requested_at > ?").bind(contact, now - 86400_000).first();
  if (n.n >= 3) return jsonRes({ error: "Já recebemos o seu pedido." }, 429);
  await env.DB.prepare(
    "INSERT INTO data_requests (id, user_id, type, source, contact_email, status, requested_at, scheduled_for) VALUES (?, NULL, 'delete', 'web', ?, 'pending', ?, ?)"
  ).bind(crypto.randomUUID(), contact, now, now + 14 * 86400_000).run();
  return jsonRes({ ok: true });
}

export async function handlePublic(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method;

  if (method === "GET") {
    const publicMediaId = path.startsWith("/public-media/") ? path.slice("/public-media/".length) : "";
    if (/^[A-Za-z0-9-]+$/.test(publicMediaId)) {
      const media = await env.DB.prepare(
        `SELECT m.r2_key, m.mime FROM media m
         WHERE m.id = ? AND (
           EXISTS (SELECT 1 FROM users u WHERE u.avatar_key = m.id AND u.status = 'active')
           OR EXISTS (SELECT 1 FROM contact_links l WHERE l.image_media_id = m.id AND l.is_active = 1)
         )`
      ).bind(publicMediaId).first();
      if (!media || !String(media.mime).startsWith("image/")) return new Response("Imagem nao encontrada", { status: 404 });
      const object = await env.MEDIA.get(media.r2_key);
      if (!object) return new Response("Imagem nao encontrada", { status: 404 });
      return new Response(object.body, { headers: {
        "content-type": media.mime,
        "cache-control": "public, max-age=3600",
        "x-content-type-options": "nosniff",
      }});
    }
    if (path === "/.well-known/assetlinks.json") {
      const rawFingerprint = String(env.ANDROID_SHA256_CERT_FINGERPRINT || "").replace(/:/g, "").toUpperCase();
      if (!/^[A-F0-9]{64}$/.test(rawFingerprint)) {
        return new Response("Android App Links ainda não foi configurado.", {
          status: 503,
          headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
        });
      }
      const fingerprint = rawFingerprint.match(/.{2}/g).join(":");
      return new Response(JSON.stringify([{
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "com.pimsg.isabstudio",
          sha256_cert_fingerprints: [fingerprint],
        },
      }]), {
        headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
      });
    }
    if (path === "/") {
      return html("PiMsg", `<a class="btn w" href="${PLAY_URL}">Baixar na Play Store</a>`);
    }
    const key = path.slice(1);
    if (LEGAL[key]) return LEGAL[key](env);
    if (path === "/support") return supportPage(env);
    if (path === "/delete-account") return deletePage();
    const g = path.match(/^\/g\/([A-Za-z0-9]{4,12})$/);
    if (g) return groupPage(env, g[1]);
    const u = path.match(/^\/u\/([a-z0-9]{6,16})$/);
    if (u) return linkPage(env, u[1]);
  }
  if (method === "POST") {
    const r = path.match(/^\/u\/([a-z0-9]{6,16})\/request$/);
    if (r) return requestCode(request, env, r[1]);
    if (path === "/delete-account") return deleteRequest(request, env);
  }
  return null;
}
