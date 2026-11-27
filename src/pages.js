
import { pushToUsers } from "./push.js";

const PLAY_URL = "https://play.google.com/store/apps/details?id=com.pimsg.isabstudio";
const LEGAL_VERSION = "1.3";
const LEGAL_DATE = "27 de novembro de 2026";
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const code = (n) => Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => ALPHABET[b % ALPHABET.length]).join("");
const jsonRes = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });

const CSS = `:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#000;color:#fff;font:16px/1.65 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}main{max-width:680px;margin:0 auto;padding:32px 22px 64px}.brand{font-weight:700;font-size:30px;letter-spacing:-.5px;margin-bottom:28px}h1{font-size:26px;line-height:1.25;margin:0 0 10px}h2{font-size:18px;margin:28px 0 6px}p,li{color:#c9c9ce}ol,ul{padding-left:22px}a{color:#fff}.card{background:#17171a;border-radius:18px;padding:22px;margin:20px 0}.code{font:700 30px/1.2 ui-monospace,monospace;letter-spacing:4px;text-align:center;padding:16px;background:#232327;border-radius:14px;margin:14px 0}.btn{display:block;width:100%;text-align:center;border:0;border-radius:28px;padding:15px 20px;font:600 16px system-ui,sans-serif;text-decoration:none;cursor:pointer;margin-top:12px}.w{background:#fff;color:#000}.g{background:#232327;color:#fff}input{width:100%;background:#000;color:#fff;border:1px solid #3a3a3f;border-radius:14px;padding:14px;font:16px system-ui,sans-serif}.muted{color:#8e8e93;font-size:14px}.err{color:#e5484d}footer{margin-top:40px;font-size:14px;color:#8e8e93}footer a{color:#8ab4f8;margin-right:14px;display:inline-block;text-decoration:underline;text-underline-offset:3px}.profile{display:flex;flex-direction:column;align-items:center;text-align:center;gap:8px;margin:8px 0 22px}.avatar{width:104px;height:104px;border-radius:50%;object-fit:cover;background:#232327}.link-photo{width:100%;max-height:300px;object-fit:cover;border-radius:16px;margin:14px 0}`;

function html(title, body, status = 200, footerLinks = ["terms", "privacy", "community", "support"]) {
  const labels = { terms: "Termos de uso", privacy: "Privacidade", refund: "Reembolsos", community: "Regras da comunidade", subscriptions: "Subscrições", cookies: "Cookies", faq: "Perguntas frequentes", support: "Suporte", security: "Segurança", "child-safety": "Segurança de menores", copyright: "Direitos de autor", "delete-account": "Apagar conta" };
  const footer = footerLinks.map((key) => `<a href="/${key}">${labels[key]}</a>`).join("");
  const page = `<!doctype html><html lang="pt"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)} | PiMsg</title><style>${CSS}</style></head><body><main><div class="brand">PiMsg</div>${body}<footer>${footer}<p>ISAB Group</p></footer></main></body></html>`;
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
  return html(title, `<h1>${esc(title)}</h1><p class="muted">Versão ${LEGAL_VERSION}, atualizada em ${LEGAL_DATE}</p><p class="muted">O PiMsg é um serviço de mensagens operado sob a marca ISAB Group.</p><p>${intro}</p>${body}${contactsBlock()}`);
}

const LEGAL = {
  terms: (env) => legalDoc("Termos de uso", "Estes termos regulam o uso do PiMsg, um serviço de mensagens operado sob a marca ISAB Group. Ao criar conta ou usar o app, aceita estes termos.", [
    ["1. Conta e idade mínima", "<p>O PiMsg destina-se a pessoas com pelo menos 16 anos. O utilizador deve fornecer informações verdadeiras, proteger os seus meios de acesso e manter os dados da conta actualizados. A entrada pode ser feita com Google ou com uma conta criada por número de telefone; o número digitado como contacto não é, por si só, uma confirmação de identidade nem um método de recuperação da conta.</p>"],
    ["2. Planos e limites", "<p>O PiMsg disponibiliza os planos Free, Plus, Pro e Business. Os limites, funcionalidades, quotas e preços aplicáveis são apresentados no ecrã de subscrição antes da compra e podem variar por país, moeda, impostos e alterações devidamente comunicadas. A disponibilidade de cada funcionalidade depende do plano activo e das condições técnicas do serviço.</p>"],
    ["3. Uso permitido", "<p>Pode usar o PiMsg para conversar e para vender e negociar de forma honesta. É proibido:</p><ul><li>enganar ou aplicar golpes, incluindo vender o que não existe;</li><li>enviar spam ou mensagens em massa a quem não as pediu;</li><li>assediar, ameaçar ou discriminar;</li><li>partilhar conteúdo ilegal, violento ou sexual envolvendo menores, ou conteúdo sexual sem consentimento;</li><li>fazer-se passar por outra pessoa;</li><li>contornar os limites do app com contas falsas ou automatismos.</li></ul>"],
    ["4. Moderação", "<p>Qualquer pessoa pode bloquear e denunciar utilizadores, mensagens, grupos e stories. Analisamos as denúncias e podemos remover conteúdo, avisar, suspender temporariamente ou banir contas que quebrem estas regras. Quem for sancionado pode pedir revisão através do suporte. Os detalhes estão nas Regras da comunidade.</p>"],
    ["5. O seu conteúdo", "<p>O conteúdo que envia continua a ser seu. Dá-nos apenas a permissão necessária para o guardar, transmitir e mostrar aos destinatários que escolheu. É responsável pelo que envia e por ter direito a enviá-lo.</p>"],
    ["6. Negociações entre utilizadores", "<p>O PiMsg é uma ferramenta de conversa. Não somos parte nas vendas, pagamentos ou acordos feitos entre utilizadores e não garantimos a qualidade, a entrega ou a identidade de quem vende ou compra. Tenha cuidado com pagamentos adiantados.</p>"],
    ["7. Disponibilidade e alterações", "<p>Trabalhamos para manter o serviço disponível, mas podem existir interrupções. Podemos atualizar estes termos e, se a mudança for importante, avisamos no app.</p>"],
    ["8. Reclamações e contacto", "<p>Para questões de conta, suporte, denúncias ou privacidade, use os contactos oficiais apresentados no fim desta página. As reclamações serão registadas e analisadas pela equipa responsável.</p>"],
    ["9. Lei aplicável", "<p>Estes termos regem-se pela lei aplicável na República de Angola, sem prejuízo dos direitos imperativos dos consumidores no seu país.</p>"],
  ], env),

  privacy: (env) => legalDoc("Política de privacidade", "Esta política explica que dados o PiMsg, serviço de mensagens operado sob a marca ISAB Group, recolhe, para que serve e quais são os seus direitos.", [
    ["1. Dados que recolhemos", "<ul><li>Conta e autenticação: identificador Firebase, nome, endereço de e-mail e estado de verificação quando fornecidos pelo fornecedor de autenticação; em contas criadas por número, o número indicado pode ser guardado como contacto não verificado. O PiMsg não deve apresentar esse número como verificado sem confirmação efectiva.</li><li>Perfil: nome, apresentação, foto, vídeo de apresentação e o código pessoal que gerar. O número de telefone só aparece no seu perfil se escolher mostrá-lo, e ninguém encontra outra pessoa pesquisando pelo número.</li><li>Conteúdo: mensagens, fotos, vídeos, áudios, ficheiros e stories que envia.</li><li>Dispositivo: identificador do aparelho, token de notificações e dados técnicos, usados para segurança e para limitar contas falsas.</li><li>Uso: contagens de mensagens e conversas necessárias para aplicar os limites dos planos, e registos de compras feitas na Google Play.</li><li>Denúncias e moderação: denúncias que faz ou recebe e as ações tomadas.</li></ul>"],
    ["2. Para que usamos", "<p>Para prestar o serviço, entregar mensagens e notificações, aplicar os planos e limites, prevenir fraude e abuso, tratar denúncias, cumprir obrigações legais e melhorar o app.</p>"],
    ["3. Mensagens", "<p>As mensagens e ficheiros são processados e armazenados nos servidores para permitir a entrega, sincronização e funcionamento do serviço. Na versão actual, as conversas não dispõem de cifragem de ponta a ponta. O acesso interno é limitado a situações justificadas, como suporte, segurança, investigação de denúncias ou cumprimento de obrigações legais, com controlos de acesso.</p>"],
    ["4. Com quem partilhamos", "<p>Usamos prestadores que processam dados por nossa conta: Google (Firebase Authentication, Google Sign-In, Firebase Cloud Messaging e Google Play Billing) e Cloudflare (Workers, base de dados D1, armazenamento R2 e serviços de segurança/entrega). Estes fornecedores podem processar dados em infraestruturas localizadas fora de Angola, conforme os seus serviços e contratos. Podemos divulgar dados quando a lei o exigir. Não vendemos os seus dados.</p>"],
    ["5. Quanto tempo guardamos", "<p>Guardamos os dados enquanto a conta existir. Os stories expiram ao fim de 24 horas. Quando um pedido de eliminação é executado, removemos ou anonimizamos os dados pessoais abrangidos, excepto o que for necessário manter por motivos legais ou de segurança, como registos de denúncias e de compras, durante o tempo estritamente necessário.</p>"],
    ["6. Os seus direitos", "<p>Pode aceder, corrigir e pedir a eliminação dos seus dados, e pedir uma cópia deles. Pode apagar a conta no app, em Menu, Definições, Apagar conta, ou pelo formulário em /delete-account. Os pedidos são tratados e os dados são eliminados ou anonimizados de acordo com os prazos legais, necessidades de segurança, prevenção de fraude e obrigações de conservação. O prazo operacional apresentado no processo de eliminação não substitui os direitos legais do titular.</p>"],
    ["7. Menores", "<p>O PiMsg não é destinado a menores de 16 anos. Se souber de uma conta de um menor dessa idade, contacte-nos para a removermos.</p>"],
    ["8. Segurança e transferências", "<p>Usamos ligações cifradas e controlo de acessos. Os prestadores indicados podem processar dados fora do seu país, com as proteções que oferecem. Nenhum sistema é totalmente seguro.</p>"],
    ["9. Direitos e pedidos", "<p>Para exercer direitos de acesso, retificação, oposição ou eliminação, contacte privacidade@tchilopop.com. Para questões de acesso à conta, contacte contas@tchilopop.com. Para denunciar conteúdo ou comportamento, use denuncia@tchilopop.com.</p>"],
    ["10. Alterações", "<p>Se esta política mudar de forma relevante, avisamos no app.</p>"],
  ], env),

  refund: (env) => legalDoc("Política de reembolso", "Esta política aplica-se às subscrições digitais Plus, Pro e Business adquiridas através da Google Play.", [
    ["1. Pagamentos pela Google Play", "<p>As compras digitais feitas na aplicação são processadas pela Google Play. O pedido de reembolso deve ser submetido na Google Play, através do histórico de encomendas da conta Google utilizada na compra. A decisão e o processamento do reembolso seguem as regras da Google Play e a legislação de defesa do consumidor aplicável.</p>"],
    ["2. Subscrições", "<p>As subscrições Plus, Pro e Business podem ser semanais, mensais ou anuais, conforme o produto apresentado. Quando o produto indicar renovação automática, a Google Play cobra novamente no início de cada período até ao cancelamento. Pode cancelar em Google Play &gt; Pagamentos e subscrições &gt; Subscrições. O cancelamento impede cobranças futuras, mas normalmente mantém o acesso até ao fim do período já pago. A elegibilidade para reembolso é avaliada pela Google Play e pela legislação aplicável; esta política não elimina direitos legais do consumidor.</p>"],
    ["3. Compras não activadas", "<p>Se a Google Play confirmar a cobrança mas o plano não for activado, contacte contas@tchilopop.com com o ID da encomenda, o produto e a data da compra. Não envie palavras-passe, códigos de autenticação ou dados completos do cartão.</p>"],
    ["4. Efeito do reembolso", "<p>Quando um reembolso é aprovado, os benefícios associados à compra podem ser revogados ou ajustados para reflectir a devolução, respeitando a legislação aplicável e os direitos do consumidor.</p>"],
    ["5. Segurança", "<p>Inclua apenas os dados necessários para localizar a encomenda. Nunca envie a palavra-passe da sua conta Google, códigos de verificação ou dados completos do cartão.</p>"],
    ["6. Prazos e direitos legais", "<p>Os prazos de análise e processamento dependem da Google Play e do método de pagamento. Nada nesta política limita direitos de reembolso ou garantia que sejam obrigatórios segundo a legislação aplicável.</p>"],
  ], env),

  community: (env) => legalDoc("Regras da comunidade", "O PiMsg existe para facilitar conversas e vendas honestas. Estas regras mantêm o espaço seguro para todos.", [
    ["O que não é permitido", "<ul><li>Golpes, produtos falsos e pedidos de pagamento enganosos.</li><li>Spam e mensagens em massa para quem não pediu contacto.</li><li>Assédio, ameaças, ódio e discriminação.</li><li>Conteúdo sexual envolvendo menores, ou conteúdo sexual sem consentimento, e imagens de violência extrema.</li><li>Venda de produtos ilegais ou perigosos.</li><li>Roubar a identidade de outra pessoa e criar contas falsas para contornar limites.</li></ul>"],
    ["Como denunciar e bloquear", "<p>Toque na pessoa, mensagem, grupo ou story e escolha Denunciar ou Bloquear. Quem é bloqueado deixa de poder escrever-lhe e de ver os seus stories.</p>"],
    ["O que fazemos", "<p>Analisamos as denúncias. Conforme a gravidade, removemos o conteúdo, enviamos um aviso, suspendemos a conta por algum tempo ou banimos. Casos graves, como abuso de menores, levam a banimento imediato e podem ser comunicados às autoridades.</p>"],
    ["Pedir revisão", "<p>Se acha que foi sancionado por engano, contacte o suporte e reveremos o caso.</p>"],
  ], env),

  subscriptions: (env) => legalDoc("Política de subscrições", "Esta política explica o funcionamento dos planos pagos do PiMsg, incluindo cobrança, renovação, cancelamento e gestão de benefícios.", [
    ["1. Planos e informação prévia", "<p>Os planos disponíveis podem incluir Plus, Pro e Business. Antes de confirmar a compra, a Google Play apresenta o preço, a moeda, o período, as condições de renovação e a conta Google que será cobrada. O utilizador deve confirmar esses dados no ecrã oficial de compra.</p>"],
    ["2. Renovação automática", "<p>Quando uma subscrição é identificada como renovável, será renovada automaticamente no fim do período, salvo cancelamento antes da próxima cobrança. A data e as condições exactas são as indicadas pela Google Play.</p>"],
    ["3. Cancelamento", "<p>O cancelamento é feito na Google Play, em Pagamentos e subscrições. Desinstalar o PiMsg ou terminar sessão não cancela uma subscrição. Após cancelar, os benefícios normalmente permanecem até à data de expiração já paga, salvo regras diferentes da Google Play ou da lei aplicável.</p>"],
    ["4. Conta e email", "<p>Para iniciar uma compra no PiMsg é necessário associar um endereço de e-mail à conta e confirmar esse endereço. O servidor verifica o estado de confirmação recebido do Firebase Authentication antes de aceitar a validação da compra. O número de telefone indicado como contacto não substitui esta confirmação.</p>"],
    ["5. Mudança de plano", "<p>As alterações de plano ou período podem depender das opções de gestão apresentadas pela Google Play. Não inicie uma segunda subscrição para substituir outra activa sem verificar primeiro as opções de alteração, para evitar cobranças sobrepostas.</p>"],
    ["6. Falhas, pagamentos e reembolsos", "<p>As compras são validadas no servidor com a Google Play. Se houver cobrança sem activação, contacte contas@tchilopop.com com o ID da encomenda. Os pedidos de reembolso são tratados segundo as regras da Google Play e os direitos obrigatórios do consumidor.</p>"],
    ["7. Alterações dos benefícios", "<p>Os limites e benefícios são apresentados no app antes da adesão. Alterações relevantes serão comunicadas com antecedência razoável e não eliminam direitos já adquiridos para um período pago, salvo fundamento legal.</p>"],
  ], env),

  cookies: (env) => legalDoc("Política de cookies e tecnologias semelhantes", "Esta página descreve a utilização de cookies e tecnologias semelhantes nas páginas públicas do PiMsg.", [
    ["1. Cookies essenciais", "<p>As páginas públicas podem utilizar tecnologias estritamente necessárias para segurança, prevenção de abuso, funcionamento e entrega do serviço, incluindo mecanismos do fornecedor de infraestrutura. Se forem activados mecanismos de desafio ou gestão de sessão, estes podem definir identificadores técnicos essenciais.</p>"],
    ["2. Analítica e publicidade", "<p>Esta versão das páginas públicas não declara a utilização de cookies publicitários nem de rastreio para publicidade comportamental. Se forem introduzidas ferramentas de analítica, publicidade ou medição não essenciais, esta política e os mecanismos de consentimento serão actualizados antes da sua activação quando exigido por lei.</p>"],
    ["3. Gestão", "<p>O utilizador pode gerir ou bloquear cookies nas definições do navegador. O bloqueio de tecnologias estritamente necessárias pode impedir algumas páginas ou mecanismos de segurança de funcionarem correctamente.</p>"],
  ], env),

  faq: (env) => legalDoc("Perguntas frequentes", "Respostas práticas sobre conta, contactos, mensagens, segurança, subscrições e privacidade no PiMsg.", [
    ["Como crio uma conta?", "<p>Pode continuar com Google ou iniciar a criação de conta com um número de telefone. O número digitado como contacto não é confirmado por SMS e não deve ser tratado como número verificado.</p>"],
    ["O número de telefone é verificado?", "<p>Não. Quando é apenas indicado no formulário, fica guardado como contacto. Não afirmamos que pertence ao utilizador nem que foi confirmado por SMS.</p>"],
    ["Como mantenho acesso à conta noutro telemóvel?", "<p>Associe e confirme um e-mail na área Perfil, em E-mail e verificação, ou utilize a mesma conta Google. Uma conta anónima sem método de autenticação recuperável pode não ser recuperável noutro dispositivo.</p>"],
    ["Como confirmo o meu e-mail?", "<p>Abra Perfil, introduza o seu e-mail e toque em Enviar confirmação. Abra a ligação recebida e depois volte ao PiMsg e escolha Já confirmei o e-mail.</p>"],
    ["Porque preciso de e-mail verificado para pagar?", "<p>A confirmação ajuda a associar a subscrição a uma conta recuperável. O PiMsg só permite iniciar compras depois de o servidor confirmar o estado de e-mail verificado.</p>"],
    ["Quais são os planos?", "<p>Existem Free, Plus, Pro e Business. Os limites e funcionalidades actuais aparecem em Menu &gt; Subscrições. O preço final e o período são sempre mostrados pela Google Play antes de confirmar.</p>"],
    ["Como cancelo uma subscrição?", "<p>Abra Google Play, toque no perfil, entre em Pagamentos e subscrições, depois Subscrições, escolha PiMsg e cancele. Apagar o app não cancela pagamentos.</p>"],
    ["Como peço um reembolso?", "<p>Use o histórico de encomendas ou o fluxo de reembolso da Google Play. Se pagou e o plano não foi activado, contacte contas@tchilopop.com com o ID da encomenda.</p>"],
    ["As mensagens têm cifragem de ponta a ponta?", "<p>Não nesta versão. As mensagens são transmitidas e armazenadas nos servidores do serviço. Não envie palavras-passe, dados bancários ou informação extremamente sensível nas conversas.</p>"],
    ["Como bloqueio ou denuncio alguém?", "<p>Abra o perfil ou as opções da conversa e escolha Bloquear ou Denunciar. Para denúncias urgentes, escreva para denuncia@tchilopop.com.</p>"],
    ["Como apago a minha conta?", "<p>Use Menu &gt; Definições &gt; Apagar conta ou visite /delete-account. A equipa poderá solicitar confirmação adicional da identidade. Alguns registos poderão ser conservados quando a lei o exigir.</p>"],
    ["Porque o PiMsg pede acesso ao microfone?", "<p>O microfone é necessário para gravar mensagens de voz. O Android apresenta o pedido nativo quando tenta utilizar essa função. Pode negar a permissão e continuar a usar as mensagens de texto.</p>"],
    ["Porque não aparece pedido para aceder às fotografias?", "<p>O PiMsg utiliza o selector de fotografias e ficheiros do Android. Esse selector permite escolher apenas os ficheiros pretendidos sem conceder acesso geral à galeria.</p>"],
    ["Como contacto o suporte?", "<p>Escreva para suporte@tchilopop.com. Para privacidade, utilize privacidade@tchilopop.com; para pagamentos, contas@tchilopop.com.</p>"],
  ], env),

  security: (env) => legalDoc("Segurança e reporte de vulnerabilidades", "A segurança das contas, mensagens e ficheiros é uma prioridade operacional do PiMsg. Esta página explica como reportar problemas de segurança.", [
    ["1. Como reportar", "<p>Se identificar uma vulnerabilidade, acesso indevido, exposição de dados ou falha que possa afectar outros utilizadores, contacte suporte@tchilopop.com com o assunto “Reporte de segurança”. Inclua passos para reproduzir o problema e evidências não sensíveis. Não publique dados pessoais nem explore contas de terceiros.</p>"],
    ["2. Tratamento", "<p>Os relatos serão avaliados pela equipa responsável. Podemos pedir detalhes adicionais, limitar temporariamente funcionalidades afectadas e aplicar correcções. Não garantimos um prazo de resposta sem avaliação da gravidade e dos recursos disponíveis.</p>"],
    ["3. Responsabilidade do utilizador", "<p>Use uma palavra-passe segura no fornecedor de autenticação, mantenha o dispositivo actualizado e não partilhe códigos de verificação. O PiMsg nunca deve pedir a sua palavra-passe Google nem códigos de autenticação por mensagem.</p>"],
    ["4. Limitações conhecidas", "<p>Na versão actual, as conversas não têm cifragem de ponta a ponta. A transmissão usa ligações protegidas, mas os conteúdos são processados nos servidores do serviço para entrega e armazenamento.</p>"],
  ], env),

  "child-safety": (env) => legalDoc("Política de segurança de menores", "O PiMsg destina-se a pessoas com pelo menos 16 anos e não foi concebido para utilização por crianças abaixo dessa idade.", [
    ["1. Conteúdo proibido", "<p>É estritamente proibido criar, solicitar, guardar, partilhar ou promover exploração sexual de menores, aliciamento, abuso, extorsão sexual, tráfico, ou qualquer conteúdo que sexualize menores. Também são proibidas ameaças, assédio e tentativas de obter dados pessoais de menores para fins abusivos.</p>"],
    ["2. Denúncia", "<p>Denuncie contas, mensagens, grupos ou stories através das ferramentas do PiMsg. Para situações relacionadas com segurança de menores, contacte suporte@tchilopop.com com o assunto “Segurança de menores”. Não reenvie nem descarregue material ilegal para o incluir numa denúncia.</p>"],
    ["3. Medidas", "<p>Podemos remover conteúdo, restringir funcionalidades, suspender ou encerrar contas e preservar informações relevantes quando necessário para investigar uma denúncia ou cumprir a lei. Quando legalmente exigido, poderemos comunicar casos às autoridades competentes.</p>"],
    ["4. Utilizadores menores", "<p>Se acredita que uma pessoa abaixo da idade mínima criou uma conta, contacte o suporte para análise. Não tente investigar ou confrontar a pessoa por conta própria.</p>"],
  ], env),

  copyright: (env) => legalDoc("Política de propriedade intelectual e direitos de autor", "O PiMsg respeita os direitos de autor, marcas e outros direitos de propriedade intelectual. Os utilizadores devem publicar apenas conteúdos que tenham direito de utilizar.", [
    ["1. Conteúdo dos utilizadores", "<p>O utilizador mantém os direitos sobre o conteúdo que cria. Ao enviar conteúdo, concede ao PiMsg apenas a autorização técnica necessária para alojar, processar, transmitir e apresentar esse conteúdo no âmbito do serviço e às pessoas que escolheu.</p>"],
    ["2. Conteúdo de terceiros", "<p>Não publique fotografias, vídeos, música, marcas, textos ou outros materiais de terceiros sem autorização ou outra base legal válida. O utilizador é responsável por assegurar que tem os direitos necessários.</p>"],
    ["3. Como apresentar uma reclamação", "<p>Envie para suporte@tchilopop.com uma descrição da obra protegida, a localização do conteúdo no PiMsg, os seus dados de contacto, a prova de titularidade ou autorização para agir e uma declaração de boa-fé de que a utilização contestada não foi autorizada. Não inclua dados pessoais desnecessários.</p>"],
    ["4. Análise e medidas", "<p>Analisaremos as reclamações e poderemos solicitar informações adicionais, limitar o acesso ao conteúdo ou remover material quando houver fundamento. Reclamações manifestamente falsas ou abusivas podem ser tratadas segundo as regras da comunidade e a lei aplicável.</p>"],
  ], env),
};

function supportPage(env) {
  return html("Suporte", `<h1>Suporte</h1><p>Escolha o contacto adequado para receber ajuda mais rapidamente.</p>${contactsBlock()}<h2>Perguntas frequentes</h2><p><a href="/faq">Ver todas as perguntas frequentes</a></p><p><b>Como encontro alguém?</b> Toque na lupa e escreva o nome, o código ou o código de convite de um grupo. O número de telefone não serve para pesquisar.</p><p><b>Como mudo o meu código?</b> Em Perfil, toque em Gerar novo código. O código anterior deixa de valer.</p><p><b>Como cancelo o plano Pro?</b> Na Google Play, em Pagamentos e subscrições.</p><p><b>Como apago a conta?</b> No app, em Menu, Definições, Apagar conta, ou em <a href="/delete-account">pimsg.tchilopop.com/delete-account</a>. Para pedidos sobre a conta, escreva para contas@tchilopop.com.</p>`);
}

function deletePage() {
  return html("Apagar conta", `<h1>Apagar conta</h1><p>Pode solicitar a eliminação da sua conta PiMsg e dos dados associados. A equipa poderá pedir informações adicionais para confirmar que o pedido é legítimo e informará o estado e o prazo aplicável.</p><p>Se ainda tem acesso ao app, o caminho mais rápido é Menu, Definições, Apagar conta.</p><div class="card"><p>Se não consegue entrar, indique o e-mail ou número usado na conta e pedimos a verificação por esse meio.</p><input id="c" placeholder="E-mail ou número da conta" autocomplete="off"><button class="btn w" id="b">Pedir eliminação</button><p id="m" class="muted"></p></div><script>document.getElementById('b').onclick=function(){var v=document.getElementById('c').value.trim(),m=document.getElementById('m');if(v.length<5){m.textContent='Indique o e-mail ou número da conta.';m.className='err';return}fetch('/delete-account',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({contact:v})}).then(function(r){return r.json()}).then(function(j){m.className=j.ok?'muted':'err';m.textContent=j.ok?'Pedido recebido. Vamos confirmar consigo pelo contacto indicado.':(j.error||'Não foi possível enviar o pedido.')}).catch(function(){m.className='err';m.textContent='Não foi possível enviar o pedido.'})}</script>`);
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
  return html(l.display_name, `<div class="profile">${avatar}<h1>Converse com ${esc(l.display_name)}</h1></div><p>Assunto: <b>${esc(l.subject)}</b></p>${photo}${l.intro_text ? `<p>${esc(l.intro_text)}</p>` : ""}<div class="card" id="box"><p>Escreva o seu nome para gerar o código de conversa.</p><input id="n" placeholder="O seu nome" maxlength="60" autocomplete="name"><button class="btn w" id="go">Gerar código</button><p id="e" class="err"></p></div><h2>Como falar</h2>${steps}${COPY_JS}<script>document.getElementById('go').onclick=function(){var n=document.getElementById('n').value.trim(),e=document.getElementById('e');if(n.length<2){e.textContent='Escreva o seu nome.';return}fetch('/u/${slug}/request',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({name:n})}).then(function(r){return r.json()}).then(function(j){if(!j.code){e.textContent=j.error||'Não foi possível gerar o código.';return}document.getElementById('box').innerHTML='<p>O seu código de conversa</p><div class="code" id="cd">'+j.code+'</div><button class="btn g" onclick="copyCode(\\'cd\\',this)">Copiar código</button><a class="btn w" href="${PLAY_URL}&referrer='+encodeURIComponent('pimsg_type=contact&pimsg_code='+j.code)+'">Baixar na Play Store</a><p class="muted">O código vale por 7 dias.</p>'}).catch(function(){e.textContent='Não foi possível gerar o código.'})}</script>`, 200, ["terms", "privacy", "support"]);
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
  ).bind(crypto.randomUUID(), contact, now, null).run();
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
           EXISTS (SELECT 1 FROM users u WHERE (u.avatar_key = m.id OR u.intro_video_key = m.id OR u.profile_cover_key = m.id) AND u.status = 'active')
           OR EXISTS (SELECT 1 FROM contact_links l WHERE l.image_media_id = m.id AND l.is_active = 1)
         )`
      ).bind(publicMediaId).first();
      if (!media || !(String(media.mime).startsWith("image/") || String(media.mime).startsWith("video/"))) return new Response("Media nao encontrada", { status: 404 });
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
