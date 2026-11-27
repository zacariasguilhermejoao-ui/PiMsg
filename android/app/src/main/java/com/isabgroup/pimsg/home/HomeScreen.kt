package com.isabgroup.pimsg.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isabgroup.pimsg.R
import com.isabgroup.pimsg.chat.ChatRef
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.Push
import com.isabgroup.pimsg.ui.ChatRowSkeleton
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import com.isabgroup.pimsg.ui.openUrl
import com.isabgroup.pimsg.ui.playClick
import com.isabgroup.pimsg.ui.components.PresenteAnimado
import com.isabgroup.pimsg.ui.components.RoboMascoteAnimado
import com.isabgroup.pimsg.ui.piFieldColors
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private const val SITE = "https://pimsg.tchilopop.com"

@Composable
private fun EmptyTabState(tab: Tab, onOpenBots: () -> Unit) {
    val image = when (tab) {
        Tab.All -> R.drawable.icone_sem_conversas
        Tab.New -> R.drawable.icone_novas_pro
        Tab.Favorites -> R.drawable.icone_favoritos_estrela
        Tab.Groups -> R.drawable.icone_grupos_pro
    }
    val message = when (tab) {
        Tab.All -> "Ainda não tens conversas. Pesquisa uma pessoa para começar."
        Tab.New -> "Não tens novas conversas por enquanto."
        Tab.Favorites -> "As tuas conversas favoritas aparecerão aqui."
        Tab.Groups -> "Ainda não participas em nenhum grupo."
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            modifier = Modifier.size(190.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            message,
            color = PiColors.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(54.dp).clickable { onOpenBots() }, contentAlignment = Alignment.Center) {
                RoboMascoteAnimado(estaEscrevendo = false, tamanho = 50.dp)
            }
            PresenteAnimado(tamanho = 50.dp)
        }
    }
}

private fun parseColor(hex: String?): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { PiColors.Gray }

@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    onOpenChat: (ChatRef) -> Unit,
    onOpenStories: (List<StoryItem>) -> Unit,
    onNavigate: (String) -> Unit,
    initialContactCode: String? = null,
    initialContactSlug: String? = null,
    initialGroupCode: String? = null,
    onPiLinkConsumed: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var joinTarget by remember { mutableStateOf<GroupItem?>(null) }
    var profileTarget by remember { mutableStateOf<UserItem?>(null) }
    var publicProfile by remember { mutableStateOf<JSONObject?>(null) }
    var publicAvatar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var publicSocialIcons by remember { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    var ownProfileName by remember { mutableStateOf("O meu perfil") }
    var ownPlan by remember { mutableStateOf("free") }
    var ownProfileAvatar by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var profileLoading by remember { mutableStateOf(false) }
    var newGroupOpen by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickStory = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.postStory(context.contentResolver, uri)
    }

    LaunchedEffect(Unit) {
        // Recupera a lista anterior antes de tentar ligar ao servidor.
        runCatching {
            val saved = context.getSharedPreferences("pimsg_offline", android.content.Context.MODE_PRIVATE)
                .getString("chats", null)
            val tabKey = "chats_${state.tab.key}"
            val cachedJson = context.getSharedPreferences("pimsg_offline", android.content.Context.MODE_PRIVATE)
                .getString(tabKey, null) ?: saved
            if (!cachedJson.isNullOrBlank()) {
                val array = JSONArray(cachedJson)
                val cached = (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    ChatItem(
                        id = item.getString("id"),
                        title = item.optString("title", "Conversa"),
                        preview = item.optString("preview", ""),
                        unread = item.optInt("unread", 0),
                        isGroup = item.optBoolean("isGroup", false),
                        ringColor = item.optString("ringColor").takeIf { it.isNotBlank() },
                        otherId = item.optString("otherId").takeIf { it.isNotBlank() },
                        inviteCode = item.optString("inviteCode").takeIf { it.isNotBlank() },
                    )
                }
                vm.restoreCachedChats(cached)
            }
        }
        vm.load()
        runCatching {
            val prefs = Api.get("/v1/me/notifications")
            context.getSharedPreferences("pimsg_notifications", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("push_enabled", prefs.optBoolean("push_enabled", true))
                .putBoolean("popup_enabled", prefs.optBoolean("popup_enabled", true))
                .apply()
        }
        val pushEnabled = context.getSharedPreferences("pimsg_notifications", android.content.Context.MODE_PRIVATE)
            .getBoolean("push_enabled", true)
        if (pushEnabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            runCatching { Push.register(context) }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            vm.load(silent = true)
        }
    }
    LaunchedEffect(initialContactCode, initialContactSlug, initialGroupCode) {
        when {
            !initialContactCode.isNullOrBlank() -> vm.openRequestCode(initialContactCode) { id, user ->
                onOpenChat(ChatRef(id, user.name, user.id, null))
                onPiLinkConsumed()
            }
            !initialContactSlug.isNullOrBlank() -> vm.openContactSlug(initialContactSlug) { id, user ->
                onOpenChat(ChatRef(id, user.name, user.id, null))
                onPiLinkConsumed()
            }
            !initialGroupCode.isNullOrBlank() -> vm.joinGroupCode(initialGroupCode) { id, name, invite ->
                onOpenChat(ChatRef(id, name, null, invite))
                onPiLinkConsumed()
            }
        }
    }

    LaunchedEffect(state.chats, state.tab) {
        if (state.chats.isNotEmpty()) {
            val array = JSONArray()
            state.chats.forEach { item ->
                array.put(JSONObject().put("id", item.id).put("title", item.title)
                    .put("preview", item.preview).put("unread", item.unread)
                    .put("isGroup", item.isGroup).put("ringColor", item.ringColor)
                    .put("otherId", item.otherId).put("inviteCode", item.inviteCode))
            }
            context.getSharedPreferences("pimsg_offline", android.content.Context.MODE_PRIVATE)
                .edit().putString("chats_${state.tab.key}", array.toString()).putString("chats", array.toString()).apply()
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val me = Api.get("/v1/me")
            ownProfileName = me.optString("name", "O meu perfil")
            ownPlan = runCatching { Api.get("/v1/plan").optString("current_plan", "free") }.getOrDefault("free")
            val key = me.optString("avatar_key").takeIf { it.isNotBlank() && it != "null" }
            if (key != null) {
                val downloaded = Api.download("/v1/media/$key")
                ownProfileAvatar = android.graphics.BitmapFactory.decodeByteArray(downloaded.bytes, 0, downloaded.bytes.size)
            }
        }
    }
    LaunchedEffect(query) { delay(300); vm.search(query) }
    LaunchedEffect(profileTarget?.id) {
        val target = profileTarget ?: run { publicProfile = null; publicAvatar = null; return@LaunchedEffect }
        profileLoading = true
        publicProfile = null
        publicAvatar = null
        publicSocialIcons = emptyMap()
        try {
            val profile = Api.get("/v1/users/\u0024{target.id}")
            publicProfile = profile
            val socialLinks = profile.optJSONArray("social_links") ?: JSONArray()
            val icons = mutableMapOf<String, android.graphics.Bitmap>()
            for (index in 0 until socialLinks.length()) {
                val link = socialLinks.optJSONObject(index)?.optString("url") ?: continue
                if (!link.startsWith("https://")) continue
                runCatching {
                    val data = Api.download("/v1/favicon?url=" + Api.encode(link))
                    android.graphics.BitmapFactory.decodeByteArray(data.bytes, 0, data.bytes.size)?.let { icons[link] = it }
                }
            }
            publicSocialIcons = icons
            val key = if (profile.isNull("avatar_key")) null else profile.optString("avatar_key").takeIf { it.isNotBlank() }
            if (key != null) runCatching {
                val downloaded = Api.download("/v1/media/\u0024key")
                publicAvatar = android.graphics.BitmapFactory.decodeByteArray(downloaded.bytes, 0, downloaded.bytes.size)
            }
        } catch (_: Exception) { publicProfile = null }
        profileLoading = false
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    val myStories = state.myId?.let { state.stories[it] }.orEmpty()

    Column(Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { view.playClick(); menuOpen = true }) {
                Icon(PiIcons.Menu, contentDescription = "Menu", tint = PiColors.White)
            }
            if (!searching) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { view.playClick(); searching = true }) {
                    Icon(PiIcons.Search, contentDescription = "Pesquisar", tint = PiColors.White)
                }
            } else {
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(PiColors.Surface)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PiIcons.Search, contentDescription = null, tint = PiColors.Gray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = PiColors.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(PiColors.White),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("Nome, usuário ou código", color = PiColors.Gray, fontSize = 16.sp)
                                inner()
                            }
                        },
                    )
                }
                IconButton(onClick = { view.playClick(); searching = false; query = "" }) {
                    Icon(PiIcons.Close, contentDescription = "Fechar pesquisa", tint = PiColors.White)
                }
            }
        }

        val showResults = searching && query.trim().length >= 2

        state.notice?.let {
            Text(
                it, color = PiColors.White, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.clearNotice() }.background(PiColors.Surface).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        if (!showResults) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Tab.values().forEach { tab ->
                    val selected = tab == state.tab
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { view.playClick(); vm.selectTab(tab) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            tab.label,
                            color = if (selected) PiColors.White else PiColors.Gray,
                            fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(24.dp).height(3.dp).clip(CircleShape).background(if (selected) PiColors.White else Color.Transparent))
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                showResults -> LazyColumn(Modifier.fillMaxSize()) {
                    if (state.results.isEmpty() && state.groupResults.isEmpty()) item { Hint("Nenhum resultado. Confira o nome ou o código.") }
                    items(state.results, key = { "u" + it.id }) { u ->
                        PersonRow(
                            title = u.name,
                            subtitle = u.linkSubject?.let { "Assunto: $it" } ?: "Toque para conversar",
                            ring = null, unread = 0,
                            onClick = { profileTarget = u },
                        )
                    }
                    items(state.groupResults, key = { "g" + it.id }) { g ->
                        PersonRow(title = g.name, subtitle = "Grupo, toque para entrar", ring = null, unread = 0, onClick = { joinTarget = g })
                    }
                }
                state.loading -> Column { repeat(8) { ChatRowSkeleton() } }
                state.error != null -> Hint(state.error!!)
                state.chats.isEmpty() -> EmptyTabState(
                    tab = state.tab,
                    onOpenBots = { onNavigate("bot") },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.chats, key = { it.id }) { c ->
                        val stories = c.otherId?.let { state.stories[it] }.orEmpty()
                        PersonRow(
                            title = c.title, subtitle = if (c.isTyping) "A escrever..." else c.preview, unread = c.unread,
                            ring = if (stories.isNotEmpty()) parseColor(c.ringColor) else null,
                            onAvatarClick = if (stories.isNotEmpty()) ({ onOpenStories(stories) }) else null,
                            onClick = { onOpenChat(ChatRef(c.id, c.title, c.otherId, c.inviteCode)) },
                        )
                    }
                }
            }
        }

        Image(
            painterResource(R.drawable.pimsg_logo), contentDescription = "PiMsg",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp).height(26.dp),
        )
    }

    if (menuOpen) {
        MenuSheet(
            hasMyStory = myStories.isNotEmpty(),
            profileName = ownProfileName,
            profileAvatar = ownProfileAvatar,
            profilePlan = ownPlan,
            onDismiss = { menuOpen = false },
            onItem = { key ->
                when (key) {
                    "terms", "privacy", "refund", "support" -> openUrl(context, "$SITE/$key")
                    "newstory" -> pickStory.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    "mystory" -> onOpenStories(myStories)
                    "newgroup" -> { newGroupName = ""; newGroupOpen = true }
                    "signout" -> onSignOut()
                    else -> onNavigate(key)
                }
            },
        )
    }

    profileTarget?.let { target ->
        val profile = publicProfile
        AlertDialog(
            onDismissRequest = { profileTarget = null },
            containerColor = PiColors.Surface,
            title = { Text(profile?.optString("name")?.takeIf { !it.isNullOrBlank() } ?: target.name, color = PiColors.White) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (publicAvatar != null) Image(bitmap = publicAvatar!!.asImageBitmap(), contentDescription = "Fotografia do perfil", modifier = Modifier.size(88.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(88.dp).clip(CircleShape).background(PiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Person, contentDescription = "Avatar", tint = PiColors.Gray, modifier = Modifier.size(46.dp))
                    }
                    if (profileLoading) Text("A carregar perfil...", color = PiColors.TextSecondary)
                    else if (profile == null) Text("Não foi possível carregar este perfil.", color = PiColors.TextSecondary)
                    else {
                        val bio = profile.optString("bio")
                        if (bio.isNotBlank() && bio != "null") Text(bio, color = PiColors.TextSecondary)
                        if (profile.optBoolean("professional_profile")) Text("Perfil profissional", color = Color(0xFF8AB4F8), fontSize = 12.sp)
                        if (profile.optBoolean("verified_badge")) Text("✓ Perfil verificado", color = Color(0xFF8AB4F8), fontSize = 12.sp)
                        val links = profile.optJSONArray("social_links") ?: JSONArray()
                        (0 until links.length()).forEach { index ->
                            val item = links.optJSONObject(index) ?: return@forEach
                            val label = item.optString("label")
                            val link = item.optString("url")
                            if (link.startsWith("https://")) Row(Modifier.fillMaxWidth().clickable { openUrl(context, link) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                publicSocialIcons[link]?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp).clip(CircleShape)) }
                                Text(label, color = PiColors.White)
                            }
                        }
                        val introVideo = profile.optString("intro_video_key")
                        if (introVideo.isNotBlank() && introVideo != "null") Text("Ver vídeo de apresentação", color = PiColors.White, modifier = Modifier.fillMaxWidth().clickable { openUrl(context, "$SITE/public-media/$introVideo") }.padding(vertical = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !profileLoading && profile != null, onClick = {
                    vm.openDirect(target) { id -> profileTarget = null; searching = false; query = ""; onOpenChat(ChatRef(id, target.name, target.id, null)) }
                }) { Text("Adicionar contacto", color = PiColors.White) }
            },
            dismissButton = { TextButton(onClick = { profileTarget = null }) { Text("Fechar", color = PiColors.TextSecondary) } },
        )
    }
    joinTarget?.let { g ->
        AlertDialog(
            onDismissRequest = { joinTarget = null },
            containerColor = PiColors.Surface,
            title = { Text(g.name, color = PiColors.White) },
            text = {
                Text(
                    (g.description?.let { "$it\n\n" } ?: "") + "Quer ver as mensagens anteriores do grupo? Os ficheiros nunca são descarregados sozinhos.",
                    color = PiColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val code = query.trim()
                    joinTarget = null
                    vm.joinGroup(code, true) { id -> searching = false; query = ""; onOpenChat(ChatRef(id, g.name, null, code.uppercase())) }
                }) { Text("Com histórico", color = PiColors.White) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val code = query.trim()
                        joinTarget = null
                        vm.joinGroup(code, false) { id -> searching = false; query = ""; onOpenChat(ChatRef(id, g.name, null, code.uppercase())) }
                    }) { Text("Sem histórico", color = PiColors.White) }
                    TextButton(onClick = { joinTarget = null }) { Text("Cancelar", color = PiColors.Gray) }
                }
            },
        )
    }

    if (newGroupOpen) {
        AlertDialog(
            onDismissRequest = { newGroupOpen = false },
            containerColor = PiColors.Surface,
            title = { Text("Novo grupo", color = PiColors.White) },
            text = {
                OutlinedTextField(
                    value = newGroupName, onValueChange = { newGroupName = it.take(60) }, singleLine = true,
                    label = { Text("Nome do grupo") }, colors = piFieldColors(),
                )
            },
            confirmButton = {
                TextButton(enabled = newGroupName.isNotBlank(), onClick = {
                    val name = newGroupName
                    newGroupOpen = false
                    vm.createGroup(name) { id, invite -> onOpenChat(ChatRef(id, name.trim(), null, invite)) }
                }) { Text("Criar", color = PiColors.White) }
            },
            dismissButton = { TextButton(onClick = { newGroupOpen = false }) { Text("Cancelar", color = PiColors.Gray) } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = PiColors.Gray, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(28.dp))
}

// Linha de conversa. O circulo colorido aparece quando a pessoa tem story ativo; tocar nele abre o story.
@Composable
private fun PersonRow(
    title: String,
    subtitle: String,
    unread: Int,
    ring: Color?,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var avatar = if (ring != null) Modifier.border(2.5.dp, ring, CircleShape).padding(4.dp) else Modifier.padding(4.dp)
        if (onAvatarClick != null) avatar = avatar.clip(CircleShape).clickable(onClick = onAvatarClick)
        Box(avatar) {
            Image(
                painterResource(R.drawable.default_avatar), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = PiColors.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (unread > 0) {
            Box(
                Modifier.clip(CircleShape).background(PiColors.White).padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Text(unread.toString(), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
