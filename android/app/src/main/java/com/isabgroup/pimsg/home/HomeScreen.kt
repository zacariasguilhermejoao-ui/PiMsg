package com.isabgroup.pimsg.home

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.isabgroup.pimsg.R
import com.isabgroup.pimsg.ui.ChatRowSkeleton
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import kotlinx.coroutines.delay

private fun parseColor(hex: String?): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { PiColors.Gray }

@Composable
fun HomeScreen(onSignOut: () -> Unit, onOpenChat: (String, String) -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(query) { delay(300); vm.search(query) }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding()) {
        // Topo: menu de tres traços e lupa. A lupa abre o campo de pesquisa; o X fecha.
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(PiIcons.Menu, contentDescription = "Menu", tint = PiColors.White)
            }
            if (!searching) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searching = true }) {
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
                IconButton(onClick = { searching = false; query = "" }) {
                    Icon(PiIcons.Close, contentDescription = "Fechar pesquisa", tint = PiColors.White)
                }
            }
        }

        val showResults = searching && query.trim().length >= 2

        state.notice?.let {
            Text(it, color = PiColors.Danger, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        }

        if (!showResults) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Tab.values().forEach { tab ->
                    val selected = tab == state.tab
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.selectTab(tab) }.padding(horizontal = 10.dp, vertical = 8.dp),
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
                    if (state.results.isEmpty()) item { Hint("Nenhum resultado. Confira o nome ou o código.") }
                    items(state.results, key = { it.id }) { u ->
                        PersonRow(
                            title = u.name, subtitle = "Toque para conversar", ring = null, unread = 0,
                            onClick = { vm.openDirect(u) { id -> searching = false; query = ""; onOpenChat(id, u.name) } },
                        )
                    }
                }
                state.loading -> Column { repeat(8) { ChatRowSkeleton() } }
                state.error != null -> Hint(state.error!!)
                state.chats.isEmpty() -> Hint(
                    if (state.tab == Tab.All) "Nenhuma conversa ainda. Pesquise um nome ou código para começar."
                    else "Nada por aqui."
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.chats, key = { it.id }) { c ->
                        PersonRow(
                            title = c.title, subtitle = c.preview, unread = c.unread,
                            ring = if (c.hasStory) parseColor(c.ringColor) else null,
                            onClick = { onOpenChat(c.id, c.title) },
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

    if (menuOpen) MenuSheet(onDismiss = { menuOpen = false }, onSignOut = onSignOut)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = PiColors.Gray, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(28.dp))
}

// Linha de conversa. O circulo colorido aparece quando a pessoa tem story ativo.
@Composable
private fun PersonRow(title: String, subtitle: String, unread: Int, ring: Color?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ringModifier = if (ring != null) Modifier.border(2.5.dp, ring, CircleShape).padding(4.dp) else Modifier.padding(4.dp)
        Box(Modifier.then(ringModifier)) {
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
