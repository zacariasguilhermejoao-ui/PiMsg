package com.isabgroup.pimsg.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreVert
import com.isabgroup.pimsg.ui.PiText as Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.PiMsgMedia
import org.json.JSONObject
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import com.isabgroup.pimsg.ui.playClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatRef(val id: String, val title: String, val otherId: String?, val inviteCode: String?)

@Composable
fun ChatScreen(
    ref: ChatRef,
    onBack: () -> Unit,
    pendingShare: com.isabgroup.pimsg.ShareIncoming? = null,
    onShareConsumed: () -> Unit = {},
    onOpenBots: () -> Unit = {},
) {
    val convId = ref.id
    val title = ref.title
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(key = convId, factory = viewModelFactory { initializer { ChatViewModel(context.applicationContext, convId) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val cr = context.contentResolver
    val view = LocalView.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(pendingShare) {
        val share = pendingShare ?: return@LaunchedEffect
        if (!share.text.isNullOrBlank() && text.isEmpty()) {
            text = share.text
        }
        if (share.uris.isNotEmpty()) {
            for (uri in share.uris) {
                val mime = cr.getType(uri).orEmpty()
                val hint = when {
                    mime.startsWith("audio/") -> "music"
                    else -> "file"
                }
                vm.sendMedia(cr, uri, hint)
            }
            onShareConsumed()
        }
    }
    var attachOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var disappearingOpen by remember { mutableStateOf(false) }
    var disappearingSeconds by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf<String?>(null) }
    var peerProfileOpen by remember { mutableStateOf(false) }
    var peerProfileLoading by remember { mutableStateOf(false) }
    var peerProfile by remember { mutableStateOf<JSONObject?>(null) }
    var peerAvatar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val isGroup = ref.inviteCode != null
    var recording by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var voiceFile by remember { mutableStateOf<File?>(null) }

    fun startVoiceRecording() {
        try {
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(directory, "voice_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            voiceFile = file
            voiceRecorder = recorder
            recording = true
            info = "A gravar mensagem de voz. Toque em parar para enviar."
        } catch (_: Exception) {
            recording = false
            voiceRecorder?.release()
            voiceRecorder = null
            voiceFile?.delete()
            voiceFile = null
            vm.showError("Não foi possível iniciar a gravação. Verifique o microfone.")
        }
    }

    fun stopVoiceRecording() {
        val recorder = voiceRecorder
        val file = voiceFile
        voiceRecorder = null
        recording = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        voiceFile = null
        info = null
        if (file != null && file.exists() && file.length() > 1024L) {
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                vm.sendMedia(cr, uri, "audio") { file.delete() }
            }.onFailure { vm.showError("Não foi possível preparar a mensagem de voz.") }
        } else {
            file?.delete()
            vm.showError("A gravação ficou demasiado curta. Tente novamente.")
        }
    }

    val requestMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else vm.showError("Permita o acesso ao microfone para enviar mensagens de voz.")
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { voiceRecorder?.stop() }
            runCatching { voiceRecorder?.release() }
            voiceFile?.delete()
        }
    }

    LaunchedEffect(convId) {
        runCatching {
            disappearingSeconds = Api.get("/v1/chats/$convId/disappearing").optInt("seconds", 0)
        }
    }

    LaunchedEffect(convId) {
        while (true) {
            if (Api.hasInternet(context)) vm.refresh()
            delay(5000)
        }
    }

    LaunchedEffect(text, convId) {
        if (text.isBlank()) {
            vm.setTyping(false)
            return@LaunchedEffect
        }
        vm.setTyping(true)
        delay(5000)
        vm.setTyping(false)
    }
    DisposableEffect(convId) {
        onDispose { vm.setTyping(false) }
    }
    DisposableEffect(convId) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val incomingId = intent?.getStringExtra("conversation_id")
                if (incomingId == null || incomingId == convId) {
                    if (Api.hasInternet(context)) scope.launch { vm.refresh() }
                }
            }
        }
        val filter = IntentFilter("com.isabgroup.pimsg.MESSAGE_RECEIVED")
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { attachOpen = false; vm.sendMedia(cr, uri, "file") }
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { attachOpen = false; vm.sendMedia(cr, uri, "music") }
    }

    Column(Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { view.playClick(); onBack() }) { Icon(PiIcons.Back, "Voltar", tint = PiColors.White) }
            Text(title, color = PiColors.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (!isGroup && !ref.otherId.isNullOrBlank()) {
                IconButton(onClick = {
                    view.playClick()
                    peerProfileOpen = true
                    peerProfileLoading = true
                    scope.launch {
                        try {
                            val profile = Api.get("/v1/users/${ref.otherId}")
                            peerProfile = profile
                            val avatarKey = profile.optString("avatar_key").takeIf { it.isNotBlank() && it != "null" }
                            peerAvatar = avatarKey?.let { key ->
                                val cached = PiMsgMedia.findCached(context, key)
                                val file = cached ?: run {
                                    val downloaded = Api.download("/v1/media/$key")
                                    PiMsgMedia.store(context, key, downloaded.mime, "avatar", downloaded.bytes)
                                }
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            }
                        } catch (_: Exception) {
                            peerProfile = null
                        } finally { peerProfileLoading = false }
                    }
                }) { Icon(Icons.Filled.Person, "Ver perfil de ${title}", tint = PiColors.White) }
            }
            IconButton(onClick = { view.playClick(); optionsOpen = true }) { Icon(PiIcons.More, "Mais opções", tint = PiColors.White) }
        }

        DisableSelection {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.messages, key = { it.id }) { m -> Bubble(m, mine = m.pending || m.senderId == state.myId, vm = vm) }
            }
        }

        if (state.busy) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PiColors.White)
                Text("A preparar e enviar ficheiro...", color = PiColors.TextSecondary, fontSize = 12.sp)
            }
        }
        if (recording) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = PiColors.Danger)
                Text("A gravar mensagem de voz...", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.receivingMedia) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PiColors.Gray)
                Text("A receber ficheiros...", color = PiColors.TextSecondary, fontSize = 11.sp)
            }
        }
        info?.let {
            Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { info = null }.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        state.error?.let {
            Text(it, color = PiColors.Danger, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { vm.clearError() }.padding(horizontal = 20.dp, vertical = 6.dp))
        }

        state.typingName?.let { name ->
            Text("$name está a escrever…", color = PiColors.TextSecondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { view.playClick(); attachOpen = true }, enabled = !state.busy) {
                Icon(PiIcons.Plus, "Anexar", tint = PiColors.White)
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(PiColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = PiColors.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(PiColors.White),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Mensagem", color = PiColors.Gray, fontSize = 16.sp)
                        inner()
                    },
                )
            }
            IconButton(
                onClick = {
                    view.playClick()
                    if (recording) stopVoiceRecording()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceRecording()
                    else requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = !state.busy,
            ) {
                Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    if (recording) "Parar e enviar áudio" else "Gravar mensagem de voz",
                    tint = if (recording) PiColors.Danger else PiColors.White)
            }
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(PiColors.White)
                    .clickable(enabled = text.isNotBlank() && !state.busy) {
                        view.playClick()
                        vm.sendText(text)
                        text = ""
                        onShareConsumed()
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(PiIcons.Send, "Enviar", tint = Color.Black) }
        }
    }

    if (peerProfileOpen) {
        val profile = peerProfile
        AlertDialog(
            onDismissRequest = { peerProfileOpen = false },
            containerColor = PiColors.Surface,
            title = { Text(profile?.optString("name")?.takeIf { !it.isNullOrBlank() } ?: title, color = PiColors.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (peerAvatar != null) Image(peerAvatar!!.asImageBitmap(), "Fotografia de perfil", Modifier.size(88.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(88.dp).clip(CircleShape).background(PiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, "Avatar", tint = PiColors.Gray, modifier = Modifier.size(44.dp))
                    }
                    if (peerProfileLoading) Text("A carregar perfil...", color = PiColors.TextSecondary)
                    else if (profile == null) Text("Não foi possível carregar o perfil. Verifique a ligação e tente novamente.", color = PiColors.TextSecondary)
                    else {
                        val bio = profile.optString("bio")
                        if (bio.isNotBlank() && bio != "null") Text(bio, color = PiColors.TextSecondary)
                        val phone = profile.optString("phone")
                        if (phone.isNotBlank() && phone != "null") Text("Contacto: $phone", color = PiColors.White)
                        val links = profile.optJSONArray("social_links") ?: org.json.JSONArray()
                        for (i in 0 until links.length()) {
                            val link = links.optJSONObject(i) ?: continue
                            val url = link.optString("url")
                            if (url.startsWith("https://")) Text(link.optString("label", "Abrir ligação"), color = PiColors.White,
                                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) })
                        }
                        val video = profile.optString("intro_video_key")
                        if (video.isNotBlank() && video != "null") Text("Ver vídeo de apresentação", color = PiColors.White,
                            modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://pimsg.tchilopop.com/public-media/$video"))) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { peerProfileOpen = false }) { Text("Fechar", color = PiColors.White) } },
        )
    }

    if (optionsOpen) {
        AlertDialog(
            onDismissRequest = { optionsOpen = false },
            containerColor = PiColors.Surface,
            title = { Text(title, color = PiColors.White) },
            text = {
                Column {
                    OptionRow("Mensagens temporárias") {
                        optionsOpen = false
                        disappearingOpen = true
                    }
                    if (isGroup) {
                        OptionRow("Convidar para o grupo") {
                            optionsOpen = false
                            val link = "https://pimsg.tchilopop.com/g/${ref.inviteCode}"
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Entre no grupo ${ref.title} no PiMsg. Código de convite: ${ref.inviteCode}\n$link")
                            context.startActivity(Intent.createChooser(send, "Convidar"))
                        }
                        OptionRow("Denunciar grupo") { optionsOpen = false; reportOpen = true }
                        OptionRow("Sair do grupo") {
                            optionsOpen = false
                            scope.launch {
                                try { Api.post("/v1/chats/$convId/leave", JSONObject()); onBack() }
                                catch (e: Exception) { info = "Não foi possível sair do grupo." }
                            }
                        }
                    } else if (ref.otherId != null) {
                        OptionRow("Bloquear") {
                            optionsOpen = false
                            scope.launch {
                                try { Api.put("/v1/blocks/${ref.otherId}", JSONObject()); onBack() }
                                catch (e: Exception) { info = "Não foi possível bloquear." }
                            }
                        }
                        OptionRow("Denunciar") { optionsOpen = false; reportOpen = true }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { optionsOpen = false }) { Text("Fechar", color = PiColors.White) } },
        )
    }

    if (disappearingOpen) {
        val durations = listOf(
            0 to "Desativadas",
            86400 to "24 horas",
            604800 to "7 dias",
            7776000 to "90 dias",
        )
        AlertDialog(
            onDismissRequest = { disappearingOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Mensagens temporárias", color = PiColors.White) },
            text = {
                Column {
                    Text(
                        "As novas mensagens desta conversa serão eliminadas para todos após o período escolhido.",
                        color = PiColors.Gray,
                        fontSize = 14.sp,
                    )
                    durations.forEach { (seconds, label) ->
                        OptionRow(
                            if (disappearingSeconds == seconds) "✓ $label" else label
                        ) {
                            scope.launch {
                                try {
                                    Api.put(
                                        "/v1/chats/$convId/disappearing",
                                        JSONObject().put("seconds", seconds),
                                    )
                                    disappearingSeconds = seconds
                                    info = if (seconds == 0) "Mensagens temporárias desativadas."
                                        else "Mensagens temporárias ativadas: $label."
                                } catch (_: Exception) {
                                    info = "Não foi possível alterar esta definição."
                                }
                                disappearingOpen = false
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { disappearingOpen = false }) {
                    Text("Fechar", color = PiColors.White)
                }
            },
        )
    }

    if (reportOpen) {
        val reasons = listOf(
            "spam" to "Spam", "scam" to "Golpe", "harassment" to "Assédio", "inappropriate" to "Conteúdo impróprio",
            "illegal" to "Conteúdo ilegal", "impersonation" to "Falsificação de identidade", "other" to "Outro motivo",
        )
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Motivo da denúncia", color = PiColors.White) },
            text = {
                Column {
                    reasons.forEach { (key, label) ->
                        OptionRow(label) {
                            reportOpen = false
                            scope.launch {
                                try {
                                    Api.post(
                                        "/v1/reports",
                                        JSONObject().put("target_type", if (isGroup) "group" else "user")
                                            .put("target_id", if (isGroup) convId else ref.otherId).put("reason", key),
                                    )
                                    info = "Denúncia enviada. Obrigado."
                                } catch (e: Exception) { info = "Não foi possível enviar a denúncia." }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reportOpen = false }) { Text("Cancelar", color = PiColors.Gray) } },
        )
    }

    if (attachOpen) {
        AttachSheet(
            onDismiss = { attachOpen = false },
            onPickMedia = { uri -> attachOpen = false; vm.sendMedia(cr, uri, "file") },
            onPickFile = { pickFile.launch("*/*") },
            onPickMusic = { pickMusic.launch("audio/*") },
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun Bubble(m: Message, mine: Boolean, vm: ChatViewModel) {
    val shape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionsOpen by remember(m.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) PiColors.White else PiColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) Color.Black else PiColors.White
            val sub = if (mine) Color(0xFF55555B) else PiColors.Gray
            when (m.kind) {
                "text", "system" -> {
                    Text(m.body.orEmpty(), color = fg, fontSize = 16.sp)
                    if (!mine && !m.translatedBody.isNullOrBlank()) {
                        Text("TR · Traduzido", color = sub, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                        Text(m.translatedBody, color = fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                "image" -> ImageBody(m, vm, fg)
                else -> FileBody(m, vm, fg, sub)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(timeFormat.format(Date(m.createdAt)), color = sub, fontSize = 11.sp)
                Box {
                    IconButton(onClick = { actionsOpen = true }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Filled.MoreVert, "Opções da mensagem", tint = sub, modifier = Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                        if (m.kind == "text" && !m.body.isNullOrBlank()) {
                            DropdownMenuItem(text = { Text("Copiar mensagem", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("Mensagem PiMsg", m.body))
                            })
                            DropdownMenuItem(text = { Text("Partilhar mensagem", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.body)
                                context.startActivity(Intent.createChooser(send, "Partilhar mensagem"))
                            })
                        }
                        if (m.mediaId != null) {
                            DropdownMenuItem(text = { Text("Abrir ficheiro", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                scope.launch { openMedia(context, m, vm) }
                            })
                        }
                        if (mine && m.kind != "system") {
                            DropdownMenuItem(text = { Text("Apagar para todos", color = PiColors.Danger) }, onClick = {
                                actionsOpen = false
                                vm.deleteMessage(m)
                            })
                        } else if (!mine && m.kind != "system") {
                            DropdownMenuItem(text = { Text("Denunciar mensagem", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                vm.reportMessage(m)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBody(m: Message, vm: ChatViewModel, fg: Color) {
    val scope = rememberCoroutineScope()
    val id = m.mediaId
    val vmState by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(id, vmState.mediaRevision) {
        if (id != null && vm.isMediaCached(id)) vm.loadImage(id)
    }
    val bmp = id?.let { vm.images[it] }
    if (bmp != null) {
        Column {
            Image(bmp.asImageBitmap(), null, Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
            if (PiMsgMedia.saveToGallery(LocalContext.current)) {
                Text("Guardar na galeria", color = fg, fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            val ok = runCatching { vm.saveMediaToGallery(m) }.getOrDefault(false)
                            vm.showError(if (ok) "Fotografia guardada na pasta PiMsg da galeria." else "Não foi possível guardar a fotografia.")
                        }
                    }.padding(top = 6.dp))
            }
        }
    } else {
        Column(
            Modifier.size(width = 220.dp, height = 150.dp).clip(RoundedCornerShape(12.dp)).background(PiColors.SurfaceHigh)
                .clickable(enabled = id != null) { scope.launch { vm.loadImage(id!!) } },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Foto", color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(if (id == null) "Indisponível" else "Toque para ver", color = PiColors.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FileBody(m: Message, vm: ChatViewModel, fg: Color, sub: Color) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vmState by vm.state.collectAsStateWithLifecycle()
    val label = when (m.kind) { "video" -> "Vídeo"; "audio" -> "Áudio"; "music" -> "Música"; else -> "Ficheiro" }
    val cached = vmState.mediaRevision.let { vm.isMediaCached(m.mediaId) }
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            if (m.mediaId == null) "Indisponível" else m.body?.takeIf { it.isNotBlank() } ?: if (cached) "Disponível offline" else "Ficheiro por descarregar",
            color = sub, fontSize = 13.sp, maxLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (cached) "Abrir" else "Descarregar", color = fg, fontSize = 12.sp,
                modifier = Modifier.clickable(enabled = m.mediaId != null) { scope.launch { openMedia(context, m, vm) } }.padding(top = 8.dp))
            if (cached && PiMsgMedia.saveToGallery(context)) Text(
                when (m.kind) {
                    "image", "video" -> "Guardar na galeria"
                    "audio", "music" -> "Guardar nas músicas"
                    else -> "Guardar nos ficheiros"
                },
                color = fg, fontSize = 12.sp,
                modifier = Modifier.clickable { scope.launch {
                    val ok = runCatching { vm.saveMediaToGallery(m) }.getOrDefault(false)
                    val destination = when (m.kind) {
                        "image", "video" -> "galeria"
                        "audio", "music" -> "músicas"
                        else -> "ficheiros"
                    }
                    vm.showError(if (ok) "Ficheiro guardado em PiMsg/$destination." else "Não foi possível guardar. Verifique o formato e o espaço disponível.")
                } }.padding(top = 8.dp))
        }
    }
}

private suspend fun openMedia(context: Context, m: Message, vm: ChatViewModel) {
    try {
        if (!vm.isMediaCached(m.mediaId) && !Api.hasInternet(context)) {
            vm.showError("Este ficheiro ainda não foi descarregado. Liga-te à Internet para o abrir.")
            return
        }
        val file = vm.downloadMedia(m)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: when (m.kind) {
                "image" -> "image/*"
                "video" -> "video/*"
                "audio", "music" -> "audio/*"
                else -> "*/*"
            }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: android.content.ActivityNotFoundException) {
        vm.showError("Não há uma app no telemóvel para abrir este ficheiro.")
    } catch (e: Exception) {
        vm.showError("Não foi possível abrir o ficheiro. Verifique a ligação à Internet.")
    }
}

@Composable
private fun OptionRow(label: String, onClick: () -> Unit) {
    Text(label, color = PiColors.White, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}
