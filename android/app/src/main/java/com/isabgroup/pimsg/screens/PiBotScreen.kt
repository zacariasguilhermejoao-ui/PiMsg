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
private data class AiBotInfo(val id: String, val name: String, val description: String, val available: Boolean, val requiresPaid: Boolean = false)

@Composable
fun PiBotScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    var bots by remember { mutableStateOf<List<AiBotInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var isPaid by remember { mutableStateOf(false) }
    var aiRemaining by remember { mutableStateOf(0) }
    var translationRemaining by remember { mutableStateOf<Int?>(4) }

    LaunchedEffect(Unit) {
        try {
            val response = Api.get("/v1/bots")
            isPaid = response.optBoolean("paid_access", false)
            aiRemaining = response.optInt("ai_remaining_today", 0)
            translationRemaining = if (response.isNull("translation_remaining_today")) null else response.optInt("translation_remaining_today")
            val array = response.optJSONArray("bots") ?: JSONArray()
            bots = (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                AiBotInfo(item.optString("id"), item.optString("name"), item.optString("description"),
                    item.optBoolean("available"), item.optBoolean("requires_paid"))
            }
        } catch (_: Exception) {
            catalogError = "Não foi possível carregar os bots. Verifica a ligação e tenta novamente."
        } finally { loading = false }
    }

    when (selected) {
        "pi-ai" -> PiAiChatScreen(onBack = { selected = null }, initialCredits = null, initialPro = true, initialProRemaining = aiRemaining)
        "pi-reminder" -> PiReminderScreen(onBack = { selected = null })
        "pi-business" -> PiBusinessScreen(onBack = { selected = null })
        "pi-bots" -> PiCustomBotsScreen(onBack = { selected = null })
        else -> ScreenFrame("Bots PiMsg", onBack) {
            Text("Assistentes e ferramentas", color = PiColors.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text("Os bots avançados utilizam recursos de IA e estão incluídos no plano pago.", color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
            Column(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(14.dp)).padding(13.dp)) {
                Text(if (isPaid) "Plano pago activo · $aiRemaining pedidos de IA hoje" else "Plano Free · tradução gratuita", color = PiColors.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(if (isPaid) "A tradução não tem limite diário no plano pago." else "Tens direito a ${translationRemaining ?: 4} de 4 traduções por dia. Os restantes bots exigem o plano pago.", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
            if (loading) Text("A carregar bots…", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            catalogError?.let { Text(it, color = PiColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp)) }
            bots.forEach { bot ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp)
                        .background(PiColors.Surface, RoundedCornerShape(15.dp))
                        .clickable(enabled = bot.available && bot.id != "pi-translate") { selected = bot.id }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PiBotAvatar(size = 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text(bot.name, color = PiColors.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(bot.description, color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            when { bot.id == "pi-translate" -> "Gratuito · ${translationRemaining ?: "ilimitado"} traduções restantes hoje"; bot.available -> "Disponível no teu plano"; else -> "Incluído no plano pago" },
                            color = if (bot.available) Color(0xFF8AB4F8) else PiColors.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                    Text(if (bot.available) "›" else "Pro", color = if (bot.available) PiColors.White else PiColors.Gray, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun BotTextInput(value: String, onValueChange: (String) -> Unit, hint: String, multiline: Boolean = false) {
    Box(Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 11.dp)) {
        if (value.isEmpty()) Text(hint, color = PiColors.Gray, fontSize = 13.sp)
        BasicTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = PiColors.White, fontSize = 14.sp, lineHeight = 19.sp),
            cursorBrush = SolidColor(PiColors.White), maxLines = if (multiline) 5 else 1)
    }
}

@Composable
private fun PiReminderScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    suspend fun load() { val r = Api.get("/v1/bots/reminders"); reminders = r.optJSONArray("reminders") ?: JSONArray() }
    LaunchedEffect(Unit) { try { load() } catch (e: Exception) { error = "Não foi possível carregar os lembretes." } }
    ScreenFrame("Pi Reminder", onBack) {
        Text("Organiza o teu dia", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Cria lembretes de tarefas, aniversários e compromissos.", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        BotTextInput(title, { title = it.take(120) }, "Nome do lembrete")
        Spacer(Modifier.height(8.dp))
        BotTextInput(details, { details = it.take(500) }, "Detalhes (opcional)", true)
        Spacer(Modifier.height(8.dp))
        Text("O lembrete será marcado para amanhã à mesma hora.", color = PiColors.Gray, fontSize = 11.sp)
        Text("Adicionar lembrete", color = PiColors.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp).clickable(enabled = title.isNotBlank() && !busy) {
            scope.launch { busy = true; try { Api.post("/v1/bots/reminders", JSONObject().put("title", title).put("details", details).put("due_at", System.currentTimeMillis() + 86400000)); title = ""; details = ""; load() } catch (e: Exception) { error = "Não foi possível guardar o lembrete." } finally { busy = false } }
        }.background(PiColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(13.dp))
        error?.let { Text(it, color = PiColors.Danger, fontSize = 12.sp) }
        Text("OS TEUS LEMBRETES", color = PiColors.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        val rows = reminders
        if (rows != null) for (i in 0 until rows.length()) {
            val item = rows.optJSONObject(i) ?: continue
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(PiColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.optString("title"), color = PiColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.optString("details"), color = PiColors.TextSecondary, fontSize = 11.sp)
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.optLong("due_at"))), color = PiColors.Gray, fontSize = 10.sp)
                }
                Text("Concluir", color = PiColors.White, fontSize = 11.sp, modifier = Modifier.clickable { scope.launch { runCatching { Api.patch("/v1/bots/reminders/${item.optString("id")}", JSONObject().put("status","done")); load() } } }.padding(8.dp))
                Text("×", color = PiColors.Danger, modifier = Modifier.clickable { scope.launch { runCatching { Api.delete("/v1/bots/reminders/${item.optString("id")}"); load() } } }.padding(8.dp))
            }
        }
    }
}

@Composable
private fun PiBusinessScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }
    var faq by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var customerMessage by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.get("/v1/bots/business").optJSONObject("profile") }.onSuccess { p ->
            if (p != null) { name=p.optString("business_name"); description=p.optString("description"); greeting=p.optString("greeting"); faq=p.optString("faq"); hours=p.optString("hours"); contact=p.optString("contact") }
        }.onFailure { error = "Preenche os dados e guarda a configuração do teu negócio." }
    }
    ScreenFrame("Pi Business", onBack) {
        Text("Atendimento inteligente", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Ensina o bot sobre o teu negócio. Ele responde apenas com as informações fornecidas.", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        BotTextInput(name,{name=it},"Nome do negócio")
        Spacer(Modifier.height(7.dp)); BotTextInput(description,{description=it},"Descrição do negócio",true)
        Spacer(Modifier.height(7.dp)); BotTextInput(greeting,{greeting=it},"Mensagem de boas-vindas",true)
        Spacer(Modifier.height(7.dp)); BotTextInput(faq,{faq=it},"Produtos, preços e perguntas frequentes",true)
        Spacer(Modifier.height(7.dp)); BotTextInput(hours,{hours=it},"Horário de funcionamento")
        Spacer(Modifier.height(7.dp)); BotTextInput(contact,{contact=it},"Contacto do negócio")
        Text("Guardar configuração", color = PiColors.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp).clickable(enabled = name.isNotBlank() && !busy) {
            scope.launch { busy=true; try { Api.put("/v1/bots/business", JSONObject().put("business_name",name).put("description",description).put("greeting",greeting).put("faq",faq).put("hours",hours).put("contact",contact).put("enabled",true)); error="Configuração guardada." } catch(e:Exception) { error="Não foi possível guardar. Confirma o teu plano." } finally { busy=false } }
        }.background(PiColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(13.dp))
        error?.let { Text(it, color = PiColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        Text("TESTAR O ATENDIMENTO", color = PiColors.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        BotTextInput(customerMessage,{customerMessage=it},"Escreve uma pergunta de cliente",true)
        Text("Enviar pergunta", color = PiColors.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp).clickable(enabled = customerMessage.isNotBlank() && !busy) {
            scope.launch { busy=true; try { answer=Api.post("/v1/bots/business/chat",JSONObject().put("message",customerMessage)).optString("reply") } catch(e:Exception) { answer="Não foi possível responder. Guarda primeiro os dados do negócio e confirma o plano." } finally { busy=false } }
        }.background(PiColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(12.dp))
        if (answer.isNotBlank()) Text(answer, color = PiColors.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).background(PiColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp))
    }
}

@Composable
private fun PiCustomBotsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var bots by remember { mutableStateOf<JSONArray?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() { bots = Api.get("/v1/bots/custom").optJSONArray("bots") ?: JSONArray() }
    LaunchedEffect(Unit) { runCatching { load() }.onFailure { error="Não foi possível carregar os teus bots." } }
    ScreenFrame("Pi Bots", onBack) {
        Text("Cria o teu próprio assistente", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Define o nome, a finalidade e as instruções. Podes criar até 10 bots.", color = PiColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        BotTextInput(name,{name=it},"Nome do bot")
        Spacer(Modifier.height(7.dp)); BotTextInput(description,{description=it},"Descrição",true)
        Spacer(Modifier.height(7.dp)); BotTextInput(instructions,{instructions=it},"Instruções detalhadas para o bot",true)
        Text("Criar bot", color = PiColors.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp).clickable(enabled = name.isNotBlank() && instructions.isNotBlank() && !busy) {
            scope.launch { busy=true; try { Api.post("/v1/bots/custom",JSONObject().put("name",name).put("description",description).put("instructions",instructions)); name="";description="";instructions="";load();error="Bot criado." } catch(e:Exception) { error="Não foi possível criar o bot. Confirma o teu plano e os campos." } finally { busy=false } }
        }.background(PiColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(12.dp))
        error?.let { Text(it,color=PiColors.TextSecondary,fontSize=11.sp,modifier=Modifier.padding(top=6.dp)) }
        Text("OS TEUS BOTS",color=PiColors.Gray,fontSize=11.sp,modifier=Modifier.padding(top=16.dp,bottom=7.dp))
        val list=bots
        if(list!=null) for(i in 0 until list.length()) {
            val item=list.optJSONObject(i) ?: continue
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp).background(PiColors.Surface, RoundedCornerShape(12.dp)).clickable { selectedId=item.optString("id");answer="" }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(item.optString("name"),color=PiColors.White,fontSize=14.sp,fontWeight=FontWeight.SemiBold); Text(item.optString("description"),color=PiColors.TextSecondary,fontSize=11.sp) }
                Text("Abrir",color=PiColors.White,fontSize=11.sp)
                Text("×",color=PiColors.Danger,modifier=Modifier.clickable { scope.launch { runCatching { Api.delete("/v1/bots/custom/${item.optString("id")}");load() } } }.padding(8.dp))
            }
        }
        if(selectedId!=null) {
            Text("CONVERSAR COM O BOT",color=PiColors.Gray,fontSize=11.sp,modifier=Modifier.padding(top=14.dp,bottom=7.dp))
            BotTextInput(message,{message=it},"Escreve uma mensagem",true)
            Text("Enviar",color=PiColors.White,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp).clickable(enabled=message.isNotBlank()&&!busy) {
                scope.launch { busy=true; try { answer=Api.post("/v1/bots/custom/$selectedId/chat",JSONObject().put("message",message)).optString("reply");message="" } catch(e:Exception) { answer="Não foi possível obter resposta. Confirma o plano e tenta novamente." } finally { busy=false } }
            }.background(PiColors.SurfaceHigh,RoundedCornerShape(12.dp)).padding(11.dp))
            if(answer.isNotBlank()) Text(answer,color=PiColors.White,fontSize=13.sp,modifier=Modifier.padding(top=8.dp).background(PiColors.Surface,RoundedCornerShape(12.dp)).padding(12.dp))
        }
    }
}

@Composable
private fun PiAiChatScreen(onBack: () -> Unit, initialCredits: Int?, initialPro: Boolean, initialProRemaining: Int?) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AiMessage>() }
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(initialProRemaining) }
    var creditBalance by remember { mutableStateOf(initialCredits) }
    var isPro by remember { mutableStateOf(initialPro) }
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
