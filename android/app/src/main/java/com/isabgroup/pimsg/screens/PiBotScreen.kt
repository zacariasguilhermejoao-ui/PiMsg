package com.isabgroup.pimsg.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import com.isabgroup.pimsg.ui.PiBotAvatar
import com.isabgroup.pimsg.ui.PiButton
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import com.isabgroup.pimsg.ui.ScreenFrame
import com.isabgroup.pimsg.ui.playClick
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class AiMessage(val role: String, val content: String)

@Composable
fun PiBotScreen(onBack: () -> Unit) {
    var openChat by remember { mutableStateOf(false) }
    if (openChat) {
        PiAiChatScreen(onBack = { openChat = false })
    } else {
        ScreenFrame("PiBot", onBack) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PiBotAvatar(size = 58.dp)
                Column {
                    Text("Assistentes PiMsg", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ferramentas integradas na plataforma", color = PiColors.TextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Disponível", color = PiColors.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(18.dp))
                    .clickable { openChat = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PiBotAvatar(size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text("Pi AI", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Assistente inteligente", color = PiColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Perguntas, explicações, escrita, ideias e apoio na utilização do PiMsg.",
                        color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Text("›", color = PiColors.White, fontSize = 28.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Os restantes assistentes serão apresentados aqui depois de as respetivas funções estarem integradas e testadas.",
                color = PiColors.Gray, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun PiAiChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AiMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf<Int?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val transition = rememberInfiniteTransition(label = "pi-ai-working")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (busy) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(if (busy) 520 else 1600), RepeatMode.Reverse),
        label = "pi-ai-avatar-pulse",
    )

    LaunchedEffect(Unit) {
        runCatching {
            val saved = context.getSharedPreferences("pimsg_pi_ai", android.content.Context.MODE_PRIVATE)
                .getString("history", null)
            if (!saved.isNullOrBlank()) {
                val array = JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val role = item.optString("role")
                    val text = item.optString("content")
                    if (role in listOf("user", "assistant") && text.isNotBlank()) messages.add(AiMessage(role, text))
                }
            }
        }
        if (messages.isEmpty()) {
            messages.add(AiMessage("assistant", "Olá. Sou o Pi AI. Em que posso ajudar?"))
        }
        loaded = true
    }

    LaunchedEffect(messages.size, loaded) {
        if (loaded) {
            val array = JSONArray()
            messages.takeLast(40).forEach { array.put(JSONObject().put("role", it.role).put("content", it.content)) }
            context.getSharedPreferences("pimsg_pi_ai", android.content.Context.MODE_PRIVATE)
                .edit().putString("history", array.toString()).apply()
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val prompt = draft.trim()
        if (prompt.isEmpty() || busy || prompt.length > 2000) return
        val history = JSONArray()
        messages.filter { it.content.isNotBlank() }.takeLast(10).forEach {
            history.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        messages.add(AiMessage("user", prompt))
        draft = ""
        notice = null
        busy = true
        scope.launch {
            try {
                val response = Api.post(
                    "/v1/bots/pi-ai/chat",
                    JSONObject().put("message", prompt).put("history", history),
                )
                val answer = response.optString("reply").trim()
                if (answer.isBlank()) throw IllegalStateException("Resposta vazia")
                messages.add(AiMessage("assistant", answer))
                if (response.has("remaining")) remaining = response.optInt("remaining")
            } catch (e: Exception) {
                val message = if (e is ApiException) {
                    runCatching { JSONObject(e.message ?: "{}").optString("error") }.getOrNull()
                } else null
                val safe = message?.takeIf { it.isNotBlank() }
                    ?: "Não foi possível obter uma resposta. Verifique a ligação e tente novamente."
                notice = safe
                messages.add(AiMessage("assistant", safe))
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding().imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = { view.playClick(); onBack() }) {
                Icon(PiIcons.Back, contentDescription = "Voltar", tint = PiColors.White)
            }
            PiBotAvatar(size = 30.dp, modifier = Modifier.scale(if (busy) pulse else 1f))
            Column(Modifier.weight(1f)) {
                Text("Pi AI", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (busy) "A preparar uma resposta" else "Assistente PiMsg", color = PiColors.TextSecondary, fontSize = 11.sp)
            }
            remaining?.let { Text("$it hoje", color = PiColors.Gray, fontSize = 11.sp) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (message.role == "assistant") {
                        PiBotAvatar(size = 25.dp, modifier = Modifier.padding(end = 7.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth(0.86f)
                            .background(
                                if (message.role == "user") PiColors.SurfaceHigh else PiColors.Surface,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Text(
                            message.content,
                            color = PiColors.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PiBotAvatar(size = 27.dp, modifier = Modifier.scale(pulse))
                        Text("A responder…", color = PiColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.weight(1f).background(PiColors.Surface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2000) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = PiColors.White, fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(PiColors.White),
                    maxLines = 5,
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text("Escreva ao Pi AI…", color = PiColors.Gray, fontSize = 15.sp)
                        inner()
                    },
                )
            }
            Box(
                Modifier.size(46.dp).background(
                    if (draft.isNotBlank() && !busy) PiColors.White else PiColors.SurfaceHigh,
                    RoundedCornerShape(50),
                ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { view.playClick(); send() }, enabled = draft.isNotBlank() && !busy) {
                    Icon(PiIcons.Send, contentDescription = "Enviar", tint = if (draft.isNotBlank() && !busy) PiColors.Background else PiColors.Gray)
                }
            }
        }
        Text(
            "As respostas são geradas por IA. Confirme informações importantes antes de agir.",
            color = PiColors.Gray, fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
        )
    }
}
