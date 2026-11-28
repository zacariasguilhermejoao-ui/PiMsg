package com.isabgroup.pimsg.chat

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaMetadataRetriever
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.PiMsgMedia
import com.isabgroup.pimsg.data.decodeBitmapUri
import org.json.JSONObject
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import com.isabgroup.pimsg.ui.playClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

data class ChatRef(val id: String, val title: String, val otherId: String?, val inviteCode: String?)
private data class ForwardTarget(val id: String, val title: String)

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
    var pendingAttachments by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var attachmentCaption by remember { mutableStateOf("") }
    var attachmentConfirmOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pendingShare) {
        val share = pendingShare ?: return@LaunchedEffect
        if (!share.text.isNullOrBlank() && text.isEmpty()) {
            text = share.text
        }
        if (share.uris.isNotEmpty()) {
            pendingAttachments = share.uris
            attachmentCaption = text
            attachmentConfirmOpen = true
        }
    }
    var attachOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var scheduleDialogOpen by remember { mutableStateOf(false) }
    var scheduledListOpen by remember { mutableStateOf(false) }
    var scheduledItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var scheduling by remember { mutableStateOf(false) }
    var scheduledError by remember { mutableStateOf<String?>(null) }
    var scheduledAt by remember { mutableStateOf(System.currentTimeMillis() + 3600000L) }
    var reportOpen by remember { mutableStateOf(false) }
    var disappearingOpen by remember { mutableStateOf(false) }
    var disappearingSeconds by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf<String?>(null) }
    var peerProfileOpen by remember { mutableStateOf(false) }
    var peerProfileLoading by remember { mutableStateOf(false) }
    var peerProfile by remember { mutableStateOf<JSONObject?>(null) }
    var peerAvatar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var forwardMessage by remember { mutableStateOf<Message?>(null) }
    var forwardTargets by remember { mutableStateOf<List<ForwardTarget>>(emptyList()) }
    var forwardLoading by remember { mutableStateOf(false) }
    var forwardError by remember { mutableStateOf<String?>(null) }
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

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            attachOpen = false
            pendingAttachments = uris
            attachmentConfirmOpen = true
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            attachOpen = false
            pendingAttachments = uris
            attachmentConfirmOpen = true
        }
    }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            attachOpen = false
            pendingAttachments = uris
            attachmentConfirmOpen = true
        }
    }

    Column(Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { view.playClick(); onBack() }) { Icon(PiIcons.Back, "Voltar", tint = PiColors.White) }
            Text(title, color = PiColors.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (!isGroup && !ref.otherId.isNullOrBlank()) {
                IconButton(onClick = {
                    view.playClick()
                    peerProfileOpen = true
                    val profileCache = context.getSharedPreferences("pimsg_public_profiles", Context.MODE_PRIVATE)
                    val cachedProfile = profileCache.getString(ref.otherId, null)
                    peerProfile = cachedProfile?.let { runCatching { JSONObject(it) }.getOrNull() }
                    val cachedAvatarKey = peerProfile?.optString("avatar_key")?.takeIf { !it.isNullOrBlank() && it != "null" }
                    peerAvatar = cachedAvatarKey?.let { key ->
                        PiMsgMedia.findCached(context, key)?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
                    }
                    peerProfileLoading = peerProfile == null
                    scope.launch {
                        try {
                            val profile = Api.get("/v1/users/${ref.otherId}")
                            peerProfile = profile
                            profileCache.edit().putString(ref.otherId, profile.toString()).apply()
                            val avatarKey = profile.optString("avatar_key").takeIf { it.isNotBlank() && it != "null" }
                            if (avatarKey == null) {
                                peerAvatar = null
                            } else {
                                val cached = PiMsgMedia.findCached(context, avatarKey)
                                if (cached != null) peerAvatar = android.graphics.BitmapFactory.decodeFile(cached.absolutePath)
                                if (Api.hasInternet(context)) {
                                    val downloaded = Api.download("/v1/media/$avatarKey")
                                    val file = PiMsgMedia.store(context, avatarKey, downloaded.mime, "avatar", downloaded.bytes)
                                    peerAvatar = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                }
                            }
                        } catch (_: Exception) {
                            if (peerProfile == null) peerProfileLoading = false
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
                items(state.messages, key = { it.id }) { m ->
                    Bubble(
                        m,
                        mine = m.pending || m.senderId == state.myId,
                        vm = vm,
                        onForward = { message ->
                            forwardMessage = message
                            forwardLoading = true
                            forwardError = null
                            scope.launch {
                                try {
                                    val chats = Api.get("/v1/chats?tab=all").getJSONArray("chats")
                                    forwardTargets = (0 until chats.length()).mapNotNull { index ->
                                        val item = chats.optJSONObject(index) ?: return@mapNotNull null
                                        val id = item.optString("id")
                                        if (id.isBlank() || id == convId) return@mapNotNull null
                                        val isGroupTarget = item.optString("type") == "group"
                                        val label = if (isGroupTarget) item.optString("name") else item.optString("other_name")
                                        ForwardTarget(id, label.ifBlank { "Conversa" })
                                    }
                                } catch (_: Exception) {
                                    forwardError = "Não foi possível carregar as conversas."
                                } finally {
                                    forwardLoading = false
                                }
                            }
                        },
                    )
                }
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
            if (isGroup) {
                IconButton(onClick = {
                    view.playClick()
                    scheduledAt = System.currentTimeMillis() + 3600000L
                    scheduleDialogOpen = true
                    scheduledError = null
                }, enabled = text.isNotBlank() && !state.busy) {
                    Icon(Icons.Filled.Schedule, "Agendar mensagem", tint = if (text.isNotBlank()) PiColors.White else PiColors.Gray)
                }
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

    if (scheduleDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!scheduling) scheduleDialogOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Agendar mensagem", color = PiColors.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("A mensagem será enviada automaticamente para este grupo na data e hora escolhidas.", color = PiColors.TextSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = scheduledAt }
                            DatePickerDialog(context, { _, y, m, d ->
                                scheduledAt = Calendar.getInstance().apply {
                                    timeInMillis = scheduledAt
                                    set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                                }.timeInMillis
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(scheduledAt)), color = PiColors.White) }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = scheduledAt }
                            TimePickerDialog(context, { _, h, min ->
                                scheduledAt = Calendar.getInstance().apply {
                                    timeInMillis = scheduledAt
                                    set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                        }) { Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scheduledAt)), color = PiColors.White) }
                    }
                    scheduledError?.let { Text(it, color = PiColors.Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = !scheduling, onClick = {
                    scheduling = true
                    scope.launch {
                        try {
                            Api.post("/v1/chats/$convId/scheduled", JSONObject().put("body", text.trim()).put("scheduled_at", scheduledAt))
                            text = ""
                            onShareConsumed()
                            scheduleDialogOpen = false
                            info = "Mensagem agendada com sucesso."
                        } catch (e: Exception) {
                            scheduledError = e.message ?: "Não foi possível agendar a mensagem."
                        } finally { scheduling = false }
                    }
                }) { Text(if (scheduling) "A agendar..." else "Agendar", color = PiColors.White) }
            },
            dismissButton = { TextButton(enabled = !scheduling, onClick = { scheduleDialogOpen = false }) { Text("Cancelar", color = PiColors.TextSecondary) } },
        )
    }
    if (scheduledListOpen) {
        LaunchedEffect(scheduledListOpen) {
            if (scheduledListOpen) runCatching {
                val response = Api.get("/v1/chats/$convId/scheduled")
                val rows = response.getJSONArray("scheduled")
                scheduledItems = (0 until rows.length()).map { rows.getJSONObject(it) }
            }.onFailure { scheduledError = it.message ?: "Não foi possível carregar os agendamentos." }
        }
        AlertDialog(
            onDismissRequest = { scheduledListOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Mensagens agendadas", color = PiColors.White) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val pending = scheduledItems.filter { it.optString("status") == "pending" }
                    if (pending.isEmpty()) Text("Ainda não tens mensagens agendadas neste grupo.", color = PiColors.TextSecondary)
                    pending.forEach { item ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PiColors.SurfaceHigh).padding(12.dp)) {
                            Text(item.optString("body"), color = PiColors.White, fontSize = 14.sp)
                            Text(SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(Date(item.optLong("scheduled_at"))), color = PiColors.TextSecondary, fontSize = 12.sp)
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        Api.delete("/v1/chats/$convId/scheduled/${item.optString("id")}")
                                        scheduledItems = scheduledItems.filterNot { it.optString("id") == item.optString("id") }
                                    }.onFailure { scheduledError = it.message ?: "Não foi possível cancelar." }
                                }
                            }) { Text("Cancelar agendamento", color = PiColors.Danger) }
                        }
                    }
                    scheduledError?.let { Text(it, color = PiColors.Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { scheduledListOpen = false }) { Text("Fechar", color = PiColors.White) } },
        )
    }

    if (peerProfileOpen) {
        val profile = peerProfile
        Dialog(
            onDismissRequest = { peerProfileOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(22.dp))
                    .background(PiColors.Surface).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Perfil", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { peerProfileOpen = false }) { Text("Fechar", color = PiColors.White) }
                }
                if (peerAvatar != null) Image(peerAvatar!!.asImageBitmap(), "Fotografia de perfil", Modifier.size(112.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Box(Modifier.size(112.dp).clip(CircleShape).background(PiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, "Avatar", tint = PiColors.Gray, modifier = Modifier.size(54.dp))
                }
                Text(profile?.optString("name")?.takeIf { !it.isNullOrBlank() } ?: title, color = PiColors.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (peerProfileLoading) Text("A actualizar perfil…", color = PiColors.TextSecondary)
                else if (profile == null) Text("Perfil indisponível neste momento.", color = PiColors.TextSecondary)
                else {
                    val bio = profile.optString("bio")
                    if (bio.isNotBlank() && bio != "null") Text(bio, color = PiColors.TextSecondary, fontSize = 15.sp)
                    val phone = profile.optString("phone")
                    if (phone.isNotBlank() && phone != "null") Text("Contacto: $phone", color = PiColors.White)
                    val links = profile.optJSONArray("social_links") ?: org.json.JSONArray()
                    for (i in 0 until links.length()) {
                        val link = links.optJSONObject(i) ?: continue
                        val url = link.optString("url")
                        if (url.startsWith("https://")) Text(link.optString("label", "Abrir ligação"), color = PiColors.White,
                            modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }.padding(vertical = 6.dp))
                    }
                    val video = profile.optString("intro_video_key")
                    if (video.isNotBlank() && video != "null") Text("Ver vídeo de apresentação", color = PiColors.White,
                        modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://pimsg.tchilopop.com/public-media/$video"))) }.padding(vertical = 6.dp))
                }
            }
        }
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
                        OptionRow("Mensagens agendadas") {
                            optionsOpen = false
                            scheduledError = null
                            scheduledListOpen = true
                        }
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
                        Row(
                            Modifier.fillMaxWidth().clickable {
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
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.AccessTime, contentDescription = "Mensagens temporárias", tint = if (disappearingSeconds == seconds) PiColors.White else PiColors.Gray)
                            Text(label, color = PiColors.White, fontSize = 17.sp)
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

    if (forwardMessage != null) {
        AlertDialog(
            onDismissRequest = { forwardMessage = null; forwardTargets = emptyList() },
            containerColor = PiColors.Surface,
            title = { Text("Reencaminhar mensagem", color = PiColors.White) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (forwardLoading) Text("A carregar conversas…", color = PiColors.TextSecondary)
                    forwardError?.let { Text(it, color = PiColors.Danger) }
                    forwardTargets.forEach { target ->
                        OptionRow(target.title) {
                            val message = forwardMessage ?: return@OptionRow
                            forwardMessage = null
                            scope.launch {
                                try {
                                    val newMediaId = if (message.mediaId != null) {
                                        val downloaded = Api.download("/v1/media/${message.mediaId}")
                                        Api.upload(
                                            { java.io.ByteArrayInputStream(downloaded.bytes) },
                                            downloaded.bytes.size.toLong(),
                                            downloaded.mime,
                                        )
                                    } else null
                                    val payload = JSONObject().put("kind", message.kind)
                                        .put("body", message.body ?: "")
                                    if (newMediaId != null) payload.put("media_id", newMediaId)
                                    Api.post("/v1/chats/${target.id}/messages", payload)
                                    info = "Mensagem reencaminhada para ${target.title}."
                                } catch (_: Exception) {
                                    info = "Não foi possível reencaminhar esta mensagem."
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { forwardMessage = null; forwardTargets = emptyList() }) { Text("Fechar", color = PiColors.White) } },
        )
    }

    if (attachmentConfirmOpen && pendingAttachments.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                attachmentConfirmOpen = false
                pendingAttachments = emptyList()
                attachmentCaption = ""
                onShareConsumed()
            },
            containerColor = PiColors.Surface,
            title = { Text("Pré-visualizar envio", color = PiColors.White) },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${pendingAttachments.size} ficheiro(s) seleccionado(s).", color = PiColors.TextSecondary)
                    pendingAttachments.take(5).forEach { uri ->
                        val name = runCatching {
                            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        }.getOrNull() ?: uri.lastPathSegment ?: "Ficheiro"
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AttachmentPreview(context, uri)
                            Text(name, color = PiColors.White, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (pendingAttachments.size > 5) Text("E mais ${pendingAttachments.size - 5} ficheiro(s)…", color = PiColors.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = attachmentCaption,
                        onValueChange = { attachmentCaption = it.take(500) },
                        label = { Text("Descrição (opcional)") },
                        placeholder = { Text("Escreva uma descrição para a fotografia ou vídeo") },
                        minLines = 2,
                        colors = com.isabgroup.pimsg.ui.piFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = pendingAttachments
                    val caption = attachmentCaption
                    attachmentConfirmOpen = false
                    pendingAttachments = emptyList()
                    attachmentCaption = ""
                    onShareConsumed()
                    vm.sendMediaBatch(cr, selected, caption)
                }) { Text("Enviar", color = PiColors.White) }
            },
            dismissButton = {
                TextButton(onClick = {
                    attachmentConfirmOpen = false
                    pendingAttachments = emptyList()
                    attachmentCaption = ""
                    onShareConsumed()
                }) { Text("Cancelar", color = PiColors.TextSecondary) }
            },
        )
    }

    if (attachOpen) {
        AttachSheet(
            onDismiss = { attachOpen = false },
            onPickMedia = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onPickFile = { pickFile.launch(arrayOf("*/*")) },
            onPickMusic = { pickMusic.launch(arrayOf("audio/*")) },
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun AttachmentPreview(context: Context, uri: android.net.Uri) {
    var preview by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val mime = remember(uri) { context.contentResolver.getType(uri).orEmpty() }
    LaunchedEffect(uri) {
        preview = withContext(Dispatchers.IO) {
            runCatching {
                if (mime.startsWith("image/")) {
                    decodeBitmapUri(context.contentResolver, uri)
                } else if (mime.startsWith("video/")) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        runCatching { retriever.release() }
                    }
                } else null
            }.getOrNull()
        }
    }
    if (preview != null) {
        Image(
            preview!!.asImageBitmap(),
            contentDescription = "Pré-visualização do anexo",
            modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else if (mime.startsWith("image/") || mime.startsWith("video/")) {
        Box(
            Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(12.dp)).background(PiColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Text(if (mime.startsWith("video/")) "A preparar pré-visualização do vídeo…" else "A carregar fotografia…", color = PiColors.TextSecondary, fontSize = 12.sp) }
    }
}


@Composable
private fun Bubble(m: Message, mine: Boolean, vm: ChatViewModel, onForward: (Message) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionsOpen by remember(m.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape)
                .background(if (mine) Color(0xFFD9FDD3) else Color(0xFF1877F2))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) Color(0xFF142016) else Color.White
            val sub = if (mine) Color(0xFF536455) else Color(0xFFDCE9FF)
            when (m.kind) {
                "text", "system" -> {
                    Text(m.body.orEmpty(), color = fg, fontSize = 16.sp)
                    if (!mine && !m.translatedBody.isNullOrBlank()) {
                        Text("TR · Traduzido", color = sub, fontSize = 10.sp, modifier = Modifier.padding(top = 7.dp))
                        Text(m.translatedBody, color = fg, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                "image" -> ImageBody(m, vm, fg)
                "video" -> VideoBody(m, vm, fg)
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
                            DropdownMenuItem(text = { Text("Reencaminhar", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                onForward(m)
                            })
                        }
                        if (m.mediaId != null) {
                            DropdownMenuItem(text = { Text("Reencaminhar", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                onForward(m)
                            })
                            DropdownMenuItem(text = { Text("Abrir ficheiro", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                scope.launch { openMedia(context, m, vm) }
                            })
                        }
                        if (m.kind != "system") {
                            DropdownMenuItem(text = { Text("Apagar para mim", color = PiColors.White) }, onClick = {
                                actionsOpen = false
                                vm.deleteForMe(m)
                            })
                        }
                        if (mine && !m.pending && m.kind != "system") {
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
            if (!m.body.isNullOrBlank()) Text(m.body, color = fg, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
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
private fun VideoBody(m: Message, vm: ChatViewModel, fg: Color) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val id = m.mediaId
    LaunchedEffect(id, state.mediaRevision) {
        if (id != null && vm.isMediaCached(id)) vm.loadVideoThumbnail(m)
    }
    val thumbnail = id?.let { vm.videoThumbnails[it] }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(12.dp))
                .background(PiColors.SurfaceHigh)
                .clickable(enabled = id != null) { scope.launch { openMedia(context, m, vm) } },
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), "Pré-visualização do vídeo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("Vídeo", color = fg, fontSize = 15.sp)
            }
            Icon(Icons.Filled.PlayArrow, "Reproduzir vídeo", tint = Color.White, modifier = Modifier.size(42.dp))
        }
        if (!m.body.isNullOrBlank()) Text(m.body, color = fg, fontSize = 14.sp)
        if (thumbnail == null && id != null) {
            Text("Toque para descarregar e pré-visualizar", color = fg, fontSize = 11.sp)
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
