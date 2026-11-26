# PiLink — links inteligentes do PiMsg

O PiLink preserva o destino de links personalizados e convites de grupos quando a pessoa ainda não tem o PiMsg instalado.

## Fluxos suportados

- Link personalizado `/u/{slug}`: se o PiMsg estiver instalado, o Android abre a app e o utilizador autenticado é encaminhado para a conversa com o proprietário do link. Se ainda não tiver a app, a página web cria um pedido de contacto e o botão da Play Store transporta o código desse pedido pelo Google Play Install Referrer.
- Convite de grupo `/g/{codigo}`: a Play Store recebe o código do grupo e, na primeira abertura depois da instalação, o PiMsg recupera o convite e entra no grupo. Por privacidade, o histórico anterior fica oculto para novos membros.
- Depois da instalação, o destino é recuperado uma única vez, guardado localmente enquanto a pessoa conclui a autenticação e consumido quando a conversa abre.

## Infraestrutura

- Cloudflare Worker: páginas públicas, criação/validação dos pedidos e resolução autenticada de links.
- D1 existente: reutiliza `contact_links`, `contact_requests`, `users` e `conversations`. Esta implementação não exige uma nova migração D1.
- Android: Android App Links e Google Play Install Referrer 2.2.

## Configuração obrigatória para Android App Links

No Cloudflare Worker `pimsg-api`, criar a variável de ambiente:

`ANDROID_SHA256_CERT_FINGERPRINT`

Valor: SHA-256 do certificado de assinatura da app publicado na Google Play. Obter em Google Play Console → PiMsg → Integridade da app → Certificado da chave de assinatura da app → SHA-256. Pode colar o valor com ou sem dois-pontos; o Worker normaliza-o.

Depois de configurar, confirmar que:

`https://pimsg.tchilopop.com/.well-known/assetlinks.json`

devolve HTTP 200 e o pacote `com.pimsg.isabstudio` com a impressão digital correta. Enquanto a variável não estiver configurada, o endpoint devolve HTTP 503 e a verificação automática de Android App Links não ficará ativa.

## Testes

1. Publicar uma versão numa faixa de teste interno/fechado da Google Play. O Install Referrer não é entregue em instalações por APK sideload.
2. Num aparelho sem PiMsg, abrir um link `/u/{slug}`, escrever o nome, gerar o código e tocar em Baixar na Play Store.
3. Instalar pela Play Store e abrir o PiMsg. Depois da autenticação, deve abrir a conversa com o proprietário do link.
4. Repetir com `/g/{codigo}`; deve entrar no grupo, sem mostrar histórico anterior.
5. Com PiMsg já instalado, abrir diretamente `/u/{slug}` e `/g/{codigo}`; os Android App Links devem encaminhar para a app depois da associação do domínio estar verificada.

## Limites

A instalação não inicia automaticamente a app. O Android/Google Play não oferece uma forma fiável de iniciar uma app imediatamente no fim da instalação. O PiMsg recupera o destino na primeira abertura, depois da autenticação. O código de pedido de contacto mantém a validade existente de sete dias.
