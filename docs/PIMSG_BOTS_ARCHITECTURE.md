# PiMsg — Arquitetura do ecossistema de bots

Estado: proposta técnica inicial, ainda sem alterações no Worker de produção ou na base de dados.

## Decisões confirmadas

- O ecossistema de bots e IA deve usar exclusivamente produtos Cloudflare; não integrar Gemini, OpenAI, Groq ou outro fornecedor de IA.
- Pi Games está fora do âmbito.
- Pi Translate é gratuito, automático e mostra um indicador discreto `TR · Traduzido` junto da mensagem traduzida.
- Pi AI é o assistente geral do PiMsg.
- Pi Business é orientado ao atendimento de empresas.
- Pi Reminder cria lembretes e envia notificações.
- Pi Bots permite criar bots especializados.
- Pi Bots Marketplace permite descobrir, instalar e publicar bots.
- Não remover nem substituir as funções atuais do PiMsg.

## Arquitetura proposta

### 1. Motor de IA comum

Usar Cloudflare Workers AI através de um binding `AI` no Worker `pimsg-api`. Pi AI, Pi Business e bots especializados usam o mesmo serviço de inferência, com perfis, instruções e permissões diferentes. Não criar integrações com fornecedores externos.

A primeira versão deve usar chamadas diretas ao binding Workers AI e histórico controlado em D1. Não introduzir o Agents SDK/Durable Objects sem necessidade comprovada de estado em tempo real ou execução longa; isso adicionaria novas classes, bindings e migrações.

### 2. Dados em D1

Criar, depois da reconciliação do esquema de produção, tabelas próprias para:
- catálogo de bots e respetivos criadores;
- versões e estado de publicação;
- instalações dos utilizadores;
- sessões e histórico de conversas com bots;
- tarefas/lembretes agendados;
- registo de utilização e consumo de IA.

Não reutilizar a tabela `messages` para conversas de bots sem uma decisão explícita de compatibilidade: o seu `CHECK` atual limita os tipos de mensagem.

### 3. Pi Reminder

Guardar lembretes em D1 e processá-los com Cron Triggers no Worker. A notificação deve seguir o mecanismo de push que o PiMsg já utiliza, sem criar um fornecedor adicional. O processamento deve ser idempotente para evitar notificações duplicadas.

### 4. Pi Bots e Marketplace

Na primeira versão, os bots são configurações de instruções e ferramentas aprovadas, não código arbitrário enviado por utilizadores. Cada ferramenta deve ter permissões explícitas e validação no servidor. Um bot não recebe acesso direto à base de dados nem às credenciais do Worker.

O Marketplace começa com catálogo, pesquisa, detalhes e instalação. Cobranças, comissões e pagamentos a criadores ficam para uma fase posterior, depois de serem definidas as regras comerciais e a integração de pagamentos.

### 5. Pi Business

Começar com perfis empresariais e respostas baseadas em informação fornecida pelo próprio negócio. Não permitir que o bot invente preços, disponibilidade, políticas ou dados de clientes. Ações como criar pedidos ou alterar dados exigem ferramentas específicas e autorização do utilizador.

### 6. Pi Translate

A tradução continua gratuita e automática, independente do plano Pro. Usar Workers AI, guardar em cache resultados reutilizáveis e preservar o texto original. O indicador `TR · Traduzido` deve permitir perceber que a mensagem foi traduzida e, idealmente, consultar o original.

## Proteção de custos e abuso

- Medir utilização por utilizador e por funcionalidade.
- Aplicar limites de tamanho de entrada e saída.
- Definir limites de frequência e concorrência.
- Usar cache para traduções repetidas.
- Permitir apenas modelos aprovados e configurados no servidor.
- Registar falhas e consumo estimado sem guardar segredos.
- Ter limites de utilização configuráveis; não prometer IA ilimitada.
- Informar o utilizador quando um limite temporário for atingido.

Workers AI inclui uma alocação diária gratuita e cobra utilização acima dela nos planos elegíveis. A quota e o preço devem ser confirmados no painel antes de ativar funcionalidades para todos os utilizadores; não assumir que a utilização será sempre gratuita.

## Segurança e privacidade

- Todas as rotas privadas exigem autenticação Firebase já validada pelo Worker.
- Verificar a propriedade do bot e a instalação antes de executar ações.
- Não expor prompts privados, chaves, tokens ou configurações internas.
- Não permitir execução de JavaScript/código submetido por utilizadores na primeira versão.
- Aplicar limites e registos de auditoria às ações dos bots.
- Respeitar bloqueios, denúncias e estado da conta PiMsg.

## Ordem de implementação

1. Reconciliar o código atualmente publicado no Worker com os módulos do GitHub. O Worker de produção tem módulos e rotas adicionais em relação ao `main`; não publicar diretamente o `main` atual.
2. Confirmar bindings, secrets, rotas e esquema D1 sem alterar dados existentes.
3. Adicionar binding Workers AI e testes de inferência controlados.
4. Implementar serviço comum de IA, quotas e registo de utilização.
5. Implementar Pi AI e Pi Translate.
6. Implementar Pi Reminder com Cron Trigger e push idempotente.
7. Implementar criação, instalação e gestão de bots.
8. Implementar catálogo do Marketplace.
9. Implementar Pi Business e ferramentas empresariais aprovadas.
10. Integrar os ecrãs Android e validar o fluxo completo.
11. Só depois de testes e revisão, publicar uma versão sincronizada.

## Situação técnica verificada em 2026-11-26

- Worker de produção: `pimsg-api`.
- Bindings observados: `DB`, `MEDIA` e `FIREBASE_PROJECT_ID`.
- Não foi observado binding `AI` no Worker de produção.
- D1 já contém utilizadores, conversas, mensagens, grupos, Stories, subscrições, créditos e dispositivos.
- Ainda não foram identificadas tabelas de bots.
- O código de produção inclui `index.js`, `chat.js`, `extra.js`, `pages.js` e `push.js`; algumas versões diferem do `main` do GitHub.
- Nenhuma alteração de produção ou migração foi executada durante esta auditoria.

## Referências oficiais

- Workers AI modelos: https://developers.cloudflare.com/workers-ai/models/
- Workers AI preços: https://developers.cloudflare.com/workers-ai/platform/pricing/
- Agents SDK: https://developers.cloudflare.com/agents/
- Durable Objects: https://developers.cloudflare.com/durable-objects/
- Cron Triggers: https://developers.cloudflare.com/workers/configuration/cron-triggers/
