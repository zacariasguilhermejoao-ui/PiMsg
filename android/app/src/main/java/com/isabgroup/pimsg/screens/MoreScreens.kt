package com.isabgroup.pimsg.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

        Spacer(Modifier.height(24.dp))
        PiButton("Guardar", {
            scope.launch {
                try {
                    Api.patch("/v1/me", JSONObject().put("name", name).put("bio", bio).put("show_phone", showPhone))
                    msg = "Perfil guardado."
                } catch (e: Exception) { msg = "Não foi possível guardar." }
            }
        }, enabled = name.isNotBlank())
        msg?.let { Text(it, color = PiColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- Definições
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun loadBlocked() {
        try {
            val arr = Api.get("/v1/blocks").getJSONArray("users")
            blocked = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> o.getString("id") to o.getString("name") } }
        } catch (e: Exception) { /* lista vazia */ }
    }
    LaunchedEffect(Unit) { loadBlocked() }

    ScreenFrame("Definições", onBack) {
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
