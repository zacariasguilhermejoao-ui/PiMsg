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
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPhone by remember { mutableStateOf(false) }
    var phoneVerified by remember { mutableStateOf(false) }
    var avatarKey by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var instagram by remember { mutableStateOf("") }
    var facebook by remember { mutableStateOf("") }
    var tiktok by remember { mutableStateOf("") }
    var youtube by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var ringColor by remember { mutableStateOf("#8E8E93") }
    var msg by remember { mutableStateOf<String?>(null) }
    var previewOpen by remember { mutableStateOf(false) }
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
            bio = j.strOrNull("bio").orEmpty()
            code = j.strOrNull("code").orEmpty()
            showPhone = j.optBoolean("show_phone")
            phoneVerified = j.optBoolean("phone_verified")
            avatarKey = j.strOrNull("avatar_key")
            instagram = j.strOrNull("instagram_url").orEmpty()
            facebook = j.strOrNull("facebook_url").orEmpty()
            tiktok = j.strOrNull("tiktok_url").orEmpty()
            youtube = j.strOrNull("youtube_url").orEmpty()
            website = j.strOrNull("website_url").orEmpty()
            ringColor = j.strOrNull("ring_color") ?: "#8E8E93"
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
                                .border(3.dp, runCatching { Color(android.graphics.Color.parseColor(ringColor)) }.getOrDefault(PiColors.Gray), androidx.compose.foundation.shape.CircleShape)
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

        Section("Personalização do perfil")
        OutlinedTextField(value = ringColor, onValueChange = { ringColor = it.take(7) }, label = { Text("Cor do perfil (HEX)") }, singleLine = true, supportingText = { Text("Exemplo: #8E8E93") }, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())

        Section("Redes sociais e website")
        Text("Adicione os seus perfis. Só serão apresentados no seu perfil público quando preencher os campos.", color = PiColors.TextSecondary, fontSize = 13.sp)
        OutlinedTextField(value = instagram, onValueChange = { instagram = it.take(200) }, label = { Text("Instagram (URL)") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = facebook, onValueChange = { facebook = it.take(200) }, label = { Text("Facebook (URL)") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tiktok, onValueChange = { tiktok = it.take(200) }, label = { Text("TikTok (URL)") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = youtube, onValueChange = { youtube = it.take(200) }, label = { Text("YouTube (URL)") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = website, onValueChange = { website = it.take(200) }, label = { Text("Website") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())

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
                        .put("instagram_url", instagram.trim())
                        .put("facebook_url", facebook.trim())
                        .put("tiktok_url", tiktok.trim())
                        .put("youtube_url", youtube.trim())
                        .put("website_url", website.trim())
                        .put("ring_color", ringColor.trim())
                    Api.patch("/v1/me", body)
                    msg = "Perfil guardado com sucesso."
                } catch (e: Exception) { msg = "Não foi possível guardar. Verifique as ligações e tente novamente." }
                saving = false
            }
        }, enabled = name.isNotBlank() && !saving)
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(24.dp))
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
                            modifier = Modifier.size(96.dp).border(3.dp, runCatching { Color(android.graphics.Color.parseColor(ringColor)) }.getOrDefault(PiColors.Gray), androidx.compose.foundation.shape.CircleShape).clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Person, contentDescription = "Avatar padrão", tint = PiColors.Gray, modifier = Modifier.size(72.dp))
                    }
                    Text(name.ifBlank { "O teu nome" }, color = PiColors.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (bio.isNotBlank()) Text(bio, color = PiColors.TextSecondary)
                    listOf("Instagram" to instagram, "Facebook" to facebook, "TikTok" to tiktok, "YouTube" to youtube, "Website" to website)
                        .filter { it.second.isNotBlank() }.forEach { Text(it.first, color = PiColors.White) }
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
    var aiLimit by remember { mutableStateOf(5) }
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
            aiLimit = data.optInt("ai_limit", 5)
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
            Text("Pi AI hoje: $aiUsage / $aiLimit respostas", color = PiColors.White, fontSize = 13.sp)
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
            listOf("Até 30 contactos", "Até 30 mensagens por dia", "5 respostas do Pi AI por dia", "Conversas e partilha de ficheiros").forEach {
                Text("•  $it", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            }
            if (current == "free") Text("Plano em utilização", color = PiColors.White, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("PRO", color = PiColors.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text("Mais capacidade", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            listOf("Mensagens sem limite diário", "30 respostas do Pi AI por dia", "Vídeo de apresentação no perfil").forEach {
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
