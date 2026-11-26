# PiMsg bot visual assets

- `pi_bot.png`: ícone estático oficial do PiBot.
- `bot_working.svg`: SVG animado original fornecido, preservado como asset-fonte.

O PNG é copiado para `android/app/src/main/res/drawable-nodpi/pi_bot.png` e disponibilizado no Compose pelo componente reutilizável `PiBotAvatar` em `ui/Components.kt`.

O ecrã `PiBotScreen` está acessível pelo menu da aplicação. Como o Android Drawable não executa as animações CSS/keyframes do SVG, o ecrã usa uma pré-visualização de movimento e pulsação nativa em Jetpack Compose. O SVG original permanece preservado para uma integração futura com renderizador compatível.

A interface deixa explícito que o serviço de IA ainda não está ligado. Esta etapa não altera a API, o Cloudflare Worker, o banco de dados, a autenticação ou os fluxos de mensagens.
