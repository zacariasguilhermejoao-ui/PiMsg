package com.isabgroup.pimsg.ui

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

object PiI18n {
    private const val PREFS = "pimsg_preferences"
    private const val KEY_LANGUAGE = "app_language"
    var language by mutableStateOf("pt")
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        language = saved ?: when (context.resources.configuration.locales[0]?.language?.lowercase()) {
            "en" -> "en"
            "es" -> "es"
            "fr" -> "fr"
            else -> "pt"
        }
    }

    fun setLanguage(context: Context, value: String) {
        val normalized = value.lowercase().takeIf { it in setOf("pt", "en", "es", "fr") } ?: "pt"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, normalized).apply()
        language = normalized
    }

    fun languageName(code: String = language): String = when (code) {
        "en" -> "English"
        "es" -> "Español"
        "fr" -> "Français"
        else -> "Português"
    }

    fun translate(source: String): String {
        if (language == "pt") return source
        Regex("""^(\d+) / 500 respostas utilizadas este mês$""").matchEntire(source)?.let { match ->
            val count = match.groupValues[1]
            return when (language) {
                "en" -> "$count / 500 replies used this month"
                "es" -> "$count / 500 respuestas utilizadas este mes"
                "fr" -> "$count / 500 réponses utilisées ce mois-ci"
                else -> source
            }
        }
        if (source.startsWith("Respostas através dos links este mês: ")) {
            val value = source.substringAfter(": ")
            return when (language) {
                "en" -> "Replies through links this month: $value"
                "es" -> "Respuestas a través de enlaces este mes: $value"
                "fr" -> "Réponses via les liens ce mois-ci : $value"
                else -> source
            }
        }
        return translations[source]?.get(language) ?: source
    }

    private val translations = mapOf(
        "Definições" to mapOf("en" to "Settings", "es" to "Ajustes", "fr" to "Paramètres"),
        "Som e vibração" to mapOf("en" to "Sound and vibration", "es" to "Sonido y vibración", "fr" to "Son et vibration"),
        "Som ao tocar" to mapOf("en" to "Touch sounds", "es" to "Sonidos táctiles", "fr" to "Sons tactiles"),
        "Efeitos sonoros discretos nos controlos principais" to mapOf("en" to "Subtle sound effects for main controls", "es" to "Efectos de sonido discretos en los controles principales", "fr" to "Effets sonores discrets pour les commandes principales"),
        "Vibração ao tocar" to mapOf("en" to "Touch vibration", "es" to "Vibración táctil", "fr" to "Vibration tactile"),
        "Resposta tátil nos botões e ações principais" to mapOf("en" to "Haptic feedback on buttons and main actions", "es" to "Respuesta háptica en botones y acciones principales", "fr" to "Retour haptique sur les boutons et actions principales"),
        "Testar som e vibração" to mapOf("en" to "Test sound and vibration", "es" to "Probar sonido y vibración", "fr" to "Tester le son et la vibration"),
        "Segurança" to mapOf("en" to "Security", "es" to "Seguridad", "fr" to "Sécurité"),
        "Segurança da conta" to mapOf("en" to "Account security", "es" to "Seguridad de la cuenta", "fr" to "Sécurité du compte"),
        "Contas bloqueadas" to mapOf("en" to "Blocked accounts", "es" to "Cuentas bloqueadas", "fr" to "Comptes bloqués"),
        "Não bloqueou ninguém." to mapOf("en" to "You haven't blocked anyone.", "es" to "No has bloqueado a nadie.", "fr" to "Vous n'avez bloqué personne."),
        "Desbloquear" to mapOf("en" to "Unblock", "es" to "Desbloquear", "fr" to "Débloquer"),
        "Informações legais" to mapOf("en" to "Legal information", "es" to "Información legal", "fr" to "Informations légales"),
        "Termos de uso" to mapOf("en" to "Terms of use", "es" to "Términos de uso", "fr" to "Conditions d'utilisation"),
        "Política de privacidade" to mapOf("en" to "Privacy policy", "es" to "Política de privacidad", "fr" to "Politique de confidentialité"),
        "Política de reembolso" to mapOf("en" to "Refund policy", "es" to "Política de reembolso", "fr" to "Politique de remboursement"),
        "Regras da comunidade" to mapOf("en" to "Community guidelines", "es" to "Normas de la comunidad", "fr" to "Règles de la communauté"),
        "Suporte" to mapOf("en" to "Support", "es" to "Soporte", "fr" to "Assistance"),
        "Conta" to mapOf("en" to "Account", "es" to "Cuenta", "fr" to "Compte"),
        "Sair" to mapOf("en" to "Sign out", "es" to "Cerrar sesión", "fr" to "Se déconnecter"),
        "Apagar conta" to mapOf("en" to "Delete account", "es" to "Eliminar cuenta", "fr" to "Supprimer le compte"),
        "Idioma da aplicação" to mapOf("en" to "App language", "es" to "Idioma de la aplicación", "fr" to "Langue de l'application"),
        "Escolher idioma" to mapOf("en" to "Choose language", "es" to "Elegir idioma", "fr" to "Choisir la langue"),
        "Português" to mapOf("en" to "Portuguese", "es" to "Portugués", "fr" to "Portugais"),
        "Inglês" to mapOf("en" to "English", "es" to "Inglés", "fr" to "Anglais"),
        "Espanhol" to mapOf("en" to "Spanish", "es" to "Español", "fr" to "Espagnol"),
        "Francês" to mapOf("en" to "French", "es" to "Francés", "fr" to "Français"),
        "Guardar" to mapOf("en" to "Save", "es" to "Guardar", "fr" to "Enregistrer"),
        "Cancelar" to mapOf("en" to "Cancel", "es" to "Cancelar", "fr" to "Annuler"),
        "Fechar" to mapOf("en" to "Close", "es" to "Cerrar", "fr" to "Fermer"),
        "Perfil profissional" to mapOf("en" to "Professional profile", "es" to "Perfil profesional", "fr" to "Profil professionnel"),
        "Pré-visualização do perfil" to mapOf("en" to "Profile preview", "es" to "Vista previa del perfil", "fr" to "Aperçu du profil"),
        "Vídeo de apresentação adicionado" to mapOf("en" to "Presentation video added", "es" to "Vídeo de presentación añadido", "fr" to "Vidéo de présentation ajoutée"),
        "Esta é uma pré-visualização do que outras pessoas podem ver." to mapOf("en" to "This is a preview of what other people can see.", "es" to "Esta es una vista previa de lo que otras personas pueden ver.", "fr" to "Voici un aperçu de ce que les autres peuvent voir."),
        "Criar link" to mapOf("en" to "Create link", "es" to "Crear enlace", "fr" to "Créer un lien"),
        "Respostas através dos seus links" to mapOf("en" to "Replies through your links", "es" to "Respuestas a través de tus enlaces", "fr" to "Réponses via vos liens"),
        "Respostas ilimitadas através dos links personalizados" to mapOf("en" to "Unlimited replies through personalized links", "es" to "Respuestas ilimitadas mediante enlaces personalizados", "fr" to "Réponses illimitées via les liens personnalisés"),
        "Limite atingido. Poderá responder novamente e criar links no dia 1, às 00h00 (hora de Angola), ou ativar o Pro." to mapOf("en" to "Limit reached. You can reply and create links again on the 1st at 00:00 (Angola time), or activate Pro.", "es" to "Límite alcanzado. Podrás responder y crear enlaces de nuevo el día 1 a las 00:00 (hora de Angola), o activar Pro.", "fr" to "Limite atteint. Vous pourrez répondre et créer des liens à nouveau le 1er à 00 h 00 (heure d'Angola), ou activer Pro."),
        "As conversas normais continuam disponíveis. O limite renova no dia 1, às 00h00 (hora de Angola)." to mapOf("en" to "Regular chats remain available. The limit resets on the 1st at 00:00 (Angola time).", "es" to "Los chats normales siguen disponibles. El límite se restablece el día 1 a las 00:00 (hora de Angola).", "fr" to "Les conversations normales restent disponibles. La limite est réinitialisée le 1er à 00 h 00 (heure d'Angola)."),
        "Grátis" to mapOf("en" to "Free", "es" to "Gratis", "fr" to "Gratuit"),
        "Plano atual" to mapOf("en" to "Current plan", "es" to "Plan actual", "fr" to "Offre actuelle"),
        "Editar perfil" to mapOf("en" to "Edit profile", "es" to "Editar perfil", "fr" to "Modifier le profil"),
        "Adicionar fotografia" to mapOf("en" to "Add photo", "es" to "Añadir foto", "fr" to "Ajouter une photo"),
        "Adicionar link" to mapOf("en" to "Add link", "es" to "Añadir enlace", "fr" to "Ajouter un lien"),
        "Biografia" to mapOf("en" to "Bio", "es" to "Biografía", "fr" to "Biographie"),
        "Nome" to mapOf("en" to "Name", "es" to "Nombre", "fr" to "Nom"),
        "Email" to mapOf("en" to "Email", "es" to "Correo electrónico", "fr" to "E-mail"),
        "Verificar email" to mapOf("en" to "Verify email", "es" to "Verificar correo", "fr" to "Vérifier l'e-mail"),
        "Já confirmei o e-mail" to mapOf("en" to "I've verified my email", "es" to "Ya he verificado mi correo", "fr" to "J'ai confirmé mon e-mail"),
        "Ativar plano" to mapOf("en" to "Activate plan", "es" to "Activar plan", "fr" to "Activer l'offre"),
        "Novo link" to mapOf("en" to "New link", "es" to "Nuevo enlace", "fr" to "Nouveau lien"),
        "Copiar link" to mapOf("en" to "Copy link", "es" to "Copiar enlace", "fr" to "Copier le lien"),
        "Partilhar link" to mapOf("en" to "Share link", "es" to "Compartir enlace", "fr" to "Partager le lien"),
        "Eliminar" to mapOf("en" to "Delete", "es" to "Eliminar", "fr" to "Supprimer"),
        "Voltar" to mapOf("en" to "Back", "es" to "Volver", "fr" to "Retour"),
        "Continuar" to mapOf("en" to "Continue", "es" to "Continuar", "fr" to "Continuer"),
        "Entrar" to mapOf("en" to "Log in", "es" to "Iniciar sesión", "fr" to "Se connecter"),
        "Criar conta" to mapOf("en" to "Create account", "es" to "Crear cuenta", "fr" to "Créer un compte"),
        "Palavra-passe" to mapOf("en" to "Password", "es" to "Contraseña", "fr" to "Mot de passe"),
        "Pesquisar" to mapOf("en" to "Search", "es" to "Buscar", "fr" to "Rechercher"),
        "Conversas" to mapOf("en" to "Chats", "es" to "Chats", "fr" to "Discussions"),
        "Contactos" to mapOf("en" to "Contacts", "es" to "Contactos", "fr" to "Contacts"),
        "Notificações" to mapOf("en" to "Notifications", "es" to "Notificaciones", "fr" to "Notifications"),
        "Privacidade" to mapOf("en" to "Privacy", "es" to "Privacidad", "fr" to "Confidentialité"),
        "Bloquear" to mapOf("en" to "Block", "es" to "Bloquear", "fr" to "Bloquer"),
        "Denunciar" to mapOf("en" to "Report", "es" to "Denunciar", "fr" to "Signaler"),
        "Guardar alterações" to mapOf("en" to "Save changes", "es" to "Guardar cambios", "fr" to "Enregistrer les modifications"),
        "Adicionar" to mapOf("en" to "Add", "es" to "Añadir", "fr" to "Ajouter"),
        "Mensagem" to mapOf("en" to "Message", "es" to "Mensaje", "fr" to "Message"),
        "Enviar" to mapOf("en" to "Send", "es" to "Enviar", "fr" to "Envoyer"),
        "Sem mensagens" to mapOf("en" to "No messages", "es" to "Sin mensajes", "fr" to "Aucun message"),
        "Cancelar" to mapOf("en" to "Cancel", "es" to "Cancelar", "fr" to "Annuler"),

        "Meus links" to mapOf("en" to "My links", "es" to "Mis enlaces", "fr" to "Mes liens"),
        "Planos" to mapOf("en" to "Plans", "es" to "Planes", "fr" to "Offres"),
        "Criar story" to mapOf("en" to "Create story", "es" to "Crear historia", "fr" to "Créer une story"),
        "Criar grupo" to mapOf("en" to "Create group", "es" to "Crear grupo", "fr" to "Créer un groupe"),
        "A minha fotografia de perfil" to mapOf("en" to "My profile photo", "es" to "Mi foto de perfil", "fr" to "Ma photo de profil"),
        "O meu perfil" to mapOf("en" to "My profile", "es" to "Mi perfil", "fr" to "Mon profil"),
        "Ver e editar perfil" to mapOf("en" to "View and edit profile", "es" to "Ver y editar perfil", "fr" to "Voir et modifier le profil"),
        "Ver o meu story" to mapOf("en" to "View my story", "es" to "Ver mi historia", "fr" to "Voir ma story"),
        "A MINHA CONTA" to mapOf("en" to "MY ACCOUNT", "es" to "MI CUENTA", "fr" to "MON COMPTE"),
        "Pesquisar" to mapOf("en" to "Search", "es" to "Buscar", "fr" to "Rechercher"),
        "Nome, usuário ou código" to mapOf("en" to "Name, username or code", "es" to "Nombre, usuario o código", "fr" to "Nom, utilisateur ou code"),
        "Fechar pesquisa" to mapOf("en" to "Close search", "es" to "Cerrar búsqueda", "fr" to "Fermer la recherche"),
        "Nenhum resultado. Confira o nome ou o código." to mapOf("en" to "No results. Check the name or code.", "es" to "No hay resultados. Comprueba el nombre o el código.", "fr" to "Aucun résultat. Vérifiez le nom ou le code."),
        "Toque para conversar" to mapOf("en" to "Tap to chat", "es" to "Toca para chatear", "fr" to "Touchez pour discuter"),
        "Grupo, toque para entrar" to mapOf("en" to "Group, tap to join", "es" to "Grupo, toca para entrar", "fr" to "Groupe, touchez pour rejoindre"),
        "Nenhuma conversa ainda. Pesquise um nome ou código para começar." to mapOf("en" to "No chats yet. Search for a name or code to get started.", "es" to "Aún no hay chats. Busca un nombre o código para empezar.", "fr" to "Aucune discussion. Recherchez un nom ou un code pour commencer."),
        "Nada por aqui." to mapOf("en" to "Nothing here yet.", "es" to "No hay nada aquí.", "fr" to "Rien ici pour le moment."),
        "Fotografia do perfil" to mapOf("en" to "Profile photo", "es" to "Foto de perfil", "fr" to "Photo de profil"),
        "A carregar perfil..." to mapOf("en" to "Loading profile...", "es" to "Cargando perfil...", "fr" to "Chargement du profil..."),
        "Não foi possível carregar este perfil." to mapOf("en" to "Could not load this profile.", "es" to "No se pudo cargar este perfil.", "fr" to "Impossible de charger ce profil."),
        "Ver vídeo de apresentação" to mapOf("en" to "Watch presentation video", "es" to "Ver vídeo de presentación", "fr" to "Voir la vidéo de présentation"),
        "Adicionar contacto" to mapOf("en" to "Add contact", "es" to "Añadir contacto", "fr" to "Ajouter un contact"),
        "Com histórico" to mapOf("en" to "With history", "es" to "Con historial", "fr" to "Avec historique"),
        "Sem histórico" to mapOf("en" to "Without history", "es" to "Sin historial", "fr" to "Sans historique"),
        "Novo grupo" to mapOf("en" to "New group", "es" to "Nuevo grupo", "fr" to "Nouveau groupe"),
        "Nome do grupo" to mapOf("en" to "Group name", "es" to "Nombre del grupo", "fr" to "Nom du groupe"),
        "Criar" to mapOf("en" to "Create", "es" to "Crear", "fr" to "Créer"),
        "Galeria" to mapOf("en" to "Gallery", "es" to "Galería", "fr" to "Galerie"),
        "Ficheiro" to mapOf("en" to "File", "es" to "Archivo", "fr" to "Fichier"),
        "Música" to mapOf("en" to "Music", "es" to "Música", "fr" to "Musique"),
        "Permita o acesso às fotos para escolher da galeria." to mapOf("en" to "Allow photo access to choose from the gallery.", "es" to "Permite el acceso a las fotos para elegir de la galería.", "fr" to "Autorisez l'accès aux photos pour choisir dans la galerie."),
        "Permitir acesso" to mapOf("en" to "Allow access", "es" to "Permitir acceso", "fr" to "Autoriser l'accès"),
        "Nenhuma foto ou vídeo encontrado." to mapOf("en" to "No photos or videos found.", "es" to "No se encontraron fotos ni vídeos.", "fr" to "Aucune photo ou vidéo trouvée."),
        "Vídeo" to mapOf("en" to "Video", "es" to "Vídeo", "fr" to "Vidéo"),
        "Voltar" to mapOf("en" to "Back", "es" to "Volver", "fr" to "Retour"),
        "Mais opções" to mapOf("en" to "More options", "es" to "Más opciones", "fr" to "Plus d'options"),
        "Anexar" to mapOf("en" to "Attach", "es" to "Adjuntar", "fr" to "Joindre"),
        "Mensagens temporárias" to mapOf("en" to "Disappearing messages", "es" to "Mensajes temporales", "fr" to "Messages éphémères"),
        "Convidar para o grupo" to mapOf("en" to "Invite to group", "es" to "Invitar al grupo", "fr" to "Inviter dans le groupe"),
        "Convidar" to mapOf("en" to "Invite", "es" to "Invitar", "fr" to "Inviter"),
        "Denunciar grupo" to mapOf("en" to "Report group", "es" to "Denunciar grupo", "fr" to "Signaler le groupe"),
        "Sair do grupo" to mapOf("en" to "Leave group", "es" to "Salir del grupo", "fr" to "Quitter le groupe"),
        "Não foi possível sair do grupo." to mapOf("en" to "Could not leave the group.", "es" to "No se pudo salir del grupo.", "fr" to "Impossible de quitter le groupe."),
        "Não foi possível bloquear." to mapOf("en" to "Could not block.", "es" to "No se pudo bloquear.", "fr" to "Impossible de bloquer."),
        "Desativadas" to mapOf("en" to "Off", "es" to "Desactivadas", "fr" to "Désactivées"),
        "24 horas" to mapOf("en" to "24 hours", "es" to "24 horas", "fr" to "24 heures"),
        "7 dias" to mapOf("en" to "7 days", "es" to "7 días", "fr" to "7 jours"),
        "90 dias" to mapOf("en" to "90 days", "es" to "90 días", "fr" to "90 jours"),
        "As novas mensagens desta conversa serão eliminadas para todos após o período escolhido." to mapOf("en" to "New messages in this chat will be deleted for everyone after the selected period.", "es" to "Los mensajes nuevos de este chat se eliminarán para todos después del período elegido.", "fr" to "Les nouveaux messages de cette discussion seront supprimés pour tous après la période choisie."),
        "Mensagens temporárias desativadas." to mapOf("en" to "Disappearing messages are off.", "es" to "Los mensajes temporales están desactivados.", "fr" to "Les messages éphémères sont désactivés."),
        "Não foi possível alterar esta definição." to mapOf("en" to "Could not change this setting.", "es" to "No se pudo cambiar este ajuste.", "fr" to "Impossible de modifier ce paramètre."),
        "Spam" to mapOf("en" to "Spam", "es" to "Spam", "fr" to "Spam"),
        "Golpe" to mapOf("en" to "Scam", "es" to "Estafa", "fr" to "Arnaque"),
        "Assédio" to mapOf("en" to "Harassment", "es" to "Acoso", "fr" to "Harcèlement"),
        "Conteúdo impróprio" to mapOf("en" to "Inappropriate content", "es" to "Contenido inapropiado", "fr" to "Contenu inapproprié"),
        "Conteúdo ilegal" to mapOf("en" to "Illegal content", "es" to "Contenido ilegal", "fr" to "Contenu illégal"),
        "Falsificação de identidade" to mapOf("en" to "Impersonation", "es" to "Suplantación de identidad", "fr" to "Usurpation d'identité"),
        "Outro motivo" to mapOf("en" to "Other reason", "es" to "Otro motivo", "fr" to "Autre motif"),
        "Motivo da denúncia" to mapOf("en" to "Report reason", "es" to "Motivo de la denuncia", "fr" to "Motif du signalement"),
        "Denúncia enviada. Obrigado." to mapOf("en" to "Report submitted. Thank you.", "es" to "Denuncia enviada. Gracias.", "fr" to "Signalement envoyé. Merci."),
        "Não foi possível enviar a denúncia." to mapOf("en" to "Could not submit the report.", "es" to "No se pudo enviar la denuncia.", "fr" to "Impossible d'envoyer le signalement."),
        "Foto" to mapOf("en" to "Photo", "es" to "Foto", "fr" to "Photo"),
        "Indisponível" to mapOf("en" to "Unavailable", "es" to "No disponible", "fr" to "Indisponible"),
        "Toque para ver" to mapOf("en" to "Tap to view", "es" to "Toca para ver", "fr" to "Touchez pour voir"),
        "Áudio" to mapOf("en" to "Audio", "es" to "Audio", "fr" to "Audio"),
        "Toque para abrir" to mapOf("en" to "Tap to open", "es" to "Toca para abrir", "fr" to "Touchez pour ouvrir"),
        "Não há uma app no telemóvel para abrir este ficheiro." to mapOf("en" to "There is no app on this phone to open this file.", "es" to "No hay ninguna aplicación en el teléfono para abrir este archivo.", "fr" to "Aucune application sur ce téléphone ne peut ouvrir ce fichier."),
        "Não foi possível abrir o ficheiro." to mapOf("en" to "Could not open the file.", "es" to "No se pudo abrir el archivo.", "fr" to "Impossible d'ouvrir le fichier."),
        "Continuar com Google" to mapOf("en" to "Continue with Google", "es" to "Continuar con Google", "fr" to "Continuer avec Google"),
        "ou use o seu número" to mapOf("en" to "or use your phone number", "es" to "o usa tu número", "fr" to "ou utilisez votre numéro"),
        "Número de telefone" to mapOf("en" to "Phone number", "es" to "Número de teléfono", "fr" to "Numéro de téléphone"),
        "Receber código por SMS" to mapOf("en" to "Receive code by SMS", "es" to "Recibir código por SMS", "fr" to "Recevoir le code par SMS"),
        "Código de 6 dígitos" to mapOf("en" to "6-digit code", "es" to "Código de 6 dígitos", "fr" to "Code à 6 chiffres"),
        "Confirmar número" to mapOf("en" to "Confirm number", "es" to "Confirmar número", "fr" to "Confirmer le numéro"),
        "Alterar número" to mapOf("en" to "Change number", "es" to "Cambiar número", "fr" to "Changer de numéro"),
        "Verificação adicional" to mapOf("en" to "Additional verification", "es" to "Verificación adicional", "fr" to "Vérification supplémentaire"),
        "Confirmar código" to mapOf("en" to "Confirm code", "es" to "Confirmar código", "fr" to "Confirmer le code"),
        "Termos de Utilização" to mapOf("en" to "Terms of Service", "es" to "Términos de servicio", "fr" to "Conditions d'utilisation"),
        "Privacidade" to mapOf("en" to "Privacy", "es" to "Privacidad", "fr" to "Confidentialité"),
        "Regras da comunidade" to mapOf("en" to "Community guidelines", "es" to "Normas de la comunidad", "fr" to "Règles de la communauté"),
        "Assistentes de IA" to mapOf("en" to "AI assistants", "es" to "Asistentes de IA", "fr" to "Assistants IA"),
        "Ferramentas de inteligência artificial disponíveis no PiMsg." to mapOf("en" to "Artificial intelligence tools available in PiMsg.", "es" to "Herramientas de inteligencia artificial disponibles en PiMsg.", "fr" to "Outils d'intelligence artificielle disponibles dans PiMsg."),
        "A consultar os assistentes…" to mapOf("en" to "Loading assistants…", "es" to "Consultando asistentes…", "fr" to "Chargement des assistants…"),
        "Não existem assistentes disponíveis neste momento." to mapOf("en" to "No assistants are available right now.", "es" to "No hay asistentes disponibles en este momento.", "fr" to "Aucun assistant n'est disponible pour le moment."),
        "Disponível" to mapOf("en" to "Available", "es" to "Disponible", "fr" to "Disponible"),
        "Temporariamente indisponível" to mapOf("en" to "Temporarily unavailable", "es" to "Temporalmente no disponible", "fr" to "Temporairement indisponible"),
        "Olá. Sou o Pi AI. Em que posso ajudar?" to mapOf("en" to "Hello. I'm Pi AI. How can I help?", "es" to "Hola. Soy Pi AI. ¿En qué puedo ayudarte?", "fr" to "Bonjour. Je suis Pi AI. Comment puis-je vous aider ?"),
        "Resposta vazia" to mapOf("en" to "Empty response", "es" to "Respuesta vacía", "fr" to "Réponse vide"),
        "Não foi possível obter uma resposta. Verifique a ligação e tente novamente." to mapOf("en" to "Could not get a response. Check your connection and try again.", "es" to "No se pudo obtener una respuesta. Comprueba la conexión e inténtalo de nuevo.", "fr" to "Impossible d'obtenir une réponse. Vérifiez la connexion et réessayez."),
        "A preparar uma resposta" to mapOf("en" to "Preparing a response", "es" to "Preparando una respuesta", "fr" to "Préparation d'une réponse"),
        "Assistente PiMsg" to mapOf("en" to "PiMsg assistant", "es" to "Asistente de PiMsg", "fr" to "Assistant PiMsg"),
        "A responder…" to mapOf("en" to "Replying…", "es" to "Respondiendo…", "fr" to "Réponse en cours…"),
        "Escreva ao Pi AI…" to mapOf("en" to "Write to Pi AI…", "es" to "Escribe a Pi AI…", "fr" to "Écrivez à Pi AI…"),
        "As respostas são geradas por IA. Confirme informações importantes antes de agir." to mapOf("en" to "Responses are AI-generated. Verify important information before acting.", "es" to "Las respuestas las genera la IA. Verifica la información importante antes de actuar.", "fr" to "Les réponses sont générées par l'IA. Vérifiez les informations importantes avant d'agir."),
        "Segurança da conta" to mapOf("en" to "Account security", "es" to "Seguridad de la cuenta", "fr" to "Sécurité du compte"),
        "Proteja o acesso à sua conta com uma segunda confirmação no início de sessão." to mapOf("en" to "Protect access to your account with a second sign-in confirmation.", "es" to "Protege el acceso a tu cuenta con una segunda confirmación al iniciar sesión.", "fr" to "Protégez l'accès à votre compte avec une seconde confirmation à la connexion."),
        "VERIFICAÇÃO EM DUAS ETAPAS" to mapOf("en" to "TWO-STEP VERIFICATION", "es" to "VERIFICACIÓN EN DOS PASOS", "fr" to "VÉRIFICATION EN DEUX ÉTAPES"),
        "Google Authenticator ativo" to mapOf("en" to "Google Authenticator active", "es" to "Google Authenticator activo", "fr" to "Google Authenticator actif"),
        "Verificação em duas etapas ativada." to mapOf("en" to "Two-step verification enabled.", "es" to "Verificación en dos pasos activada.", "fr" to "Vérification en deux étapes activée."),
        "Remover autenticador" to mapOf("en" to "Remove authenticator", "es" to "Eliminar autenticador", "fr" to "Supprimer l'authentificateur"),
        "Configurar autenticador" to mapOf("en" to "Set up authenticator", "es" to "Configurar autenticador", "fr" to "Configurer l'authentificateur"),
        "Chave do PiMsg" to mapOf("en" to "PiMsg key", "es" to "Clave de PiMsg", "fr" to "Clé PiMsg"),
        "Chave copiada." to mapOf("en" to "Key copied.", "es" to "Clave copiada.", "fr" to "Clé copiée."),
        "Copiar chave" to mapOf("en" to "Copy key", "es" to "Copiar clave", "fr" to "Copier la clé"),
        "Abrir autenticador" to mapOf("en" to "Open authenticator", "es" to "Abrir autenticador", "fr" to "Ouvrir l'authentificateur"),
        "2. Confirme o código" to mapOf("en" to "2. Confirm the code", "es" to "2. Confirma el código", "fr" to "2. Confirmez le code"),
        "Código de seis dígitos" to mapOf("en" to "Six-digit code", "es" to "Código de seis dígitos", "fr" to "Code à six chiffres"),
        "Ativar verificação em duas etapas" to mapOf("en" to "Enable two-step verification", "es" to "Activar verificación en dos pasos", "fr" to "Activer la vérification en deux étapes"),
        "Não foi possível iniciar sessão com o Google." to mapOf("en" to "Could not sign in with Google.", "es" to "No se pudo iniciar sesión con Google.", "fr" to "Impossible de se connecter avec Google."),
        "Não foi possível enviar o código. Confirme o número e a configuração do Firebase." to mapOf("en" to "Could not send the code. Check the number and Firebase configuration.", "es" to "No se pudo enviar el código. Comprueba el número y la configuración de Firebase.", "fr" to "Impossible d'envoyer le code. Vérifiez le numéro et la configuration Firebase."),

        "Até 30 contactos" to mapOf("en" to "Up to 30 contacts", "es" to "Hasta 30 contactos", "fr" to "Jusqu'à 30 contacts"),
        "Até 30 mensagens por dia" to mapOf("en" to "Up to 30 messages per day", "es" to "Hasta 30 mensajes al día", "fr" to "Jusqu'à 30 messages par jour"),
        "500 respostas por mês através dos links personalizados" to mapOf("en" to "500 replies per month through personalized links", "es" to "500 respuestas al mes mediante enlaces personalizados", "fr" to "500 réponses par mois via les liens personnalisés"),
        "5 respostas do Pi AI por dia" to mapOf("en" to "5 Pi AI replies per day", "es" to "5 respuestas de Pi AI al día", "fr" to "5 réponses de Pi AI par jour"),
        "Conversas e partilha de ficheiros" to mapOf("en" to "Chats and file sharing", "es" to "Chats y uso compartido de archivos", "fr" to "Discussions et partage de fichiers"),
        "Mensagens sem limite diário" to mapOf("en" to "Messages without a daily limit", "es" to "Mensajes sin límite diario", "fr" to "Messages sans limite quotidien"),
        "30 respostas do Pi AI por dia" to mapOf("en" to "30 Pi AI replies per day", "es" to "30 respuestas de Pi AI al día", "fr" to "30 réponses de Pi AI par jour"),
        "Vídeo de apresentação no perfil" to mapOf("en" to "Profile presentation video", "es" to "Vídeo de presentación en el perfil", "fr" to "Vidéo de présentation sur le profil"),
        "Plano atual · Free" to mapOf("en" to "Current plan · Free", "es" to "Plan actual · Gratis", "fr" to "Offre actuelle · Gratuit"),
        "Plano atual · Pro" to mapOf("en" to "Current plan · Pro", "es" to "Plan actual · Pro", "fr" to "Offre actuelle · Pro"),
        "Atingiu as 500 respostas mensais através dos links personalizados. Poderá responder novamente no dia 1, às 00h00 (hora de Angola), ou ativar o Pro." to mapOf("en" to "You've reached the 500 monthly replies through personalized links. You can reply again on the 1st at 00:00 (Angola time), or activate Pro.", "es" to "Has alcanzado las 500 respuestas mensuales mediante enlaces personalizados. Podrás responder de nuevo el día 1 a las 00:00 (hora de Angola), o activar Pro.", "fr" to "Vous avez atteint les 500 réponses mensuelles via les liens personnalisés. Vous pourrez répondre à nouveau le 1er à 00 h 00 (heure d'Angola), ou activer Pro."),
        "Limite diário de mensagens atingido. Volte amanhã, compre créditos ou ative o plano Pro." to mapOf("en" to "Daily message limit reached. Return tomorrow, buy credits or activate Pro.", "es" to "Has alcanzado el límite diario de mensajes. Vuelve mañana, compra créditos o activa Pro.", "fr" to "Limite quotidien de messages atteint. Revenez demain, achetez des crédits ou activez Pro."),

        "A enviar vídeo..." to mapOf("en" to "Uploading video...", "es" to "Subiendo vídeo...", "fr" to "Envoi de la vidéo..."),
        "Vídeo de apresentação actualizado." to mapOf("en" to "Presentation video updated.", "es" to "Vídeo de presentación actualizado.", "fr" to "Vidéo de présentation mise à jour."),
        "O vídeo de apresentação está disponível no plano Pro." to mapOf("en" to "The presentation video is available on the Pro plan.", "es" to "El vídeo de presentación está disponible en el plan Pro.", "fr" to "La vidéo de présentation est disponible avec l'offre Pro."),
        "Não foi possível guardar o vídeo." to mapOf("en" to "Could not save the video.", "es" to "No se pudo guardar el vídeo.", "fr" to "Impossible d'enregistrer la vidéo."),
        "A enviar fotografia..." to mapOf("en" to "Uploading photo...", "es" to "Subiendo foto...", "fr" to "Envoi de la photo..."),
        "Fotografia atualizada." to mapOf("en" to "Photo updated.", "es" to "Foto actualizada.", "fr" to "Photo mise à jour."),
        "Não foi possível atualizar a fotografia." to mapOf("en" to "Could not update the photo.", "es" to "No se pudo actualizar la foto.", "fr" to "Impossible de mettre à jour la photo."),
        "Não foi possível carregar o perfil." to mapOf("en" to "Could not load the profile.", "es" to "No se pudo cargar el perfil.", "fr" to "Impossible de charger le profil."),
        "Alterar fotografia" to mapOf("en" to "Change photo", "es" to "Cambiar foto", "fr" to "Changer la photo"),
        "Fotografia removida." to mapOf("en" to "Photo removed.", "es" to "Foto eliminada.", "fr" to "Photo supprimée."),
        "Não foi possível remover a fotografia." to mapOf("en" to "Could not remove the photo.", "es" to "No se pudo eliminar la foto.", "fr" to "Impossible de supprimer la photo."),
        "Remover fotografia" to mapOf("en" to "Remove photo", "es" to "Eliminar foto", "fr" to "Supprimer la photo"),
        "Informações pessoais" to mapOf("en" to "Personal information", "es" to "Información personal", "fr" to "Informations personnelles"),
        "Biografia / apresentação" to mapOf("en" to "Bio / introduction", "es" to "Biografía / presentación", "fr" to "Biographie / présentation"),
        "E-mail e verificação" to mapOf("en" to "Email and verification", "es" to "Correo y verificación", "fr" to "E-mail et vérification"),
        "E-mail confirmado · Perfil profissional activo" to mapOf("en" to "Email confirmed · Professional profile active", "es" to "Correo confirmado · Perfil profesional activo", "fr" to "E-mail confirmé · Profil professionnel actif"),
        "Confirme o seu e-mail para activar o perfil profissional." to mapOf("en" to "Verify your email to activate your professional profile.", "es" to "Confirma tu correo para activar el perfil profesional.", "fr" to "Confirmez votre e-mail pour activer votre profil professionnel."),
        "Enviar confirmação" to mapOf("en" to "Send verification", "es" to "Enviar confirmación", "fr" to "Envoyer la confirmation"),
        "Introduza um e-mail válido." to mapOf("en" to "Enter a valid email address.", "es" to "Introduce un correo válido.", "fr" to "Saisissez une adresse e-mail valide."),
        "Enviámos uma ligação de confirmação para o seu e-mail." to mapOf("en" to "We sent a verification link to your email.", "es" to "Enviamos un enlace de confirmación a tu correo.", "fr" to "Nous avons envoyé un lien de confirmation à votre e-mail."),
        "Não foi possível enviar a confirmação. Volte a iniciar sessão e tente novamente." to mapOf("en" to "Could not send the verification. Sign in again and try.", "es" to "No se pudo enviar la confirmación. Inicia sesión de nuevo e inténtalo.", "fr" to "Impossible d'envoyer la confirmation. Reconnectez-vous et réessayez."),
        "E-mail confirmado. O perfil profissional está activo." to mapOf("en" to "Email verified. Your professional profile is active.", "es" to "Correo confirmado. Tu perfil profesional está activo.", "fr" to "E-mail confirmé. Votre profil professionnel est actif."),
        "Ainda não recebemos a confirmação. Abra a ligação enviada por e-mail e tente novamente." to mapOf("en" to "We haven't received confirmation yet. Open the email link and try again.", "es" to "Aún no hemos recibido la confirmación. Abre el enlace del correo e inténtalo de nuevo.", "fr" to "Nous n'avons pas encore reçu la confirmation. Ouvrez le lien reçu par e-mail et réessayez."),
        "Não foi possível verificar o estado do e-mail." to mapOf("en" to "Could not check email verification status.", "es" to "No se pudo comprobar la verificación del correo.", "fr" to "Impossible de vérifier l'état de l'e-mail."),
        "Já confirmei o e-mail" to mapOf("en" to "I've verified my email", "es" to "Ya he confirmado mi correo", "fr" to "J'ai confirmé mon e-mail"),
        "Apresente-se às pessoas que visitam o seu perfil." to mapOf("en" to "Introduce yourself to people who visit your profile.", "es" to "Preséntate a las personas que visitan tu perfil.", "fr" to "Présentez-vous aux personnes qui visitent votre profil."),
        "Adicionar vídeo" to mapOf("en" to "Add video", "es" to "Añadir vídeo", "fr" to "Ajouter une vidéo"),
        "Substituir vídeo" to mapOf("en" to "Replace video", "es" to "Reemplazar vídeo", "fr" to "Remplacer la vidéo"),
        "Vídeo adicionado ao perfil" to mapOf("en" to "Video added to profile", "es" to "Vídeo añadido al perfil", "fr" to "Vidéo ajoutée au profil"),
        "Vídeo removido." to mapOf("en" to "Video removed.", "es" to "Vídeo eliminado.", "fr" to "Vidéo supprimée."),
        "Remover vídeo" to mapOf("en" to "Remove video", "es" to "Eliminar vídeo", "fr" to "Supprimer la vidéo"),
        "Redes sociais" to mapOf("en" to "Social links", "es" to "Redes sociales", "fr" to "Réseaux sociaux"),
        "Adicione os links que pretende partilhar no seu perfil." to mapOf("en" to "Add the links you want to share on your profile.", "es" to "Añade los enlaces que quieras compartir en tu perfil.", "fr" to "Ajoutez les liens que vous souhaitez partager sur votre profil."),
        "Remover ligação" to mapOf("en" to "Remove link", "es" to "Eliminar enlace", "fr" to "Supprimer le lien"),
        "O meu código PiMsg" to mapOf("en" to "My PiMsg code", "es" to "Mi código PiMsg", "fr" to "Mon code PiMsg"),
        "Partilhe este código para que outras pessoas o encontrem no PiMsg." to mapOf("en" to "Share this code so others can find you on PiMsg.", "es" to "Comparte este código para que otras personas te encuentren en PiMsg.", "fr" to "Partagez ce code pour que d'autres personnes vous trouvent sur PiMsg."),
        "Código copiado." to mapOf("en" to "Code copied.", "es" to "Código copiado.", "fr" to "Code copié."),
        "Gerar novo" to mapOf("en" to "Generate new", "es" to "Generar nuevo", "fr" to "Générer un nouveau"),
        "Novo código criado." to mapOf("en" to "New code created.", "es" to "Nuevo código creado.", "fr" to "Nouveau code créé."),
        "Mostrar o meu número no perfil" to mapOf("en" to "Show my number on my profile", "es" to "Mostrar mi número en el perfil", "fr" to "Afficher mon numéro sur mon profil"),
        "Disponível quando o número estiver confirmado." to mapOf("en" to "Available once the number is verified.", "es" to "Disponible cuando se confirme el número.", "fr" to "Disponible lorsque le numéro est confirmé."),
        "Ver como os outros veem o meu perfil" to mapOf("en" to "Preview how others see my profile", "es" to "Ver cómo otros ven mi perfil", "fr" to "Voir comment les autres voient mon profil"),
        "Perfil guardado com sucesso." to mapOf("en" to "Profile saved successfully.", "es" to "Perfil guardado correctamente.", "fr" to "Profil enregistré avec succès."),
        "Não foi possível guardar. Verifique as ligações e tente novamente." to mapOf("en" to "Could not save. Check your connection and try again.", "es" to "No se pudo guardar. Comprueba la conexión e inténtalo de nuevo.", "fr" to "Impossible d'enregistrer. Vérifiez votre connexion et réessayez."),
        "Adicionar rede social" to mapOf("en" to "Add social link", "es" to "Añadir red social", "fr" to "Ajouter un lien social"),
        "Ligação do site ou perfil" to mapOf("en" to "Website or profile link", "es" to "Enlace del sitio o perfil", "fr" to "Lien du site ou du profil"),
        "O ícone do site será carregado automaticamente quando existir." to mapOf("en" to "The website icon will load automatically when available.", "es" to "El icono del sitio se cargará automáticamente cuando esté disponible.", "fr" to "L'icône du site sera chargée automatiquement si disponible."),
        "Ligação adicionada." to mapOf("en" to "Link added.", "es" to "Enlace añadido.", "fr" to "Lien ajouté."),
        "Tem fotografia personalizada" to mapOf("en" to "Custom photo", "es" to "Foto personalizada", "fr" to "Photo personnalisée"),
        "Link copiado." to mapOf("en" to "Link copied.", "es" to "Enlace copiado.", "fr" to "Lien copié."),
        "Ainda não criou nenhum link." to mapOf("en" to "You haven't created any links yet.", "es" to "Todavía no has creado ningún enlace.", "fr" to "Vous n'avez pas encore créé de lien."),
        "Assunto ou produto" to mapOf("en" to "Subject or product", "es" to "Asunto o producto", "fr" to "Sujet ou produit"),
        "Descrição na página do link (opcional)" to mapOf("en" to "Description on the link page (optional)", "es" to "Descripción en la página del enlace (opcional)", "fr" to "Description sur la page du lien (facultatif)"),
        "Adicionar fotografia ao link" to mapOf("en" to "Add a photo to the link", "es" to "Añadir foto al enlace", "fr" to "Ajouter une photo au lien"),
        "Imagem do link" to mapOf("en" to "Link image", "es" to "Imagen del enlace", "fr" to "Image du lien"),
        "Remover imagem" to mapOf("en" to "Remove image", "es" to "Eliminar imagen", "fr" to "Supprimer l'image"),
        "Não foi possível criar o link." to mapOf("en" to "Could not create the link.", "es" to "No se pudo crear el enlace.", "fr" to "Impossible de créer le lien."),
        "Os meus links" to mapOf("en" to "My links", "es" to "Mis enlaces", "fr" to "Mes liens"),
        "Plano em utilização" to mapOf("en" to "Current plan", "es" to "Plan en uso", "fr" to "Offre utilisée"),
        "Acesso essencial" to mapOf("en" to "Essential access", "es" to "Acceso esencial", "fr" to "Accès essentiel"),
        "Mais capacidade" to mapOf("en" to "More capacity", "es" to "Más capacidad", "fr" to "Plus de capacité"),
        "Pro semanal" to mapOf("en" to "Weekly Pro", "es" to "Pro semanal", "fr" to "Pro hebdomadaire"),
        "Acesso por semana" to mapOf("en" to "Weekly access", "es" to "Acceso semanal", "fr" to "Accès hebdomadaire"),
        "Pro mensal" to mapOf("en" to "Monthly Pro", "es" to "Pro mensual", "fr" to "Pro mensuel"),
        "Acesso por mês" to mapOf("en" to "Monthly access", "es" to "Acceso mensual", "fr" to "Accès mensuel"),
        "Gerir subscrição na Google Play" to mapOf("en" to "Manage subscription in Google Play", "es" to "Gestionar suscripción en Google Play", "fr" to "Gérer l'abonnement dans Google Play"),
        "A consultar a sua conta…" to mapOf("en" to "Checking your account…", "es" to "Consultando tu cuenta…", "fr" to "Vérification de votre compte…"),

        "Não foi possível entrar com o Google." to mapOf("en" to "Could not sign in with Google.", "es" to "No se pudo iniciar sesión con Google.", "fr" to "Impossible de se connecter avec Google."),
        "Configuração do Google incompleta no Firebase." to mapOf("en" to "Google setup is incomplete in Firebase.", "es" to "La configuración de Google en Firebase está incompleta.", "fr" to "La configuration Google dans Firebase est incomplète."),
        "Não foi possível iniciar a confirmação neste ecrã." to mapOf("en" to "Could not start verification on this screen.", "es" to "No se pudo iniciar la verificación en esta pantalla.", "fr" to "Impossible de démarrer la vérification sur cet écran."),
        "Não foi possível detetar o código do país." to mapOf("en" to "Could not detect the country code.", "es" to "No se pudo detectar el código del país.", "fr" to "Impossible de détecter l'indicatif du pays."),
        "Verifique o número de telefone." to mapOf("en" to "Check the phone number.", "es" to "Comprueba el número de teléfono.", "fr" to "Vérifiez le numéro de téléphone."),
        "Não foi possível confirmar este número." to mapOf("en" to "Could not verify this number.", "es" to "No se pudo verificar este número.", "fr" to "Impossible de vérifier ce numéro."),
        "Ative a autenticação por telefone no Firebase Authentication." to mapOf("en" to "Enable phone authentication in Firebase Authentication.", "es" to "Activa la autenticación por teléfono en Firebase Authentication.", "fr" to "Activez l'authentification par téléphone dans Firebase Authentication."),
        "Foram feitas demasiadas tentativas. Tente mais tarde." to mapOf("en" to "Too many attempts. Try again later.", "es" to "Demasiados intentos. Inténtalo más tarde.", "fr" to "Trop de tentatives. Réessayez plus tard."),
        "Introduza o código recebido por SMS." to mapOf("en" to "Enter the code received by SMS.", "es" to "Introduce el código recibido por SMS.", "fr" to "Saisissez le code reçu par SMS."),
        "Código incorreto ou expirado. Tente novamente." to mapOf("en" to "Incorrect or expired code. Try again.", "es" to "Código incorrecto o caducado. Inténtalo de nuevo.", "fr" to "Code incorrect ou expiré. Réessayez."),
        "Inicie sessão para configurar a segurança." to mapOf("en" to "Sign in to configure security.", "es" to "Inicia sesión para configurar la seguridad.", "fr" to "Connectez-vous pour configurer la sécurité."),
        "A verificação com autenticador exige uma conta Google com e-mail verificado." to mapOf("en" to "Authenticator verification requires a Google account with a verified email.", "es" to "La verificación con autenticador requiere una cuenta de Google con correo verificado.", "fr" to "La vérification par authentificateur nécessite un compte Google avec une adresse e-mail vérifiée."),
        "A configuração OAuth do Google não está disponível." to mapOf("en" to "Google OAuth configuration is unavailable.", "es" to "La configuración OAuth de Google no está disponible.", "fr" to "La configuration OAuth de Google n'est pas disponible."),
        "Não foi possível confirmar a conta Google." to mapOf("en" to "Could not verify the Google account.", "es" to "No se pudo verificar la cuenta de Google.", "fr" to "Impossible de vérifier le compte Google."),
        "Adicione esta chave ao Google Authenticator e introduza o código de seis dígitos gerado." to mapOf("en" to "Add this key to Google Authenticator and enter the generated six-digit code.", "es" to "Añade esta clave a Google Authenticator e introduce el código de seis dígitos generado.", "fr" to "Ajoutez cette clé à Google Authenticator et saisissez le code à six chiffres généré."),
        "Não foi possível iniciar a configuração do autenticador." to mapOf("en" to "Could not start authenticator setup.", "es" to "No se pudo iniciar la configuración del autenticador.", "fr" to "Impossible de démarrer la configuration de l'authentificateur."),
        "Sessão terminada." to mapOf("en" to "Session ended.", "es" to "Sesión finalizada.", "fr" to "Session terminée."),
        "Autenticador removido da conta." to mapOf("en" to "Authenticator removed from the account.", "es" to "Autenticador eliminado de la cuenta.", "fr" to "Authentificateur supprimé du compte."),
        "Não foi possível remover o autenticador. Poderá ser necessário iniciar sessão novamente." to mapOf("en" to "Could not remove the authenticator. You may need to sign in again.", "es" to "No se pudo eliminar el autenticador. Puede que tengas que iniciar sesión de nuevo.", "fr" to "Impossible de supprimer l'authentificateur. Vous devrez peut-être vous reconnecter."),
        "Não foi possível confirmar o código. Verifique os seis dígitos e tente novamente." to mapOf("en" to "Could not verify the code. Check the six digits and try again.", "es" to "No se pudo verificar el código. Comprueba los seis dígitos e inténtalo de nuevo.", "fr" to "Impossible de vérifier le code. Vérifiez les six chiffres et réessayez."),
        "Não foi possível abrir uma aplicação autenticadora neste aparelho." to mapOf("en" to "Could not open an authenticator app on this device.", "es" to "No se pudo abrir una aplicación de autenticación en este dispositivo.", "fr" to "Impossible d'ouvrir une application d'authentification sur cet appareil.") ,

        "Notificações" to mapOf("en" to "Notifications", "es" to "Notificaciones", "fr" to "Notifications"),
        "Notificações push" to mapOf("en" to "Push notifications", "es" to "Notificaciones push", "fr" to "Notifications push"),
        "Receber alertas de mensagens mesmo com o PiMsg fechado" to mapOf("en" to "Receive message alerts even when PiMsg is closed", "es" to "Recibe alertas de mensajes aunque PiMsg esté cerrado", "fr" to "Recevez des alertes même lorsque PiMsg est fermé"),
        "Avisos pop-up" to mapOf("en" to "Pop-up alerts", "es" to "Avisos emergentes", "fr" to "Alertes contextuelles"),
        "Mostrar alertas destacados no ecrã; desligar mantém notificações silenciosas" to mapOf("en" to "Show heads-up alerts; turning this off keeps notifications silent", "es" to "Mostrar avisos destacados; al desactivarlo, las notificaciones serán silenciosas", "fr" to "Afficher les alertes en bannière ; désactiver garde les notifications silencieuses"),
        "A escrever..." to mapOf("en" to "Typing…", "es" to "Escribiendo…", "fr" to "Écrit…"),
        "Traduzido automaticamente" to mapOf("en" to "Automatically translated", "es" to "Traducido automáticamente", "fr" to "Traduit automatiquement"),
        "Créditos de IA" to mapOf("en" to "AI credits", "es" to "Créditos de IA", "fr" to "Crédits IA"),
        "15 créditos iniciais de IA" to mapOf("en" to "15 starter AI credits", "es" to "15 créditos iniciales de IA", "fr" to "15 crédits IA de départ"),
        "Cada tarefa simples custa 2 créditos. Pedidos mais longos podem custar até 5. Quando os créditos terminarem, o Pi AI fica indisponível até recarregar ou ativar o Pro." to mapOf("en" to "Each simple task costs 2 credits. Longer requests can cost up to 5. When credits run out, Pi AI is unavailable until you top up or activate Pro.", "es" to "Cada tarea sencilla cuesta 2 créditos. Las solicitudes más largas pueden costar hasta 5. Cuando se agoten, Pi AI no estará disponible hasta recargar o activar Pro.", "fr" to "Chaque tâche simple coûte 2 crédits. Les demandes plus longues peuvent coûter jusqu'à 5. Une fois les crédits épuisés, Pi AI est indisponible jusqu'à une recharge ou l'activation de Pro."),
        "Pi AI Pro · " to mapOf("en" to "Pi AI Pro · ", "es" to "Pi AI Pro · ", "fr" to "Pi AI Pro · "),
        " créditos" to mapOf("en" to " credits", "es" to " créditos", "fr" to " crédits"),
        "Créditos de IA · " to mapOf("en" to "AI credits · ", "es" to "Créditos de IA · ", "fr" to "Crédits IA · ")
    )
}

@Composable
fun PiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = PiI18n.translate(text), modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}
