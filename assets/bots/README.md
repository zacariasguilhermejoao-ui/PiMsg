# PiMsg bot visual assets

- `pi_bot.png`: ícone estático oficial do PiBot.
- `bot_working.svg`: arte SVG fornecida para o estado de trabalho/animação.

O PNG também é copiado para `android/app/src/main/res/drawable-nodpi/pi_bot.png` e exposto no Compose pelo componente reutilizável `PiBotAvatar` em `ui/Components.kt`.

O SVG animado é mantido como asset-fonte. O Android Drawable nativo não executa animações CSS/keyframes embutidas em SVG; a animação visual ainda precisa de um renderizador compatível antes de ser mostrada na interface. Esta alteração não cria ainda um ecrã de bots nem altera fluxos existentes.
