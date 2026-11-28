package com.isabgroup.pimsg.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.net.TrafficStats
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.isabgroup.pimsg.R
import com.isabgroup.pimsg.chat.ChatRef
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.PiMsgMedia
import com.isabgroup.pimsg.data.uploadBitmap
import com.isabgroup.pimsg.data.decodeBitmapUri
import com.isabgroup.pimsg.ui.components.ImageCropDialog
import com.isabgroup.pimsg.data.PlayBilling
import com.android.billingclient.api.ProductDetails
import com.isabgroup.pimsg.data.Push
import com.isabgroup.pimsg.data.strOrNull
import com.isabgroup.pimsg.ui.PiButton
import com.isabgroup.pimsg.ui.PiFeedback
import com.isabgroup.pimsg.ui.PiI18n
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.ScreenFrame
import com.isabgroup.pimsg.ui.openUrl
import com.isabgroup.pimsg.ui.playClick
import com.isabgroup.pimsg.ui.piFieldColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import java.io.ByteArrayOutputStream
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
fun ProfileScreen(onBack: () -> Unit, onOpenChat: (ChatRef) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var mediaUploading by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPhone by remember { mutableStateOf(false) }
    var showOnlineStatus by remember { mutableStateOf(true) }
    var phoneVerified by remember { mutableStateOf(false) }
    var phoneContact by remember { mutableStateOf<String?>(null) }
    var avatarKey by remember { mutableStateOf<String?>(null) }
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var avatarCropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var email by remember { mutableStateOf("") }
    var emailVerified by remember { mutableStateOf(false) }
    var introVideoKey by remember { mutableStateOf<String?>(null) }
    var socialLinks by remember { mutableStateOf<List<SocialLinkUi>>(emptyList()) }
    var addSocialOpen by remember { mutableStateOf(false) }
    var socialUrl by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var previewOpen by remember { mutableStateOf(false) }
    val profileCache = remember { context.getSharedPreferences("pimsg_profile_cache", Context.MODE_PRIVATE) }
    val videoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            mediaUploading = true
            msg = "A preparar e enviar vídeo..."
            try {
                val uploaded = com.isabgroup.pimsg.data.uploadUri(context.contentResolver, uri)
                Api.patch("/v1/me/media", JSONObject().put("intro_video_media_id", uploaded.id))
                introVideoKey = uploaded.id
                msg = "Vídeo de apresentação actualizado."
            } catch (e: com.isabgroup.pimsg.data.ApiException) {
                msg = if (e.code == 402) "O vídeo de apresentação está disponível no plano Pro." else "Não foi possível guardar o vídeo."
            } catch (_: Exception) { msg = "Não foi possível guardar o vídeo." }
            finally { mediaUploading = false }
        }
    }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            val selected = runCatching { decodeBitmapUri(context.contentResolver, uri) }.getOrNull()
            if (selected == null) msg = "Não foi possível abrir esta fotografia."
            else avatarCropSource = selected
        }
    }

    LaunchedEffect(Unit) {
        val cached = profileCache.getString("profile", null)
        if (!cached.isNullOrBlank()) {
            runCatching {
                val c = JSONObject(cached)
                name = c.optString("name")
                bio = c.optString("bio")
                email = c.optString("email")
                emailVerified = c.optBoolean("email_verified")
                introVideoKey = c.optString("intro_video_key").takeIf { it.isNotBlank() && it != "null" }
                code = c.optString("code")
                showPhone = c.optBoolean("show_phone")
                showOnlineStatus = c.optBoolean("show_online_status", true)
                phoneVerified = c.optBoolean("phone_verified")
                phoneContact = c.optString("phone_contact").takeIf { it.isNotBlank() && it != "null" }
                avatarKey = c.optString("avatar_key").takeIf { it.isNotBlank() && it != "null" }
                val encoded = c.optString("avatar_jpeg")
                if (encoded.isNotBlank()) {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    avatarBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    avatarKey?.let { key ->
                        PiMsgMedia.findCached(context, key)?.let { avatarBitmap = BitmapFactory.decodeFile(it.absolutePath) }
                    }
                }
                val links = c.optJSONArray("social_links") ?: org.json.JSONArray()
                socialLinks = (0 until links.length()).map { i ->
                    val item = links.getJSONObject(i)
                    SocialLinkUi(item.optString("id"), item.optString("label"), item.optString("url"))
                }
                loading = false
            }
        }
        if (!Api.hasInternet(context)) {
            loading = false
            return@LaunchedEffect
        }
        try {
            val j = Api.get("/v1/me")
            name = j.optString("name")
            email = j.strOrNull("email").orEmpty()
            emailVerified = j.optBoolean("email_verified")
            introVideoKey = j.strOrNull("intro_video_key")
            bio = j.strOrNull("bio").orEmpty()
            code = j.strOrNull("code").orEmpty()
            showPhone = j.optBoolean("show_phone")
            showOnlineStatus = j.optBoolean("show_online_status", true)
            phoneVerified = j.optBoolean("phone_verified")
            phoneContact = j.strOrNull("phone_contact")
            avatarKey = j.strOrNull("avatar_key")
            // Mostra já os dados básicos; os elementos secundários são sincronizados depois.
            loading = false
            avatarKey?.let { key ->
                PiMsgMedia.findCached(context, key)?.let {
                    avatarBitmap = BitmapFactory.decodeFile(it.absolutePath)
                }
                try {
                    val d = Api.download("/v1/media/$key")
                    val file = PiMsgMedia.store(context, key, d.mime, "avatar", d.bytes)
                    avatarBitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } catch (_: Exception) { }
            }
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
            val cache = JSONObject()
                .put("name", name).put("bio", bio).put("email", email)
                .put("email_verified", emailVerified).put("intro_video_key", introVideoKey)
                .put("code", code).put("show_phone", showPhone).put("show_online_status", showOnlineStatus).put("phone_verified", phoneVerified)
                .put("phone_contact", phoneContact)
                .put("avatar_key", avatarKey)
            avatarBitmap?.let { bitmap ->
                val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
                val bytes = ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
                    out.toByteArray()
                }
                cache.put("avatar_jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            val linksCache = org.json.JSONArray()
            socialLinks.forEach { linksCache.put(JSONObject().put("id", it.id).put("label", it.label).put("url", it.url)) }
            cache.put("social_links", linksCache)
            profileCache.edit().putString("profile", cache.toString()).apply()
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
            PiButton(if (mediaUploading) "A carregar fotografia..." else "Alterar fotografia", { imagePicker.launch("image/*") }, primary = false, enabled = !mediaUploading)
            if (mediaUploading) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.72f), color = PiColors.White, trackColor = PiColors.SurfaceHigh)
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
        OutlinedTextField(
            shape = RoundedCornerShape(22.dp),value = name, onValueChange = { name = it.take(60) }, label = { Text("Nome") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(22.dp),value = bio, onValueChange = { bio = it.take(300) }, label = { Text("Biografia / apresentação") }, minLines = 3, supportingText = { Text("${bio.length}/300") }, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())

        Section("E-mail e verificação")
        OutlinedTextField(
            shape = RoundedCornerShape(22.dp),value = email, onValueChange = { email = it.take(180); if (it.trim() != FirebaseAuth.getInstance().currentUser?.email) emailVerified = false }, label = { Text("E-mail") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
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
        PiButton(if (mediaUploading) "A carregar vídeo..." else if (introVideoKey == null) "Adicionar vídeo" else "Substituir vídeo", { videoPicker.launch("video/*") }, primary = false, enabled = !mediaUploading)
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PiButton("Copiar código", { copy(context, "codigo", code); msg = "Código copiado." }, primary = false)
            PiButton("Gerar novo código", {
                scope.launch {
                    try { code = Api.post("/v1/me/code/regenerate", JSONObject()).getString("code"); msg = "Novo código criado." }
                    catch (e: Exception) { msg = "Não foi possível gerar o código." }
                }
            }, primary = false)
        }

        Section("Privacidade")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mostrar quando estou online", color = PiColors.White, fontSize = 16.sp)
                Text(if (showOnlineStatus) "As pessoas podem ver a bolinha azul quando estiveres activo." else "Modo anónimo activo: o teu estado online fica oculto.", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = showOnlineStatus, onCheckedChange = { enabled ->
                showOnlineStatus = enabled
                scope.launch {
                    runCatching { Api.patch("/v1/me", JSONObject().put("name", name).put("bio", bio).put("show_phone", showPhone).put("show_online_status", enabled)) }
                        .onFailure { msg = "Não foi possível guardar a privacidade." }
                }
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White, uncheckedThumbColor = PiColors.Gray, uncheckedTrackColor = PiColors.SurfaceHigh))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mostrar o meu número no perfil", color = PiColors.White, fontSize = 16.sp)
                Text(phoneContact ?: if (phoneVerified) "Número confirmado" else "Nenhum contacto adicionado.", color = PiColors.Gray, fontSize = 13.sp)
            }
            Switch(
                checked = showPhone, onCheckedChange = { showPhone = it }, enabled = phoneVerified || !phoneContact.isNullOrBlank(),
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
                        .put("show_online_status", showOnlineStatus)
                    Api.patch("/v1/me", body)
                    msg = "Perfil guardado com sucesso."
                } catch (e: Exception) { msg = "Não foi possível guardar. Verifique as ligações e tente novamente." }
                saving = false
            }
        }, enabled = name.isNotBlank() && !saving)
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(24.dp))
    }
    avatarCropSource?.let { source ->
        ImageCropDialog(
            bitmap = source,
            aspectRatio = 1f,
            title = "Ajustar fotografia de perfil",
            onDismiss = { avatarCropSource = null },
            onConfirm = { cropped ->
                avatarCropSource = null
                scope.launch {
                    mediaUploading = true
                    msg = "A guardar fotografia..."
                    try {
                        val uploaded = uploadBitmap(context, cropped, "avatar.jpg")
                        Api.patch("/v1/me/media", JSONObject().put("avatar_media_id", uploaded.id))
                        val downloaded = Api.download("/v1/media/${uploaded.id}")
                        val cached = PiMsgMedia.store(context, uploaded.id, downloaded.mime, "avatar", downloaded.bytes)
                        avatarKey = uploaded.id
                        avatarBitmap = BitmapFactory.decodeFile(cached.absolutePath)
                        context.getSharedPreferences("pimsg_home_profile", Context.MODE_PRIVATE)
                            .edit().putString("avatar_key", uploaded.id).apply()
                        val cachedProfile = profileCache.getString("profile", null)?.let { JSONObject(it) } ?: JSONObject()
                        val encoded = ByteArrayOutputStream().use { out ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 78, out)
                            out.toByteArray()
                        }
                        cachedProfile.put("avatar_key", uploaded.id)
                            .put("avatar_jpeg", Base64.encodeToString(encoded, Base64.NO_WRAP))
                        profileCache.edit().putString("profile", cachedProfile.toString()).apply()
                        msg = "Fotografia atualizada."
                    } catch (_: Exception) {
                        msg = "Não foi possível atualizar a fotografia. Verifique a ligação e tente novamente."
                    } finally {
                        mediaUploading = false
                    }
                }
            },
        )
    }
    if (addSocialOpen) {
        AlertDialog(
            onDismissRequest = { addSocialOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Adicionar rede social", color = PiColors.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
            shape = RoundedCornerShape(22.dp),value = socialUrl, onValueChange = { socialUrl = it.take(500) }, label = { Text("Ligação do site ou perfil") }, singleLine = true, colors = piFieldColors())
                    Text("O ícone do site será carregado automaticamente quando existir.", color = PiColors.TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val raw = socialUrl.trim()
                            val normalized = when {
                                raw.startsWith("https://", true) -> "https://" + raw.substringAfter("://")
                                raw.startsWith("http://", true) -> "https://" + raw.substringAfter("://")
                                else -> "https://$raw"
                            }
                            val result = Api.post("/v1/me/social-links", JSONObject().put("url", normalized))
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

@Composable
fun QrScreen(onBack: () -> Unit, onOpenChat: (ChatRef) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var scannedUserId by remember { mutableStateOf<String?>(null) }
    var scannedUserName by remember { mutableStateOf("") }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        val payload = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
        val scannedCode = payload?.takeIf { it.optString("app") == "PiMsg" && it.optString("type") == "user" }
            ?.optString("code")?.takeIf { it.isNotBlank() && it != "null" }
        if (scannedCode != null) {
            scannedUserName = payload?.optString("name").orEmpty().ifBlank { "Utilizador PiMsg" }
            scope.launch {
                try {
                    val found = Api.get("/v1/search?q=" + Api.encode(scannedCode)).getJSONArray("users")
                    val user = (0 until found.length()).map { found.getJSONObject(it) }
                        .firstOrNull { it.optString("id").isNotBlank() }
                    if (user == null) notice = "Não encontrámos uma conta activa com este QR."
                    else {
                        scannedUserId = user.getString("id")
                        scannedUserName = user.optString("name", scannedUserName)
                    }
                } catch (_: Exception) { notice = "Liga-te à Internet para localizar esta conta." }
            }
        } else if (!raw.isNullOrBlank()) notice = "Este QR não pertence a uma conta PiMsg."
    }

    LaunchedEffect(Unit) {
        val cache = context.getSharedPreferences("pimsg_profile_cache", Context.MODE_PRIVATE)
        runCatching {
            val saved = cache.getString("profile", null)?.let { JSONObject(it) }
            name = saved?.optString("name").orEmpty()
            code = saved?.optString("code").orEmpty()
        }
        if (Api.hasInternet(context)) {
            runCatching {
                val me = Api.get("/v1/me")
                name = me.optString("name")
                code = me.optString("code")
                val old = cache.getString("profile", null)?.let { JSONObject(it) } ?: JSONObject()
                old.put("name", name).put("code", code)
                cache.edit().putString("profile", old.toString()).apply()
            }.onFailure { notice = "Não foi possível actualizar o teu QR. A mostrar os dados guardados." }
        }
        if (code.isNotBlank()) qrBitmap = runCatching { createPiMsgQr(context, code, name) }.getOrNull()
        loading = false
    }

    ScreenFrame("Código QR", onBack) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(color = PiColors.White)
        } else if (qrBitmap != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name.ifBlank { "A tua conta PiMsg" }, color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Image(qrBitmap!!.asImageBitmap(), contentDescription = "Código QR PiMsg com logótipo", modifier = Modifier.size(260.dp))
                Text("Mostra este código para as pessoas encontrarem a tua conta.", color = PiColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                PiButton("Ler QR de outra conta", {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Aponta a câmara para o QR de uma conta PiMsg")
                        setBeepEnabled(false)
                        setOrientationLocked(true)
                    }
                    scanner.launch(options)
                }, primary = false)
                PiButton("Partilhar o meu QR", {
                    runCatching {
                        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
                        val imageFile = File(shareDir, "pimsg_profile_qr.png")
                        FileOutputStream(imageFile).use { output ->
                            if (!qrBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                throw IllegalStateException("Não foi possível criar a imagem QR.")
                            }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", imageFile)
                        val send = Intent(Intent.ACTION_SEND).setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_TEXT, "Encontra-me no PiMsg: $name")
                        send.clipData = android.content.ClipData.newUri(context.contentResolver, "QR PiMsg", uri)
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Partilhar QR do PiMsg"))
                    }.onFailure { notice = "Não foi possível partilhar o QR." }
                }, primary = false)
            }
        } else {
            Text("Não foi possível gerar o teu QR. Verifica se tens ligação à Internet e tenta novamente.", color = PiColors.TextSecondary)
        }
        notice?.let { Text(it, color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
    }

    scannedUserId?.let { userId ->
        AlertDialog(
            onDismissRequest = { scannedUserId = null },
            containerColor = PiColors.Surface,
            title = { Text("Conta encontrada", color = PiColors.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(scannedUserName, color = PiColors.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Queres abrir uma conversa com esta pessoa?", color = PiColors.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val chat = Api.post("/v1/chats/direct", JSONObject().put("user_id", userId))
                            scannedUserId = null
                            onOpenChat(ChatRef(chat.getString("id"), scannedUserName, userId, null))
                        } catch (_: Exception) {
                            notice = "Não foi possível iniciar a conversa."
                            scannedUserId = null
                        }
                    }
                }) { Text("Abrir conversa", color = PiColors.White) }
            },
            dismissButton = { TextButton(onClick = { scannedUserId = null }) { Text("Cancelar", color = PiColors.TextSecondary) } },
        )
    }
}

private fun createPiMsgQr(context: Context, code: String, name: String): Bitmap {
    val payload = JSONObject().put("app", "PiMsg").put("type", "user")
        .put("code", code).put("name", name).toString()
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 720, 720, hints)
    val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
    for (y in 0 until 720) for (x in 0 until 720) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    val canvas = Canvas(bitmap)
    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.pimsg_logo)
    if (logo != null) {
        val side = 154f
        val left = (720f - side) / 2f
        val top = (720f - side) / 2f
        var brightness = 0L
        var samples = 0
        val stepX = maxOf(1, logo.width / 40)
        val stepY = maxOf(1, logo.height / 40)
        for (y in 0 until logo.height step stepY) for (x in 0 until logo.width step stepX) {
            val pixel = logo.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) > 32) {
                brightness += (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                samples++
            }
        }
        val average = if (samples == 0) 0 else brightness / samples
        val backing = if (average > 127) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backing }
        canvas.drawRoundRect(RectF(left - 12f, top - 12f, left + side + 12f, top + side + 12f), 22f, 22f, paint)
        val scale = minOf(side / logo.width, side / logo.height)
        val width = logo.width * scale
        val height = logo.height * scale
        canvas.drawBitmap(
            logo, null,
            RectF(left + (side - width) / 2f, top + (side - height) / 2f, left + (side + width) / 2f, top + (side + height) / 2f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }
    return bitmap
}

// ---------------------------------------------------------------- Ferramentas
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onQr: () -> Unit,
    onPiBot: () -> Unit,
    onLinks: () -> Unit,
    onPlans: () -> Unit,
) {
    ScreenFrame("Ferramentas", onBack) {
        Text("Acesso rápido às ferramentas disponíveis no PiMsg.", color = PiColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        @Composable
        fun ToolItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, action: () -> Unit) {
            Column(
                modifier.clip(RoundedCornerShape(20.dp)).clickable { action() }.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(21.dp)).background(PiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = PiColors.White, modifier = Modifier.size(29.dp))
                }
                Text(label, color = PiColors.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolItem(Icons.Filled.QrCode2, "Código QR", Modifier.weight(1f), onQr)
            ToolItem(Icons.Filled.SmartToy, "PiBot", Modifier.weight(1f), onPiBot)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolItem(Icons.Filled.Link, "Meus links", Modifier.weight(1f), onLinks)
            ToolItem(Icons.Filled.WorkspacePremium, "Planos", Modifier.weight(1f), onPlans)
        }
    }
}

// ---------------------------------------------------------------- PiMsg Business
@Composable
private fun BusinessFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(42.dp).background(PiColors.SurfaceHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = title, tint = PiColors.Gray, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun PiMsgBusinessScreen(onBack: () -> Unit, onPlans: () -> Unit, onLinks: () -> Unit, onBots: () -> Unit) {
    ScreenFrame("PiMsg Business", onBack) {
        Text("Transforma as tuas conversas em oportunidades de venda.", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
        Text("O PiMsg ajuda profissionais, lojas e pequenas empresas a apresentar produtos, conversar com clientes e atender pessoas dentro e fora de Angola.", color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
        Section("VENDE E DIVULGA")
        BusinessFeature(Icons.Filled.Link, "Links personalizados", "Cria links para produtos, serviços ou campanhas e partilha-os nas redes sociais e noutras aplicações.")
        Spacer(Modifier.height(8.dp))
        BusinessFeature(Icons.Filled.Person, "Perfil profissional", "Apresenta a descrição, contactos públicos, ligações sociais e vídeo de apresentação no teu perfil.")
        Spacer(Modifier.height(8.dp))
        BusinessFeature(Icons.Filled.QrCode2, "Conversas com clientes", "Centraliza as conversas com os clientes no PiMsg e acompanha os pedidos directamente.")
        Section("COMUNICA SEM FRONTEIRAS")
        BusinessFeature(Icons.Filled.DataUsage, "Tradução de mensagens", "Ajuda-te a conversar com clientes que falam outros idiomas. O plano Free inclui 4 traduções por dia; os planos pagos oferecem limites superiores.")
        Section("ORGANIZA O ATENDIMENTO")
        BusinessFeature(Icons.Filled.WorkspacePremium, "Pi Business", "Guarda a descrição do negócio, horário, contactos, produtos, preços e perguntas frequentes para preparar respostas comerciais assistidas por IA.")
        Spacer(Modifier.height(8.dp))
        BusinessFeature(Icons.Filled.FolderOpen, "Pi Reminder", "Organiza tarefas e acompanha compromissos.")
        Spacer(Modifier.height(8.dp))
        BusinessFeature(Icons.Filled.SmartToy, "Pi Bots", "Cria assistentes personalizados para tarefas específicas.")
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(14.dp)).padding(13.dp)) {
            Text("Nota sobre o Pi Business", color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("O Pi Business actualmente prepara respostas com base nas informações que configurares e permite testá-las dentro da aplicação. A resposta ainda precisa de ser enviada por ti; não existe promessa de atendimento automático fora do PiMsg.", color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(14.dp))
        PiButton("Conhecer os planos Business", onPlans, primary = true)
        PiButton("Criar links para os meus produtos", onLinks, primary = false)
        PiButton("Explorar os bots PiMsg", onBots, primary = false)
    }
}

// ---------------------------------------------------------------- Acessibilidade
@Composable
fun AccessibilityScreen(onBack: () -> Unit, onLargeTextChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pimsg_accessibility", Context.MODE_PRIVATE) }
    var largeText by remember { mutableStateOf(prefs.getBoolean("large_text", false)) }
    ScreenFrame("Acessibilidade", onBack) {
        Text("Adapte a leitura do PiMsg às suas necessidades.", color = PiColors.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Texto maior", color = PiColors.White, fontSize = 16.sp)
                Text("Aumenta o tamanho dos textos e dos controlos em todo o aplicativo.", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = largeText,
                onCheckedChange = {
                    largeText = it
                    prefs.edit().putBoolean("large_text", it).apply()
                    onLargeTextChanged(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White),
            )
        }
        Section("Leitor de ecrã")
        Text("O PiMsg utiliza as descrições dos controlos do Android. Para ouvir os elementos do ecrã, active o TalkBack nas definições de Acessibilidade do seu telemóvel.", color = PiColors.TextSecondary, fontSize = 13.sp)
        PiButton("Abrir definições de acessibilidade", {
            runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, primary = false)
    }
}

// ---------------------------------------------------------------- Definições
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit, onSecurity: () -> Unit, onStorageData: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var soundEnabled by remember { mutableStateOf(PiFeedback.soundEnabled(context)) }
    var hapticEnabled by remember { mutableStateOf(PiFeedback.hapticEnabled(context)) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var pushEnabled by remember { mutableStateOf(true) }
    var popupEnabled by remember { mutableStateOf(true) }

    suspend fun loadNotificationPreferences() {
        try {
            val prefs = Api.get("/v1/me/notifications")
            pushEnabled = prefs.optBoolean("push_enabled", true)
            popupEnabled = prefs.optBoolean("popup_enabled", true)
            context.getSharedPreferences("pimsg_notifications", Context.MODE_PRIVATE).edit()
                .putBoolean("push_enabled", pushEnabled)
                .putBoolean("popup_enabled", popupEnabled)
                .apply()
        } catch (_: Exception) { }
    }

    fun saveNotificationPreferences() {
        context.getSharedPreferences("pimsg_notifications", Context.MODE_PRIVATE).edit()
            .putBoolean("push_enabled", pushEnabled)
            .putBoolean("popup_enabled", popupEnabled)
            .apply()
        scope.launch {
            runCatching {
                Api.put("/v1/me/notifications", JSONObject()
                    .put("push_enabled", pushEnabled)
                    .put("popup_enabled", popupEnabled))
            }.onFailure { msg = "Não foi possível guardar as preferências de notificação." }
        }
    }

    suspend fun loadBlocked() {
        try {
            val arr = Api.get("/v1/blocks").getJSONArray("users")
            blocked = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getString("name") } }
        } catch (e: Exception) { /* lista vazia */ }
    }
    LaunchedEffect(Unit) { loadBlocked(); loadNotificationPreferences() }

    ScreenFrame("Definições", onBack) {
        Section("Idioma")
        Row(Modifier.fillMaxWidth().clickable {
            if (Build.VERSION.SDK_INT >= 33) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            }
        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Idioma da aplicação", color = PiColors.White, fontSize = 16.sp)
                Text(
                    if (Build.VERSION.SDK_INT >= 33) PiI18n.languageName() else "Segue o idioma do telemóvel",
                    color = PiColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
            androidx.compose.material3.Icon(
                Icons.Filled.Add,
                contentDescription = if (Build.VERSION.SDK_INT >= 33) "Idioma da aplicação" else null,
                tint = PiColors.TextSecondary
            )
        }
        Section("Armazenamento e dados")
        Row(
            Modifier.fillMaxWidth().clickable { onStorageData() }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text("Armazenamento e dados", color = PiColors.White, fontSize = 16.sp)
                Text("Gira os ficheiros guardados e o consumo de dados", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
        }

        Section("Notificações")
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notificações push", color = PiColors.White, fontSize = 16.sp)
                Text("Receber alertas de mensagens mesmo com o PiMsg fechado", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = pushEnabled, onCheckedChange = {
                pushEnabled = it
                if (it) scope.launch { runCatching { Push.register(context) } }
                saveNotificationPreferences()
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Avisos pop-up", color = PiColors.White, fontSize = 16.sp)
                Text("Mostrar alertas destacados no ecrã; desligar mantém notificações silenciosas", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = popupEnabled, onCheckedChange = {
                popupEnabled = it
                saveNotificationPreferences()
            }, enabled = pushEnabled, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White))
        }

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

        Section("Avaliação")
        Text(
            "Avaliar o PiMsg na Google Play",
            color = PiColors.White,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    com.isabgroup.pimsg.ui.PlayReview.request(activity) {
                        com.isabgroup.pimsg.ui.PlayReview.openStoreListing(activity)
                    }
                } else {
                    msg = "Não foi possível abrir a avaliação neste momento."
                }
            }.padding(vertical = 12.dp)
        )

        Section("Informações legais")
        listOf("Termos de uso" to "terms", "Política de privacidade" to "privacy", "Subscrições" to "subscriptions", "Política de reembolsos" to "refund", "Política de cookies" to "cookies", "Regras da comunidade" to "community", "Segurança" to "security", "Segurança de menores" to "child-safety", "Direitos de autor" to "copyright", "Perguntas frequentes" to "faq", "Suporte" to "support").forEach { (label, path) ->
            Text(label, color = PiColors.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { openUrl(context, "$SITE/$path") }.padding(vertical = 12.dp))
        }

        Section("Conta")
        Text("Terminar sessão", color = PiColors.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onSignOut() }.padding(vertical = 12.dp))

        Spacer(Modifier.height(22.dp))
        Column(
            Modifier.fillMaxWidth()
                .background(Color(0xFF2A171A), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .border(1.dp, PiColors.Danger.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Text("ZONA DE PERIGO", color = PiColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Apagar conta", color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text("O pedido será enviado à equipa PiMsg para análise. Poderemos pedir confirmação adicional para proteger a tua conta.", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Apagar a minha conta", color = PiColors.Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(top = 14.dp))
        }
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
    }


    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = PiColors.Surface,
            title = { Text("Apagar conta", color = PiColors.White) },
            text = {
                Text(
                    "Ao confirmar, será enviado um pedido de eliminação à equipa PiMsg. Poderemos pedir informações adicionais para confirmar que és o titular da conta.",
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
                }) { Text("Enviar pedido", color = PiColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = PiColors.White) } },
        )
    }
}


private fun formatDataSize(bytes: Long): String {
    if (bytes < 0L) return "Indisponível"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0)
    else String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
}

private fun directoryBytes(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
}

@Composable
fun StorageDataScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadMode by remember { mutableStateOf(PiMsgMedia.downloadMode(context)) }
    var saveGallery by remember { mutableStateOf(PiMsgMedia.saveToGallery(context)) }
    var storageBytes by remember { mutableStateOf(0L) }
    var sentBytes by remember { mutableStateOf(TrafficStats.getUidTxBytes(Process.myUid())) }
    var receivedBytes by remember { mutableStateOf(TrafficStats.getUidRxBytes(Process.myUid())) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refreshUsage() {
        storageBytes = withContext(Dispatchers.IO) {
            directoryBytes(PiMsgMedia.root(context))
        }
        sentBytes = TrafficStats.getUidTxBytes(Process.myUid())
        receivedBytes = TrafficStats.getUidRxBytes(Process.myUid())
    }
    LaunchedEffect(Unit) { refreshUsage() }

    ScreenFrame("Armazenamento e dados", onBack) {
        Section("ARMAZENAMENTO")
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Ficheiros guardados no PiMsg", color = PiColors.White, fontSize = 16.sp)
                Text(formatDataSize(storageBytes), color = PiColors.TextSecondary, fontSize = 14.sp)
            }
        }
        Text("Inclui fotografias, vídeos, áudio e documentos descarregados para consulta offline.", color = PiColors.TextSecondary, fontSize = 12.sp)
        PiButton(
            text = if (clearing) "A limpar..." else "Gerir armazenamento",
            onClick = { confirmClear = true },
            enabled = !clearing && storageBytes > 0L,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        Section("UTILIZAÇÃO DA REDE")
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DataUsage, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Dados enviados", color = PiColors.White, fontSize = 15.sp)
                Text(formatDataSize(sentBytes), color = PiColors.TextSecondary, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 40.dp)) {
                Text("Dados recebidos", color = PiColors.White, fontSize = 15.sp)
                Text(formatDataSize(receivedBytes), color = PiColors.TextSecondary, fontSize = 13.sp)
            }
        }
        Text("Contagem do PiMsg desde o último reinício do telemóvel. O Android repõe estes valores quando o dispositivo reinicia.", color = PiColors.TextSecondary, fontSize = 12.sp)
        TextButton(onClick = { scope.launch { refreshUsage() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Actualizar utilização", color = Color(0xFF8AB4F8))
        }

        Section("DESCARREGAMENTO AUTOMÁTICO")
        Text("Escolhe quando os ficheiros recebidos podem ser descarregados automaticamente. As mensagens de voz continuam disponíveis conforme o funcionamento actual do PiMsg.", color = PiColors.TextSecondary, fontSize = 12.sp)
        listOf(
            PiMsgMedia.DOWNLOAD_NEVER to "Nunca",
            PiMsgMedia.DOWNLOAD_WIFI to "Apenas por Wi-Fi",
            PiMsgMedia.DOWNLOAD_ALWAYS to "Wi-Fi e dados móveis",
        ).forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable {
                downloadMode = value
                PiMsgMedia.setDownloadMode(context, value)
            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = downloadMode == value, onClick = {
                    downloadMode = value
                    PiMsgMedia.setDownloadMode(context, value)
                })
                Text(label, color = PiColors.White, fontSize = 14.sp)
            }
        }

        Section("GUARDAR NA GALERIA")
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Permitir guardar na galeria", color = PiColors.White, fontSize = 16.sp)
                Text(if (saveGallery) "Podes guardar fotografias, vídeos e áudio na galeria do telemóvel." else "Os ficheiros permanecem na área de armazenamento do PiMsg até escolheres guardá-los.", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = saveGallery, onCheckedChange = {
                saveGallery = it
                PiMsgMedia.setSaveToGallery(context, it)
            }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PiColors.White))
        }
        message?.let { Text(it, color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { if (!clearing) confirmClear = false },
            containerColor = PiColors.Surface,
            title = { Text("Limpar ficheiros descarregados", color = PiColors.White) },
            text = { Text("Isto remove as cópias locais de fotografias, vídeos, áudio e documentos guardados pelo PiMsg. As mensagens e os ficheiros originais da conta não são apagados; poderás descarregá-los novamente quando tiveres Internet.", color = PiColors.TextSecondary) },
            confirmButton = {
                TextButton(enabled = !clearing, onClick = {
                    confirmClear = false
                    clearing = true
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            var count = 0
                            listOf("Media", "Images", "Videos", "Audio", "Documents").forEach { folderName ->
                                val folder = File(PiMsgMedia.root(context), folderName)
                                folder.listFiles()?.forEach { file ->
                                    if (file.isFile && file.name != ".nomedia" && file.delete()) count++
                                }
                            }
                            count
                        }
                        refreshUsage()
                        clearing = false
                        message = if (deleted > 0) "$deleted ficheiro(s) local(is) removido(s)." else "Não havia ficheiros descarregados para remover."
                    }
                }) { Text("Limpar", color = PiColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar", color = PiColors.White) } },
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
    var imageUploading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var linkReplyUsed by remember { mutableStateOf(0) }
    var linkReplyRemaining by remember { mutableStateOf<Int?>(500) }
    var linkReplyResetAt by remember { mutableStateOf<Long?>(null) }
    val linkImagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            imageUploading = true
            msg = "A preparar e enviar imagem..."
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    imagePreview = android.graphics.BitmapFactory.decodeStream(input)
                }
                val uploaded = com.isabgroup.pimsg.data.uploadUri(context.contentResolver, uri)
                if (!uploaded.mime.startsWith("image/")) throw IllegalArgumentException("Escolha uma imagem.")
                imageMediaId = uploaded.id
                if (imagePreview == null) {
                    val downloaded = Api.download("/v1/media/\u0024{uploaded.id}")
                    imagePreview = android.graphics.BitmapFactory.decodeByteArray(downloaded.bytes, 0, downloaded.bytes.size)
                }
                msg = "Imagem adicionada ao link. Já podes ver a pré-visualização sem criar o link."
            } catch (e: Exception) { msg = "Não foi possível adicionar a imagem." }
            finally { imageUploading = false }
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
        Row(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(PiColors.SurfaceHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Link, contentDescription = "Links personalizados", tint = PiColors.Gray, modifier = Modifier.size(22.dp))
            }
            Text("Cria um link para cada produto ou assunto. Quando alguém o abre e gera o código, o PiMsg avisa-te e já ficas a saber do que se trata.", color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
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
        OutlinedTextField(
            shape = RoundedCornerShape(22.dp),value = subject, onValueChange = { subject = it.take(80) }, label = { Text("Assunto ou produto") }, singleLine = true, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            shape = RoundedCornerShape(22.dp),value = intro, onValueChange = { intro = it.take(400) }, label = { Text("Descrição na página do link (opcional)") }, minLines = 2, colors = piFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        PiButton(if (imageUploading) "A carregar fotografia..." else "Adicionar fotografia ao link", { linkImagePicker.launch("image/*") }, primary = false, enabled = !imageUploading)
        if (imageUploading) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PiColors.White, trackColor = PiColors.SurfaceHigh)
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

        Section("OS MEUS LINKS")
        if (links.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(14.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(28.dp))
                Text("Ainda não criaste nenhum link.", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (links.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(PiColors.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, PiColors.GrayDark, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                links.forEachIndexed { index, l ->
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.subject, color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${l.opens} aberturas", color = PiColors.TextSecondary, fontSize = 11.sp)
                        }
                        Text(l.url, color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                        if (l.imageMediaId != null) {
                            Text("Fotografia personalizada", color = PiColors.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(onClick = { copy(context, "link", l.url); msg = "Link copiado." }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Copiar", color = PiColors.TextSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, "Fale comigo sobre ${l.subject} no PiMsg: ${l.url}")
                                context.startActivity(Intent.createChooser(send, "Partilhar link"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Partilhar", color = PiColors.TextSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = { scope.launch { runCatching { Api.delete("/v1/links/${l.id}") }; load() } }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Apagar", color = PiColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    if (index < links.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(PiColors.GrayDark))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


// ---------------------------------------------------------------- Planos
@Composable
private fun PlanBenefit(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = PiColors.Gray,
            modifier = Modifier.padding(top = 1.dp).size(18.dp)
        )
        Text(
            text,
            color = PiColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f).padding(start = 11.dp)
        )
    }
}

@Composable
private fun PlanHeading(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, badge: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(PiColors.SurfaceHigh, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = title, tint = PiColors.Gray, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = PiColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(badge, color = PiColors.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(PiColors.SurfaceHigh, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                }
            }
            Text(subtitle, color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

private fun formatPiStorage(bytes: Long): String {
    val mb = bytes.coerceAtLeast(0L) / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
    else String.format(java.util.Locale.US, "%.0f MB", mb)
}

@Composable
private fun SubscriptionProductButton(
    label: String,
    product: ProductDetails?,
    billingReady: Boolean,
    emailVerified: Boolean,
    activity: android.app.Activity?,
    onBuy: (ProductDetails) -> Unit,
    onEmailRequired: () -> Unit,
) {
    val price = product?.subscriptionOfferDetails?.firstOrNull()
        ?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    val canPurchase = billingReady && product != null && activity != null
    val buttonText = when {
        price != null -> "Subscrever $label · $price"
        !billingReady -> "$label · a preparar subscrição"
        else -> "$label · indisponível"
    }
    PiButton(buttonText, {
        if (!emailVerified) onEmailRequired()
        else if (canPurchase && product != null) onBuy(product)
    }, primary = true, enabled = canPurchase)
}

@Composable
fun PlansScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var billingManager by remember { mutableStateOf<PlayBilling?>(null) }
    var products by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var purchaseMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf("free") }
    var currentTier by remember { mutableStateOf("free") }
    var status by remember { mutableStateOf("free") }
    var expiresAt by remember { mutableStateOf<Long?>(null) }
    var messageUsage by remember { mutableStateOf(0) }
    var aiUsage by remember { mutableStateOf(0) }
    var aiLimit by remember { mutableStateOf(0) }
    var messageLimit by remember { mutableStateOf<Int?>(30) }
    var translationUsage by remember { mutableStateOf(0) }
    var translationLimit by remember { mutableStateOf<Int?>(4) }
    var linkReplyUsage by remember { mutableStateOf(0) }
    var linkReplyLimit by remember { mutableStateOf<Int?>(500) }
    var customBotLimit by remember { mutableStateOf(0) }
    var storageUsedBytes by remember { mutableStateOf(0L) }
    var storageLimitBytes by remember { mutableStateOf(250L * 1024L * 1024L) }
    var businessTools by remember { mutableStateOf(false) }
    var billingReady by remember { mutableStateOf(false) }
    var emailVerified by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPlan by remember { mutableStateOf<String?>(null) }

    suspend fun refreshPlan() {
        try {
            val data = Api.get("/v1/plan")
            billingReady = data.optBoolean("billing_ready", false)
            emailVerified = data.optBoolean("email_verified", false)
            current = data.optString("current_plan", "free")
            currentTier = data.optString("current_tier", "free")
            if (currentTier != "free") context.getSharedPreferences("pimsg_translation", Context.MODE_PRIVATE).edit().remove("blocked_day").apply()
            status = data.optString("subscription_status", "free")
            expiresAt = if (data.isNull("expires_at")) null else data.optLong("expires_at")
            messageUsage = data.optInt("message_usage")
            messageLimit = if (data.isNull("message_limit")) null else data.optInt("message_limit")
            aiUsage = data.optInt("ai_usage")
            aiLimit = data.optInt("ai_limit", 0)
            translationUsage = data.optInt("translation_usage", 0)
            translationLimit = if (data.isNull("translation_limit")) null else data.optInt("translation_limit")
            linkReplyUsage = data.optInt("personalized_link_reply_usage", 0)
            linkReplyLimit = if (data.isNull("personalized_link_reply_limit")) null else data.optInt("personalized_link_reply_limit")
            customBotLimit = data.optInt("custom_bot_limit", 0)
            storageUsedBytes = data.optLong("storage_used_bytes", 0L)
            storageLimitBytes = data.optLong("storage_limit_bytes", 250L * 1024L * 1024L)
            businessTools = data.optBoolean("business_tools", false)
        } catch (_: Exception) {
            error = "Não foi possível consultar o plano da conta."
        }
    }

    LaunchedEffect(Unit) {
        val manager = PlayBilling(
            context = context,
            onProducts = { products = it },
            onMessage = { purchaseMessage = it },
            onVerified = { scope.launch { refreshPlan() } },
        )
        billingManager = manager
        manager.connect()
        refreshPlan()
        loading = false
    }
    DisposableEffect(billingManager) {
        onDispose { billingManager?.close() }
    }

    ScreenFrame(if (selectedPlan == null) "Subscrições" else "Detalhes do plano", {
        if (selectedPlan != null) selectedPlan = null else onBack()
    }) {
        if (selectedPlan != null) {
            val tier = selectedPlan!!
            val planTitle = when (tier) {
                "plus" -> "PiMsg Plus"
                "pro" -> "PiMsg Pro"
                "business" -> "PiMsg Business"
                else -> "PiMsg Free"
            }
            val planSubtitle = when (tier) {
                "plus" -> "Mais liberdade para conversar todos os dias."
                "pro" -> "Para quem cria, produz e precisa de mais."
                "business" -> "Ferramentas para negócios e equipas."
                else -> "Essencial para começares e manteres contacto."
            }
            val planFeatures = when (tier) {
                "plus" -> listOf("Mensagens sem limite diário", "Respostas ilimitadas através dos links personalizados", "Pi AI e Pi Reminder, até 20 lembretes activos", "5 pedidos de IA por dia", "25 traduções por dia", "2 GB para ficheiros e multimédia")
                "pro" -> listOf("Mensagens e respostas de links sem limite mensal", "Todos os bots, excepto ferramentas exclusivas Business", "20 pedidos de IA por dia", "75 traduções por dia", "Até 5 bots personalizados e 100 lembretes activos", "5 GB para ficheiros e multimédia", "Vídeo de apresentação no perfil")
                "business" -> listOf("Tudo o que está incluído no PiMsg Pro", "Pi Business para preparar respostas comerciais assistidas por IA", "50 pedidos de IA por dia", "150 traduções por dia", "Até 20 bots personalizados e 500 lembretes activos", "20 GB para ficheiros e multimédia", "Ferramentas para organizar informações, produtos, preços e contactos")
                else -> listOf("Até 30 contactos", "30 mensagens por dia", "500 respostas de links personalizados por mês", "4 traduções por dia", "Conversas, grupos e partilha de ficheiros", "250 MB para ficheiros e multimédia")
            }
            Spacer(Modifier.height(18.dp))
            Text(planTitle, color = PiColors.White, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(planSubtitle, color = PiColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Explorar todos os benefícios", color = PiColors.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
            Column(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(16.dp)).border(1.dp, PiColors.GrayDark, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).padding(horizontal = 15.dp, vertical = 8.dp)) {
                planFeatures.forEachIndexed { index, feature ->
                    PlanBenefit(feature)
                    if (index < planFeatures.lastIndex) Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(PiColors.GrayDark))
                }
            }
            Spacer(Modifier.height(22.dp))
            when (tier) {
                "plus" -> if (currentTier == "plus") Text("Este é o teu plano actual.", color = PiColors.TextSecondary, fontSize = 13.sp) else {
                    SubscriptionProductButton("Mensal", products[PlayBilling.PLUS_MONTHLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                    SubscriptionProductButton("Anual", products[PlayBilling.PLUS_YEARLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                }
                "pro" -> if (currentTier == "pro") Text("Este é o teu plano actual.", color = PiColors.TextSecondary, fontSize = 13.sp) else {
                    SubscriptionProductButton("Semanal", products[PlayBilling.WEEKLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                    SubscriptionProductButton("Mensal", products[PlayBilling.MONTHLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                    SubscriptionProductButton("Anual", products[PlayBilling.PRO_YEARLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                }
                "business" -> if (currentTier == "business") Text("Este é o teu plano actual.", color = PiColors.TextSecondary, fontSize = 13.sp) else {
                    SubscriptionProductButton("Mensal", products[PlayBilling.BUSINESS_MONTHLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                    SubscriptionProductButton("Anual", products[PlayBilling.BUSINESS_YEARLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
                }
                else -> Text("Este é o plano gratuito do PiMsg.", color = PiColors.TextSecondary, fontSize = 13.sp)
            }
            purchaseMessage?.let { Text(it, color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
            Text("A subscrição pode ser gerida ou cancelada nas definições da tua conta Google Play.", color = PiColors.Gray, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 20.dp))
        } else {
        Text("Mais espaço, ferramentas e liberdade para comunicar. Escolhe o plano que combina contigo.", color = PiColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(15.dp)) {
            Text("O TEU PLANO", color = PiColors.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(when(currentTier) { "plus" -> "PiMsg Plus"; "pro" -> "PiMsg Pro"; "business" -> "PiMsg Business"; else -> "PiMsg Free" }, color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
            Text("Mensagens hoje: $messageUsage" + (messageLimit?.let { " / $it" } ?: " · sem limite diário"), color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            Text("Traduções hoje: $translationUsage" + (translationLimit?.let { " / $it" } ?: ""), color = PiColors.TextSecondary, fontSize = 12.sp)
            Text("Pedidos de IA hoje: $aiUsage / $aiLimit", color = PiColors.TextSecondary, fontSize = 12.sp)
            Text("Armazenamento: ${formatPiStorage(storageUsedBytes)} / ${formatPiStorage(storageLimitBytes)}", color = PiColors.TextSecondary, fontSize = 12.sp)
            Text("Respostas de links este mês: $linkReplyUsage" + (linkReplyLimit?.let { " / $it" } ?: " · sem limite"), color = PiColors.TextSecondary, fontSize = 12.sp)
            if (currentTier != "free") {
                val date = expiresAt?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) }
                if (date != null) Text("Válido até $date · ${if (status == "active") "Activo" else if (status == "grace") "Período de tolerância" else status}", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                Text("Bots personalizados: até $customBotLimit · Pi Business: ${if (businessTools) "incluído" else "não incluído"}", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = { billingManager?.restorePurchases() }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar subscrição", color = PiColors.White) }
        }

        Spacer(Modifier.height(18.dp))
        if (!emailVerified) {
            Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp)) {
                Text("Confirma o teu e-mail para aderir", color = PiColors.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Antes de iniciar uma subscrição, associa um e-mail à conta e confirma-o em Perfil > E-mail e verificação. O PiMsg só permite compras depois de o servidor confirmar a verificação.", color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
        Text("PLANOS DISPONÍVEIS", color = PiColors.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(15.dp)) {
            PlanHeading("PiMsg Free", "Essencial para começares e manteres contacto.", Icons.Filled.DataUsage)
            listOf("Até 30 contactos", "30 mensagens por dia", "500 respostas de links personalizados por mês", "4 traduções por dia", "Conversas, grupos e partilha de ficheiros", "250 MB para ficheiros e multimédia", "Sem bots de IA pagos").forEach { PlanBenefit(it) }
            if (currentTier == "free") Text("Plano actual", color = PiColors.White, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            PiButton("Ver todos os benefícios", { selectedPlan = "free" }, primary = false)
        }

        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(15.dp)) {
            PlanHeading("PiMsg Plus", "Mais liberdade para conversar todos os dias.", Icons.Filled.FolderOpen, "POPULAR")
            listOf("Mensagens sem limite diário", "Respostas ilimitadas através dos links personalizados", "Pi AI e Pi Reminder (até 20 lembretes activos)", "5 pedidos de IA por dia", "25 traduções por dia", "2 GB para ficheiros e multimédia", "Sem bots personalizados nem Pi Business").forEach { PlanBenefit(it) }
            Spacer(Modifier.height(10.dp))
            if (currentTier != "plus") {
            SubscriptionProductButton("Mensal", products[PlayBilling.PLUS_MONTHLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            SubscriptionProductButton("Anual", products[PlayBilling.PLUS_YEARLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            }
            if (currentTier == "plus") Text("Plano actual", color = PiColors.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PiButton("Ver todos os benefícios", { selectedPlan = "plus" }, primary = false)
        }

        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(15.dp)) {
            PlanHeading("PiMsg Pro", "Para quem cria, produz e precisa de mais.", Icons.Filled.QrCode2)
            listOf("Mensagens e respostas de links sem limite mensal", "Todos os bots, excepto ferramentas exclusivas Business", "20 pedidos de IA por dia", "75 traduções por dia", "Até 5 bots personalizados", "Até 100 lembretes activos", "5 GB para ficheiros e multimédia", "Vídeo de apresentação no perfil").forEach { PlanBenefit(it) }
            Spacer(Modifier.height(10.dp))
            if (currentTier != "pro") {
            SubscriptionProductButton("Semanal", products[PlayBilling.WEEKLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            SubscriptionProductButton("Mensal", products[PlayBilling.MONTHLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            SubscriptionProductButton("Anual", products[PlayBilling.PRO_YEARLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            }
            if (currentTier == "pro") Text("Plano actual", color = PiColors.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PiButton("Ver todos os benefícios", { selectedPlan = "pro" }, primary = false)
        }

        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(15.dp)) {
            PlanHeading("PiMsg Business", "Ferramentas para negócios e equipas.", Icons.Filled.DeleteSweep)
            listOf("Tudo do Pro", "Pi Business para preparar respostas comerciais", "50 pedidos de IA por dia", "150 traduções por dia", "Até 20 bots personalizados", "Até 500 lembretes activos", "20 GB para ficheiros e multimédia", "Ferramentas para organizar informações, produtos, preços e contactos").forEach { PlanBenefit(it) }
            Spacer(Modifier.height(10.dp))
            if (currentTier != "business") {
            SubscriptionProductButton("Mensal", products[PlayBilling.BUSINESS_MONTHLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            SubscriptionProductButton("Anual", products[PlayBilling.BUSINESS_YEARLY], billingReady, emailVerified, activity, onBuy = { billingManager?.buy(activity!!, it) }, onEmailRequired = { purchaseMessage = "Confirma o teu e-mail em Perfil > E-mail e verificação antes de aderir." })
            }
            if (currentTier == "business") Text("Plano actual", color = PiColors.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PiButton("Ver todos os benefícios", { selectedPlan = "business" }, primary = false)
        }

        purchaseMessage?.let { Text(it, color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        Text("Podes consultar, alterar ou cancelar a tua subscrição a qualquer momento.", color = PiColors.Gray, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
        PiButton("Gerir subscrição", { openUrl(context, "https://play.google.com/store/account/subscriptions") }, primary = false)
        error?.let { Text(it, color = PiColors.Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        if (loading) Text("A consultar a tua conta…", color = PiColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
