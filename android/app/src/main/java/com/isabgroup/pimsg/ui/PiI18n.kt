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
        "Cancelar" to mapOf("en" to "Cancel", "es" to "Cancelar", "fr" to "Annuler")
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
