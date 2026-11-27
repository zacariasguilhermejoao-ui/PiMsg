package com.isabgroup.pimsg.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import com.isabgroup.pimsg.ui.PiText as Text
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
import androidx.compose.ui.graphics.Color
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
private data class AiBotInfo(val id: String, val name: String, val description: String, val available: Boolean)

@Composable
fun PiBotScreen(onBack: () -> Unit) {
    var openChat by remember { mutableStateOf(false) }
    var bots by remember { mutableStateOf<List<AiBotInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var aiCredits by remember { mutableStateOf<Int?>(null) }
    var isProPlan by remember { mutableStateOf(false) }
    var proRemaining by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            val response = Api.get("/v1/bots")
            isProPlan = response.optString("plan") != "free"
            aiCredits = response.optInt("ai_credits", 15)
            if (isProPlan) proRemaining = response.optInt("remaining_today", 30)
            val array = response.optJSONArray("bots") ?: JSONArray()
            bots = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AiBotInfo(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    description = item.optString("description"),
                    available = item.optBoolean("available", false),
                )
            }
        } catch (_: Exception) {
            catalogError = "Não foi possível carregar os assistentes. Verifique a ligação e tente novamente."
        } finally {
            loading = false
        }
    }

    if (openChat) {
        PiAiChatScreen(onBack = { openChat = false })
    } else {
        ScreenFrame("PiBot", onBack) {
            Text("Assistentes de IA", color = PiColors.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Ferramentas de inteligência artificial disponíveis no PiMsg.",
                color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            )
            Text(
                if (isProPlan) "Pi AI Pro · ${proRemaining ?: 30} respostas disponíveis hoje"
                else "Créditos de IA · ${aiCredits ?: 15} / 15",
                color = PiColors.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(12.dp)).padding(13.dp),
            )
            if (!isProPlan) Text(
                "Cada tarefa simples custa 2 créditos. Pedidos mais longos podem custar até 5. Quando os créditos terminarem, o Pi AI fica indisponível até recarregar ou ativar o Pro.",
                color = PiColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 18.dp),
            ) else Spacer(Modifier.height(10.dp))
            Text("CATÁLOGO", color = PiColors.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (loading) {
                Text("A consultar os assistentes…", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            }
            catalogError?.let {
                Text(it, color = PiColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            }
            if (!loading && catalogError == null && bots.isEmpty()) {
                Text("Não existem assistentes disponíveis neste momento.", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            }
            bots.forEach { bot ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(PiColors.Surface, RoundedCornerShape(16.dp))
                        .clickable(enabled = bot.available && bot.id == "pi-ai") {
                            if (bot.id == "pi-ai") openChat = true
                        }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PiBotAvatar(size = 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text(bot.name, color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(bot.description, color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            if (bot.available) "Disponível" else "Temporariamente indisponível",
                            color = if (bot.available) Color(0xFF8AB4F8) else PiColors.Gray,
                            fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                    if (bot.available) Text("›", color = PiColors.White, fontSize = 25.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
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
    var creditBalance by remember { mutableStateOf<Int?>(null) }
    var isPro by remember { mutableStateOf(false) }
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
                isPro = response.optString("plan") != "free"
                if (isPro) remaining = response.optInt("remaining")
                else creditBalance = response.optInt("remaining")
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
            Box(contentAlignment = Alignment.BottomEnd) {
                PiBotAvatar(size = 23.dp)
                if (busy) Box(Modifier.size(7.dp).scale(pulse).background(Color(0xFF8AB4F8), CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text("Pi AI", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (busy) "A preparar uma resposta" else "Assistente PiMsg", color = PiColors.TextSecondary, fontSize = 11.sp)
            }
            (if (isPro) remaining else creditBalance)?.let {
                Text(if (isPro) "$it hoje" else "$it créditos", color = PiColors.Gray, fontSize = 11.sp)
            }
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
