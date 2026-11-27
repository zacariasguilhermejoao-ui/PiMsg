package com.isabgroup.pimsg.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.isabgroup.pimsg.ui.PiText as Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.strOrNull
import com.isabgroup.pimsg.ui.PiButton
import com.isabgroup.pimsg.ui.PiFeedback
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.ScreenFrame
import com.isabgroup.pimsg.ui.openUrl
import com.isabgroup.pimsg.ui.playClick
import com.isabgroup.pimsg.ui.piFieldColors
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

private const val SITE = "https://pimsg.tchilopop.com"

private data class SocialLinkUi(val id: String, val label: String, val url: String, val favicon: android.graphics.Bitmap? = null)

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
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPhone by remember { mutableStateOf(false) }
    var phoneVerified by remember { mutableStateOf(false) }
    var avatarKey by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var email by remember { mutableStateOf("") }
    var emailVerified by remember { mutableStateOf(false) }
    var introVideoKey by remember { mutableStateOf<String?>(null) }
    var socialLinks by remember { mutableStateOf<List<SocialLinkUi>>(emptyList()) }
    var addSocialOpen by remember { mutableStateOf(false) }
    var socialUrl by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var previewOpen by remember { mutableStateOf(false) }
    val videoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                msg = "A enviar vídeo..."
                val uploaded = com.isabgroup.pimsg.data.uploadUri(context.contentResolver, uri)
                Api.patch("/v1/me/media", JSONObject().put("intro_video_media_id", uploaded.id))
                introVideoKey = uploaded.id
                msg = "Vídeo de apresentação actualizado."
            } catch (e: com.isabgroup.pimsg.data.ApiException) {
                msg = if (e.code == 402) "O vídeo de apresentação está disponível no plano Pro." else "Não foi possível guardar o vídeo."
            } catch (_: Exception) { msg = "Não foi possível guardar o vídeo." }
        }
    }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                msg = "A enviar fotografia..."
                val uploaded = com.isabgroup.pimsg.data.uploadUri(context.contentResolver, uri)
                Api.patch("/v1/me/media", JSONObject().put("avatar_media_id", uploaded.id))
                avatarKey = uploaded.id
                val downloaded = Api.download("/v1/media/${uploaded.id}")
                avatarBitmap = android.graphics.BitmapFactory.decodeByteArray(downloaded.bytes, 0, downloaded.bytes.size)
                msg = "Fotografia atualizada."
            } catch (e: Exception) { msg = "Não foi possível atualizar a fotografia." }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val j = Api.get("/v1/me")
            name = j.optString("name")
            email = j.strOrNull("email").orEmpty()
            emailVerified = j.optBoolean("email_verified")
            introVideoKey = j.strOrNull("intro_video_key")
            val linksResponse = Api.get("/v1/me/social-links")
            val rawLinks = linksResponse.optJSONArray("links") ?: org.json.JSONArray()
            val loadedLinks = (0 until rawLinks.length()).mapNotNull { index ->
                val item = rawLinks.optJSONObject(index) ?: return@mapNotNull null
                SocialLinkUi(item.optString("id"), item.optString("label"), item.optString("url"))
            }
            socialLinks = loadedLinks.map { link ->
                val icon = runCatching { Api.download("/v1/favicon?url=" + Api.encode(link.url)) }.getOrNull()
                val bitmap = icon?.let { android.graphics.BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) }
                link.copy(favicon = bitmap)
            }
            bio = j.strOrNull("bio").orEmpty()
            code = j.strOrNull("code").orEmpty()
            showPhone = j.optBoolean("show_phone")
            phoneVerified = j.optBoolean("phone_verified")
            avatarKey = j.strOrNull("avatar_key")
            avatarKey?.let { key ->
                try {
                    val d = Api.download("/v1/media/$key")
                    avatarBitmap = android.graphics.BitmapFactory.decodeByteArray(d.bytes, 0, d.bytes.size)
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            msg = "Não foi possível carregar o perfil."
        }
        loading = false
    }

    ScreenFrame("Perfil", onBack) {
        if (loading) return@ScreenFrame
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(116.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .height(108.dp)
                        .fillMaxWidth(0.34f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = avatarBitmap!!.asImageBitmap(),
                            contentDescription = "Fotografia do perfil",
                            modifier = Modifier
                                .size(104.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(PiColors.SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Person,
                                contentDescription = "Avatar padrão",
                                tint = PiColors.Gray,
                                modifier = Modifier.size(54.dp),
                            )
                        }
                    }
                }
            }
            PiButton("Alterar fotografia", { imagePicker.launch("image/*") }, primary = false)
            if (avatarKey != null) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            Api.patch("/v1/me/media", JSONObject().put("avatar_media_id", JSONObject.NULL))
                            avatarKey = null
                            avatarBitmap = null
                            msg = "Fotografia removida."
                        } catch (e: Exception) { msg = "Não foi possível remover a fotografia." }
                    }
                }) { Text("Remover fotografia", color = PiColors.Danger) }
            }
        }

        Section("Informações pessoais")
        OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Nome") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = bio, onValueChange = { bio = it.take(300) }, label = { Text("Biografia / apresentação") }, minLines = 3, supportingText = { Text("${bio.length}/300") }, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())

        Section("E-mail e verificação")
        OutlinedTextField(value = email, onValueChange = { email = it.take(180); if (it.trim() != FirebaseAuth.getInstance().currentUser?.email) emailVerified = false }, label = { Text("E-mail") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Text(if (emailVerified) "E-mail confirmado · Perfil profissional activo" else "Confirme o seu e-mail para activar o perfil profissional.", color = if (emailVerified) Color(0xFF8AB4F8) else PiColors.TextSecondary, fontSize = 12.sp)
        if (!emailVerified) {
            PiButton("Enviar confirmação", {
                scope.launch {
                    try {
                        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Sessão indisponível")
                        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) throw IllegalArgumentException("Introduza um e-mail válido.")
                        if (user.email != email.trim()) user.updateEmail(email.trim()).await()
                        user.sendEmailVerification().await()
                        msg = "Enviámos uma ligação de confirmação para o seu e-mail."
                    } catch (e: Exception) { msg = e.localizedMessage ?: "Não foi possível enviar a confirmação. Volte a iniciar sessão e tente novamente." }
                }
            }, primary = false)
            TextButton(onClick = {
                scope.launch {
                    try {
                        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Sessão indisponível")
                        user.reload().await()
                        user.getIdToken(true).await()
                        emailVerified = Api.get("/v1/me").optBoolean("email_verified")
                        msg = if (emailVerified) "E-mail confirmado. O perfil profissional está activo." else "Ainda não recebemos a confirmação. Abra a ligação enviada por e-mail e tente novamente."
                    } catch (_: Exception) { msg = "Não foi possível verificar o estado do e-mail." }
                }
            }) { Text("Já confirmei o e-mail", color = PiColors.White) }
        }

        Section("Vídeo de apresentação")
        Text("Apresente-se às pessoas que visitam o seu perfil.", color = PiColors.TextSecondary, fontSize = 13.sp)
        PiButton(if (introVideoKey == null) "Adicionar vídeo" else "Substituir vídeo", { videoPicker.launch("video/*") }, primary = false)
        if (introVideoKey != null) {
            Text("Vídeo adicionado ao perfil", color = PiColors.TextSecondary, fontSize = 12.sp)
            TextButton(onClick = { scope.launch { runCatching { Api.patch("/v1/me/media", JSONObject().put("intro_video_media_id", JSONObject.NULL)) }; introVideoKey = null; msg = "Vídeo removido." } }) { Text("Remover vídeo", color = PiColors.Danger) }
        }

        Section("Redes sociais")
        Text("Adicione os links que pretende partilhar no seu perfil.", color = PiColors.TextSecondary, fontSize = 13.sp)
        socialLinks.forEach { link ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (link.favicon != null) androidx.compose.foundation.Image(link.favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)) else Text("↗", color = PiColors.White, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f).clickable { openUrl(context, link.url) }.padding(start = 10.dp)) {
                    Text(link.label, color = PiColors.White, fontSize = 14.sp)
                    Text(link.url, color = PiColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                }
                IconButton(onClick = { scope.launch { runCatching { Api.delete("/v1/me/social-links/" + link.id) }; socialLinks = socialLinks.filterNot { it.id == link.id } } }) { Icon(Icons.Filled.Delete, contentDescription = "Remover ligação", tint = PiColors.Gray) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { socialUrl = ""; addSocialOpen = true }) { Icon(Icons.Filled.Add, contentDescription = "Adicionar Redes Sociais", tint = PiColors.White) }
        }
        Section("O meu código PiMsg")
        Text(code, color = PiColors.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Text("Partilhe este código para que outras pessoas o encontrem no PiMsg.", color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mostrar o meu número no perfil", color = PiColors.White, fontSize = 16.sp)
                if (!phoneVerified) Text("Disponível quando o número estiver confirmado.", color = PiColors.Gray, fontSize = 13.sp)
            }
            Switch(
                checked = showPhone, onCheckedChange = { showPhone = it }, enabled = phoneVerified,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White,
                    uncheckedThumbColor = PiColors.Gray, uncheckedTrackColor = PiColors.SurfaceHigh,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))
        PiButton("Ver como os outros veem o meu perfil", { previewOpen = true }, primary = false)
        Spacer(Modifier.height(12.dp))
        PiButton("Guardar alterações", {
            scope.launch {
                saving = true
                try {
                    val body = JSONObject()
                        .put("name", name)
                        .put("bio", bio)
                        .put("show_phone", showPhone)
                    Api.patch("/v1/me", body)
                    msg = "Perfil guardado com sucesso."
                } catch (e: Exception) { msg = "Não foi possível guardar. Verifique as ligações e tente novamente." }
                saving = false
            }
        }, enabled = name.isNotBlank() && !saving)
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(24.dp))
    }
    if (addSocialOpen) {
        AlertDialog(
            onDismissRequest = { addSocialOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Adicionar rede social", color = PiColors.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = socialUrl, onValueChange = { socialUrl = it.take(500) }, label = { Text("Ligação do site ou perfil") }, singleLine = true, colors = piFieldColors())
                    Text("O ícone do site será carregado automaticamente quando existir.", color = PiColors.TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val result = Api.post("/v1/me/social-links", JSONObject().put("url", socialUrl))
                            val url = result.getString("url")
                            val icon = runCatching { Api.download("/v1/favicon?url=" + Api.encode(url)) }.getOrNull()
                            val bitmap = icon?.let { android.graphics.BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) }
                            socialLinks = socialLinks + SocialLinkUi(result.getString("id"), result.getString("label"), url, bitmap)
                            addSocialOpen = false
                            msg = "Ligação adicionada."
                        } catch (_: Exception) { msg = "Não foi possível adicionar. Confirme se a ligação começa por https://." }
                    }
                }) { Text("Adicionar", color = PiColors.White) }
            },
            dismissButton = { TextButton(onClick = { addSocialOpen = false }) { Text("Cancelar", color = PiColors.TextSecondary) } },
        )
    }
    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Pré-visualização do perfil", color = PiColors.White) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (avatarBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = avatarBitmap!!.asImageBitmap(), contentDescription = "Fotografia pública",
                            modifier = Modifier.size(96.dp).clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Person, contentDescription = "Avatar padrão", tint = PiColors.Gray, modifier = Modifier.size(72.dp))
                    }
                    Text(name.ifBlank { "O teu nome" }, color = PiColors.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (bio.isNotBlank()) Text(bio, color = PiColors.TextSecondary)
                    if (emailVerified) Text("Perfil profissional", color = Color(0xFF8AB4F8), fontSize = 12.sp)
                    socialLinks.forEach { Text(it.label, color = PiColors.White) }
                    if (introVideoKey != null) Text("Vídeo de apresentação adicionado", color = PiColors.TextSecondary, fontSize = 12.sp)
                    Text("Esta é uma pré-visualização do que outras pessoas podem ver.", color = PiColors.TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { previewOpen = false }) { Text("Fechar", color = PiColors.White) } },
        )
    }
}

// ---------------------------------------------------------------- Definições
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit, onSecurity: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var soundEnabled by remember { mutableStateOf(PiFeedback.soundEnabled(context)) }
    var hapticEnabled by remember { mutableStateOf(PiFeedback.hapticEnabled(context)) }

    suspend fun loadBlocked() {
        try {
            val arr = Api.get("/v1/blocks").getJSONArray("users")
            blocked = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getString("name") } }
        } catch (e: Exception) { /* lista vazia */ }
    }
    LaunchedEffect(Unit) { loadBlocked() }

    ScreenFrame("Definições", onBack) {
        Section("Som e vibração")
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Som ao tocar", color = PiColors.White, fontSize = 16.sp)
                Text("Efeitos sonoros discretos nos controlos principais", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = soundEnabled, onCheckedChange = {
                soundEnabled = it
                PiFeedback.setSoundEnabled(context, it)
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vibração ao tocar", color = PiColors.White, fontSize = 16.sp)
                Text("Resposta tátil nos botões e ações principais", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = hapticEnabled, onCheckedChange = {
                hapticEnabled = it
                PiFeedback.setHapticEnabled(context, it)
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White))
        }

        TextButton(onClick = { view.playClick() }, modifier = Modifier.fillMaxWidth()) {
            Text("Testar som e vibração", color = Color(0xFF8AB4F8))
        }

        Section("Segurança")
        Text("Segurança da conta", color = PiColors.White, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable { onSecurity() }.padding(vertical = 12.dp))

        Section("Contas bloqueadas")
        if (blocked.isEmpty()) Text("Não bloqueou ninguém.", color = PiColors.TextSecondary, fontSize = 15.sp)
        blocked.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = PiColors.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { runCatching { Api.delete("/v1/blocks/$id") }; loadBlocked() } }) {
                    Text("Desbloquear", color = PiColors.White)
                }
            }
        }

        Section("Informações legais")
        listOf("Termos de uso" to "terms", "Política de privacidade" to "privacy", "Política de reembolso" to "refund", "Regras da comunidade" to "community", "Suporte" to "support").forEach { (label, path) ->
            Text(label, color = PiColors.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { openUrl(context, "$SITE/$path") }.padding(vertical = 12.dp))
        }

        Section("Conta")
        Text("Sair", color = PiColors.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onSignOut() }.padding(vertical = 12.dp))
        Text("Apagar conta", color = PiColors.Danger, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(vertical = 12.dp))
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
private data class LinkItem(val id: String, val subject: String, val url: String, val opens: Int, val imageMediaId: String?)

@Composable
fun LinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var links by remember { mutableStateOf<List<LinkItem>>(emptyList()) }
    var subject by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var imageMediaId by remember { mutableStateOf<String?>(null) }
    var imagePreview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var linkReplyUsed by remember { mutableStateOf(0) }
    var linkReplyRemaining by remember { mutableStateOf<Int?>(500) }
    var linkReplyResetAt by remember { mutableStateOf<Long?>(null) }
    val linkImagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                msg = "A enviar imagem..."
                val uploaded = com.isabgroup.pimsg.data.uploadUri(context.contentResolver, uri)
                if (!uploaded.mime.startsWith("image/")) throw IllegalArgumentException("Escolha uma imagem.")
                imageMediaId = uploaded.id
                val downloaded = Api.download("/v1/media/\u0024{uploaded.id}")
                imagePreview = android.graphics.BitmapFactory.decodeByteArray(downloaded.bytes, 0, downloaded.bytes.size)
                msg = "Imagem adicionada ao link."
            } catch (e: Exception) { msg = "Não foi possível adicionar a imagem." }
        }
    }

    suspend fun load() {
        try {
            val planInfo = Api.get("/v1/plan")
            linkReplyUsed = planInfo.optInt("personalized_link_reply_usage", 0)
            linkReplyRemaining = if (planInfo.isNull("personalized_link_reply_remaining")) null else planInfo.optInt("personalized_link_reply_remaining")
            linkReplyResetAt = if (planInfo.isNull("personalized_link_reply_resets_at")) null else planInfo.optLong("personalized_link_reply_resets_at")
            val arr = Api.get("/v1/links").getJSONArray("links")
            links = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LinkItem(o.getString("id"), o.getString("subject"), o.getString("url"), o.optInt("opens_count"), o.optString("image_media_id").takeIf { it.isNotBlank() && it != "null" })
            }
        } catch (e: Exception) { msg = "Não foi possível carregar os links." }
    }
    LaunchedEffect(Unit) { load() }

    ScreenFrame("Meus links", onBack) {
        Text(
            "Crie um link para cada produto ou assunto. Quando alguém o abre e gera o código, o PiMsg avisa-o e já fica a saber do que se trata.",
            color = PiColors.TextSecondary, fontSize = 15.sp,
        )
        if (linkReplyRemaining != null) {
            Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text("Respostas através dos seus links", color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$linkReplyUsed / 500 respostas utilizadas este mês", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { (linkReplyUsed / 500f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = PiColors.White, trackColor = PiColors.SurfaceHigh)
                Text(if ((linkReplyRemaining ?: 1) == 0) "Limite atingido. Poderá responder novamente e criar links no dia 1, às 00h00 (hora de Angola), ou ativar o Pro." else "As conversas normais continuam disponíveis. O limite renova no dia 1, às 00h00 (hora de Angola).", color = if ((linkReplyRemaining ?: 1) == 0) PiColors.Danger else PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = subject, onValueChange = { subject = it.take(80) }, label = { Text("Assunto ou produto") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = intro, onValueChange = { intro = it.take(400) }, label = { Text("Descrição na página do link (opcional)") }, minLines = 2, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PiButton("Adicionar fotografia ao link", { linkImagePicker.launch("image/*") }, primary = false)
        imagePreview?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Imagem do link",
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            TextButton(onClick = { imageMediaId = null; imagePreview = null }) { Text("Remover imagem", color = PiColors.Danger) }
        }
        Spacer(Modifier.height(12.dp))
        PiButton("Criar link", {
            scope.launch {
                try {
                    Api.post("/v1/links", JSONObject().put("subject", subject).put("intro_text", intro).put("image_media_id", imageMediaId ?: JSONObject.NULL))
                    subject = ""; intro = ""; imageMediaId = null; imagePreview = null; msg = "Link criado com sucesso."; load()
                } catch (e: Exception) { msg = "Não foi possível criar o link." }
            }
        }, enabled = subject.isNotBlank())
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }

        Section("Os meus links")
        if (links.isEmpty()) Text("Ainda não criou nenhum link.", color = PiColors.TextSecondary, fontSize = 15.sp)
        links.forEach { l ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(l.subject, color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                if (l.imageMediaId != null) Text("Tem fotografia personalizada", color = PiColors.TextSecondary, fontSize = 12.sp)
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


// ---------------------------------------------------------------- Planos
@Composable
fun PlansScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf("free") }
    var status by remember { mutableStateOf("free") }
    var expiresAt by remember { mutableStateOf<Long?>(null) }
    var messageUsage by remember { mutableStateOf(0) }
    var aiUsage by remember { mutableStateOf(0) }
    var messageLimit by remember { mutableStateOf<Int?>(30) }
    var aiLimit by remember { mutableStateOf(5) }\n    var linkReplyUsage by remember { mutableStateOf(0) }\n    var linkReplyLimit by remember { mutableStateOf<Int?>(500) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val data = Api.get("/v1/plan")
            current = data.optString("current_plan", "free")
            status = data.optString("subscription_status", "free")
            expiresAt = if (data.isNull("expires_at")) null else data.optLong("expires_at")
            messageUsage = data.optInt("message_usage")
            aiUsage = data.optInt("ai_usage")
            messageLimit = if (data.isNull("message_limit")) null else data.optInt("message_limit")
            aiLimit = data.optInt("ai_limit", 5)\n            linkReplyUsage = data.optInt("personalized_link_reply_usage", 0)\n            linkReplyLimit = if (data.isNull("personalized_link_reply_limit")) null else data.optInt("personalized_link_reply_limit")
        } catch (e: Exception) { error = "Não foi possível consultar o plano da conta." }
        finally { loading = false }
    }

    ScreenFrame("Planos", onBack) {
        Text("Escolha o nível de acesso adequado à sua utilização do PiMsg.", color = PiColors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text(if (current == "free") "Plano atual · Free" else "Plano atual · Pro", color = PiColors.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (current == "free") "Está a utilizar o plano gratuito." else "A sua subscrição Pro está ativa.",
                color = PiColors.TextSecondary, fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("Mensagens hoje: $messageUsage" + (messageLimit?.let { " / $it" } ?: " · sem limite diário"), color = PiColors.White, fontSize = 13.sp)
            Text("Pi AI hoje: $aiUsage / $aiLimit respostas", color = PiColors.White, fontSize = 13.sp)\n            Text("Respostas através dos links este mês: $linkReplyUsage" + (linkReplyLimit?.let { " / $it" } ?: " · sem limite"), color = PiColors.White, fontSize = 13.sp)
            if (expiresAt != null) {
                val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(expiresAt!!))
                Text("Válido até $date", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (current != "free") {
                val statusLabel = when (status) {
                    "active" -> "Ativo"
                    "grace" -> "Em período de tolerância"
                    "on_hold" -> "Suspenso pela Google Play"
                    else -> "Estado da subscrição indisponível"
                }
                Text("Estado: $statusLabel", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("FREE", color = PiColors.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text("Acesso essencial", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            listOf("Até 30 contactos", "Até 30 mensagens por dia", "500 respostas por mês através dos links personalizados", "5 respostas do Pi AI por dia", "Conversas e partilha de ficheiros").forEach {
                Text("•  $it", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            }
            if (current == "free") Text("Plano em utilização", color = PiColors.White, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("PRO", color = PiColors.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text("Mais capacidade", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            listOf("Mensagens sem limite diário", "Respostas ilimitadas através dos links personalizados", "30 respostas do Pi AI por dia", "Vídeo de apresentação no perfil").forEach {
                Text("•  $it", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f).background(PiColors.SurfaceHigh, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("Pro semanal", color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Acesso por semana", color = PiColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(Modifier.weight(1f).background(PiColors.SurfaceHigh, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("Pro mensal", color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Acesso por mês", color = PiColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text(
                "A consulta dos planos já está disponível. A adesão e os preços só serão apresentados quando os produtos de subscrição estiverem publicados e validados na Google Play.",
                color = PiColors.Gray, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp),
            )
            if (current != "free") {
                PiButton("Gerir subscrição na Google Play", {
                    openUrl(context, "https://play.google.com/store/account/subscriptions")
                }, primary = false)
            }
        }
        error?.let { Text(it, color = PiColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        if (loading) Text("A consultar a sua conta…", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(20.dp))
    }
}
