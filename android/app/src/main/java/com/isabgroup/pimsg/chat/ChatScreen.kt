package com.isabgroup.pimsg.chat

import android.content.Context
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
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.isabgroup.pimsg.data.Api
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
    val scope = rememberCoroutineScope()
    val isGroup = ref.inviteCode != null

    LaunchedEffect(convId) {
        runCatching {
            disappearingSeconds = Api.get("/v1/chats/$convId/disappearing").optInt("seconds", 0)
        }
    }

    LaunchedEffect(convId) {
        while (true) {
            vm.refresh()
            delay(4000)
        }
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

        info?.let {
            Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { info = null }.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        state.error?.let {
            Text(it, color = PiColors.Danger, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { vm.clearError() }.padding(horizontal = 20.dp, vertical = 6.dp))
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) PiColors.White else PiColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val fg = if (mine) Color.Black else PiColors.White
            val sub = if (mine) Color(0xFF55555B) else PiColors.Gray
            when (m.kind) {
                "text", "system" -> Text(m.body.orEmpty(), color = fg, fontSize = 16.sp)
                "image" -> ImageBody(m, vm, fg)
                else -> FileBody(m, vm, fg, sub)
            }
            Text(timeFormat.format(Date(m.createdAt)), color = sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 3.dp))
        }
    }
}

@Composable
private fun ImageBody(m: Message, vm: ChatViewModel, fg: Color) {
    val scope = rememberCoroutineScope()
    val id = m.mediaId
    val bmp = id?.let { vm.images[it] }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
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
    val label = when (m.kind) { "video" -> "Vídeo"; "audio" -> "Áudio"; "music" -> "Música"; else -> "Ficheiro" }
    Column(
        Modifier.clickable(enabled = m.mediaId != null) {
            scope.launch { openMedia(context, m, vm) }
        }.padding(vertical = 2.dp),
    ) {
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            if (m.mediaId == null) "Indisponível" else m.body?.takeIf { it.isNotBlank() } ?: "Toque para abrir",
            color = sub, fontSize = 13.sp, maxLines = 2,
        )
    }
}

private suspend fun openMedia(context: Context, m: Message, vm: ChatViewModel) {
    try {
        val category = when (m.kind) {
            "image" -> "Images"
            "video" -> "Videos"
            "audio", "music" -> "Audio"
            else -> "Documents"
        }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "PiMsg/$category").apply { mkdirs() }
        val existing = dir.listFiles()?.firstOrNull { it.name.startsWith("${m.mediaId}.") }
        val file = if (existing != null) existing else {
            val d = Api.download("/v1/media/${m.mediaId}")
            val mime = d.mime.substringBefore(";")
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            val safe = (m.body?.takeIf { it.isNotBlank() } ?: "ficheiro")
                .replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            val stored = File(dir, "${m.mediaId}_" + if (ext != null) "$safe.$ext" else safe)
            stored.writeBytes(d.bytes)
            stored
        }
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
        vm.showError("Não foi possível abrir o ficheiro.")
    }
}

@Composable
private fun OptionRow(label: String, onClick: () -> Unit) {
    Text(label, color = PiColors.White, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}
