# PiMsg — preparação para a Google Play

## Identidade Android
- Application ID: `com.pimsg.isabstudio`
- Produto de subscrição semanal: `pimsg_pro_weekly`
- Produto de subscrição mensal: `pimsg_pro_monthly`

## Produtos que devem existir na Play Console
Em **Monetização > Produtos > Subscrições**, criar e ativar:
1. Produto `pimsg_pro_weekly`, com um base plan semanal (ID sugerido: `weekly`).
2. Produto `pimsg_pro_monthly`, com um base plan mensal (ID sugerido: `monthly`).

Definir os preços e países de disponibilidade na Play Console. A aplicação lê o preço localizado diretamente da Google Play; não colocar preços fixos no APK. Para a primeira publicação, manter apenas um base plan ativo por produto e sem ofertas promocionais, para que o botão selecione o plano esperado.

## Validação segura no servidor
O endpoint `POST /v1/billing/verify` valida o purchase token na Google Play Developer API antes de ativar o Pro. Não confiar no estado enviado pelo Android.

1. Ativar **Google Play Android Developer API** no projeto Google Cloud ligado à Play Console.
2. Criar uma service account nesse projeto e gerar uma chave JSON.
3. Na Play Console, em **Utilizadores e permissões**, adicionar o e-mail da service account e conceder acesso à aplicação PiMsg e permissões de consulta/gestão de subscrições necessárias para verificar e reconhecer compras.
4. Guardar o JSON completo como secret do Worker Cloudflare chamado `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`. Nunca colocar a chave no GitHub, no APK ou numa variável pública.
5. Fazer um teste de compra com uma conta de licença/teste da Play Console e confirmar que `/v1/plan` passa a devolver `pro_weekly` ou `pro_monthly`.

A aplicação também consulta as compras existentes ao abrir o ecrã Planos, para restaurar o acesso depois de reinstalar ou mudar de telemóvel. O backend associa cada purchase token a uma única conta PiMsg.

## Avaliações
A opção **Definições > Avaliação > Avaliar o PiMsg na Google Play** chama a Play In-App Review API. A Google decide se apresenta o diálogo, conforme os limites e a elegibilidade da conta. Se o pedido falhar, a aplicação abre a página da Play Store.

## Antes de produção
- Confirmar que ambos os produtos e base plans estão ativos e disponíveis nos países de lançamento.
- Adicionar a service account e o secret Cloudflare antes de aceitar pagamentos reais.
- Testar compra, cancelamento, renovação, período de tolerância, suspensão por pagamento e restauro usando contas de teste.
- Confirmar que o AAB é assinado com a mesma chave de upload usada para as atualizações futuras.
