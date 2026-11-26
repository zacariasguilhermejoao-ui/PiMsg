package com.isabgroup.pimsg.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.isabgroup.pimsg.data.Push
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.strOrNull
import com.isabgroup.pimsg.ui.PiButton
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.ScreenFrame
import com.isabgroup.pimsg.ui.openUrl
import com.isabgroup.pimsg.ui.piFieldColors
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val SITE = "https://pimsg.tchilopop.com"

private fun copy(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun Section(text: String) {
    Text(text, color = PiColors.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

// ---------------------------------------------------------------- Perfil
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPhone by remember { mutableStateOf(false) }
    var phoneVerified by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val j = Api.get("/v1/me")
            name = j.getString("name")
            bio = j.strOrNull("bio").orEmpty()
            code = j.strOrNull("code").orEmpty()
            showPhone = j.optBoolean("show_phone")
            phoneVerified = j.optBoolean("phone_verified")
        } catch (e: Exception) {
            msg = "Não foi possível carregar o perfil."
        }
        loading = false
    }

    ScreenFrame("Perfil", onBack) {
        if (loading) return@ScreenFrame
        OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Nome") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = bio, onValueChange = { bio = it.take(300) }, label = { Text("Apresentação") }, minLines = 3, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())

        Section("O meu código")
        Text(code, color = PiColors.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Text("Quem tiver este código encontra-o na pesquisa. Ao gerar um novo, o anterior deixa de valer.", color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PiButton("Copiar", { copy(context, "codigo", code); msg = "Código copiado." }, Modifier.weight(1f), primary = false)
            PiButton("Gerar novo", {
                scope.launch {
                    try { code = Api.post("/v1/me/code/regenerate", JSONObject()).getString("code"); msg = "Novo código criado." }
                    catch (e: Exception) { msg = "Não foi possível gerar o código." }
                }
            }, Modifier.weight(1f), primary = false)
        }

        Section("Privacidade")
        Row(Modifier.// ---------------------------------------------------------------- Definições
private data class SettingsEntry(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
private fun SettingsRow(entry: SettingsEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = Color(0xFF929298),
            modifier = Modifier.padding(end = 16.dp).height(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(entry.description, color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF66666D),
        )
    }
}

@Composable
private fun SettingsInfo(title: String, description: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .background(PiColors.Surface, RoundedCornerShape(14.dp)).padding(16.dp),
    ) {
        Text(title, color = PiColors.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = PiColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<String?>(null) }
    var blocked by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pushEnabled by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    suspend fun loadBlocked() {
        try {
            val arr = Api.get("/v1/blocks").getJSONArray("users")
            blocked = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getString("name") } }
        } catch (_: Exception) { msg = "Não foi possível carregar as contas bloqueadas." }
    }

    val pushPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pushEnabled = granted
        if (granted) scope.launch {
            try { Push.register(context); msg = "Notificações ativadas neste dispositivo." }
            catch (_: Exception) { msg = "Permissão concedida, mas não foi possível registar o dispositivo. Tente novamente." }
        } else msg = "A permissão de notificações continua desativada nas definições do Android."
    }

    LaunchedEffect(selected) {
        if (selected == "privacy") loadBlocked()
    }

    val entries = listOf(
        SettingsEntry("subscription", "Subscrições", "Explore os benefícios premium", Icons.Outlined.WorkspacePremium),
        SettingsEntry("devices", "Dispositivos associados", "Use a app noutros dispositivos", Icons.Outlined.Devices),
        SettingsEntry("account", "Conta", "Notificações de segurança, alterar número", Icons.Outlined.ManageAccounts),
        SettingsEntry("privacy", "Privacidade", "Contas bloqueadas, mensagens temporárias", Icons.Outlined.Shield),
        SettingsEntry("lists", "Listas", "Gira pessoas e grupos", Icons.Outlined.Groups),
        SettingsEntry("conversations", "Conversas", "Tema, fundos, histórico de conversas", Icons.Outlined.Forum),
        SettingsEntry("appearance", "Aspeto", "Tema da conversa, ícone da app, tema da app", Icons.Outlined.Palette),
        SettingsEntry("notifications", "Notificações", "Toques de mensagens, grupos e chamadas", Icons.Outlined.Notifications),
        SettingsEntry("storage", "Armazenamento e dados", "Utilização da rede, descarregar automaticamente", Icons.Outlined.Storage),
        SettingsEntry("accessibility", "Acessibilidade", "Aumentar contraste, animação", Icons.Outlined.AccessibilityNew),
        SettingsEntry("language", "Idioma da app", "Português (Portugal) (idioma do dispositivo)", Icons.Outlined.Language),
        SettingsEntry("help", "Ajuda e feedback", "Centro de Ajuda, contacte-nos, Política de Privacidade", Icons.Outlined.HelpOutline),
        SettingsEntry("invite", "Convidar um contacto", "Convide pessoas para o PiMsg", Icons.Outlined.PersonAddAlt1),
    )

    val pageTitle = entries.firstOrNull { it.key == selected }?.title ?: "Definições"
    ScreenFrame(pageTitle, { if (selected != null) selected = null else onBack() }) {
        if (selected == null) {
            Text("Preferências", color = PiColors.Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            entries.forEachIndexed { index, entry ->
                SettingsRow(entry) {
                    if (entry.key == "invite") {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Vamos conversar no PiMsg. Instale a aplicação: https://play.google.com/store/apps/details?id=com.pimsg.isabstudio")
                        context.startActivity(Intent.createChooser(send, "Convidar um contacto"))
                    } else selected = entry.key
                }
                if (index != entries.lastIndex) androidx.compose.material3.HorizontalDivider(color = PiColors.SurfaceHigh.copy(alpha = 0.75f), thickness = 0.7.dp)
            }
            Spacer(Modifier.height(22.dp))
            Section("Sessão")
            Text("Sair", color = PiColors.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { onSignOut() }.padding(vertical = 12.dp))
            Text("Apagar conta", color = PiColors.Danger, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(vertical = 12.dp))
        } else {
            when (selected) {
                "subscription" -> {
                    SettingsInfo("PiMsg Premium", "Explore os benefícios premium e as opções de subscrição disponíveis na aplicação.")
                    SettingsInfo("Benefícios", "Mais liberdade para conversar, limites ampliados e funcionalidades exclusivas do plano Pro.")
                    Text("Consulte os planos e preços apresentados na Google Play antes de subscrever.", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    PiButton("Ver PiMsg na Google Play", { openUrl(context, "https://play.google.com/store/apps/details?id=com.pimsg.isabstudio") }, primary = false)
                }
                "devices" -> {
                    SettingsInfo("Este dispositivo", "O PiMsg pode registar este aparelho para receber notificações push quando a permissão estiver ativa.")
                    SettingsInfo("Outros dispositivos", "A gestão de sessões e a remoção remota de dispositivos ainda não estão disponíveis nesta versão.")
                }
                "account" -> {
                    SettingsInfo("Segurança da conta", "Mantenha o acesso à sua conta Google ou ao número de telefone associado protegido.")
                    SettingsInfo("Alterar número", "A alteração do número exige um fluxo de confirmação por SMS. Essa operação ainda não está disponível nesta versão.")
                    Text("Para questões sobre acesso à conta, contacte o suporte.", color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                    PiButton("Contactar suporte", { openUrl(context, "mailto:suporte@tchilopop.com") }, primary = false)
                }
                "privacy" -> {
                    Section("Contas bloqueadas")
                    if (blocked.isEmpty()) Text("Não bloqueou ninguém.", color = PiColors.TextSecondary, fontSize = 14.sp)
                    blocked.forEach { (id, name) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = PiColors.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { runCatching { Api.delete("/v1/blocks/$id") }; loadBlocked() } }) {
                                Text("Desbloquear", color = PiColors.White)
                            }
                        }
                    }
                    Section("Mensagens temporárias")
                    SettingsInfo("Duração das mensagens", "A duração das mensagens temporárias é configurada dentro de cada conversa, através das opções dessa conversa.")
                }
                "lists" -> SettingsInfo("Pessoas e grupos", "As conversas individuais e os grupos que criar ou integrar aparecem no ecrã principal do PiMsg. A gestão de listas personalizadas ainda não está disponível.")
                "conversations" -> SettingsInfo("Personalização das conversas", "As opções de tema, fundos e histórico de conversas ainda não estão disponíveis nesta versão.")
                "appearance" -> SettingsInfo("Personalizar o PiMsg", "A escolha do tema da aplicação, do fundo das conversas e do ícone da app ainda não está disponível nesta versão.")
                "notifications" -> {
                    SettingsInfo("Notificações push", "Permita notificações para receber alertas de novas mensagens mesmo quando o PiMsg não está aberto.")
                    Text(
                        if (pushEnabled) "Permissão de notificações: ativada" else "Permissão de notificações: desativada",
                        color = if (pushEnabled) Color(0xFF9AA0A6) else PiColors.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    PiButton(
                        if (pushEnabled) "Registar este dispositivo" else "Ativar notificações",
                        {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else scope.launch {
                                try { Push.register(context); pushEnabled = true; msg = "Dispositivo registado para notificações push." }
                                catch (_: Exception) { msg = "Não foi possível registar as notificações. Verifique a ligação e tente novamente." }
                            }
                        },
                        primary = false,
                    )
                    SettingsInfo("Toques e categorias", "Os controlos separados para toques de mensagens, grupos e chamadas ainda não estão ligados ao sistema de notificações.")
                }
                "storage" -> SettingsInfo("Armazenamento e dados", "Os ficheiros multimédia são enviados quando escolhe partilhá-los. O descarregamento automático e o relatório detalhado de utilização da rede ainda não estão disponíveis.")
                "accessibility" -> SettingsInfo("Acessibilidade", "As opções de contraste reforçado e redução de animações ainda não estão disponíveis nesta versão.")
                "language" -> SettingsInfo("Idioma da aplicação", "Português (Portugal) — a aplicação acompanha o idioma do dispositivo nesta versão.")
                "help" -> {
                    SettingsInfo("Centro de Ajuda", "Consulte os documentos oficiais ou contacte a equipa PiMsg.")
                    listOf(
                        "Centro de Ajuda" to "$SITE/support",
                        "Contacte-nos" to "mailto:suporte@tchilopop.com",
                        "Política de Privacidade" to "$SITE/privacy",
                    ).forEach { (label, url) ->
                        Text(label, color = PiColors.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { openUrl(context, url) }.padding(vertical = 13.dp))
                    }
                }
            }
            msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = PiColors.Surface,
            title = { Text("Apagar conta", color = PiColors.White) },
            text = {
                Text(
                    "O pedido fica pendente durante 14 dias. Se entrar de novo nesse período, pode cancelar. Depois, os seus dados são removidos.",
                    color = PiColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try { Api.post("/v1/me/delete", JSONObject()); onSignOut() }
                        catch (e: Exception) { msg = "Não foi possível enviar o pedido." }
                    }
                }) { Text("Apagar", color = PiColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = PiColors.White) } },
        )
    }
}

// ---------------------------------------------------------------- Meus links
, color = PiColors.Danger, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(vertical = 12.dp))
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = PiColors.Surface,
            title = { Text("Apagar conta", color = PiColors.White) },
            text = {
                Text(
                    "O pedido fica pendente durante 14 dias. Se entrar de novo nesse período, pode cancelar. Depois, os seus dados são removidos.",
                    color = PiColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try { Api.post("/v1/me/delete", JSONObject()); onSignOut() }
                        catch (e: Exception) { msg = "Não foi possível enviar o pedido." }
                    }
                }) { Text("Apagar", color = PiColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = PiColors.White) } },
        )
    }
}

// ---------------------------------------------------------------- Meus links
private data class LinkItem(val id: String, val subject: String, val url: String, val opens: Int)

@Composable
fun LinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var links by remember { mutableStateOf<List<LinkItem>>(emptyList()) }
    var subject by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            val arr = Api.get("/v1/links").getJSONArray("links")
            links = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LinkItem(o.getString("id"), o.getString("subject"), o.getString("url"), o.optInt("opens_count"))
            }
        } catch (e: Exception) { msg = "Não foi possível carregar os links." }
    }
    LaunchedEffect(Unit) { load() }

    ScreenFrame("Meus links", onBack) {
        Text(
            "Crie um link para cada produto ou assunto. Quando alguém o abre e gera o código, o PiMsg avisa-o e já fica a saber do que se trata.",
            color = PiColors.TextSecondary, fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = subject, onValueChange = { subject = it.take(80) }, label = { Text("Assunto ou produto") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = intro, onValueChange = { intro = it.take(400) }, label = { Text("Texto na página do link (opcional)") }, minLines = 2, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        PiButton("Criar link", {
            scope.launch {
                try {
                    Api.post("/v1/links", JSONObject().put("subject", subject).put("intro_text", intro))
                    subject = ""; intro = ""; msg = null; load()
                } catch (e: Exception) { msg = "Não foi possível criar o link." }
            }
        }, enabled = subject.isNotBlank())
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }

        Section("Os meus links")
        if (links.isEmpty()) Text("Ainda não criou nenhum link.", color = PiColors.TextSecondary, fontSize = 15.sp)
        links.forEach { l ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(l.subject, color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("${l.url}  ·  ${l.opens} aberturas", color = PiColors.TextSecondary, fontSize = 13.sp)
                Row {
                    TextButton(onClick = { copy(context, "link", l.url); msg = "Link copiado." }) { Text("Copiar", color = PiColors.White) }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "Fale comigo sobre ${l.subject} no PiMsg: ${l.url}")
                        context.startActivity(Intent.createChooser(send, "Partilhar link"))
                    }) { Text("Partilhar", color = PiColors.White) }
                    TextButton(onClick = { scope.launch { runCatching { Api.delete("/v1/links/${l.id}") }; load() } }) { Text("Apagar", color = PiColors.Danger) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
