package com.isabgroup.pimsg.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import kotlinx.coroutines.launch

private data class MenuEntry(val key: String, val label: String)

private val main = listOf(
    MenuEntry("profile", "Perfil"),
    MenuEntry("links", "Meus links"),
    MenuEntry("bot", "PiBot"),
    MenuEntry("newstory", "Novo story"),
    MenuEntry("newgroup", "Novo grupo"),
    MenuEntry("settings", "Definições"),
)
private val legal = listOf(
    MenuEntry("terms", "Termos de uso"),
    MenuEntry("privacy", "Política de privacidade"),
    MenuEntry("refund", "Política de reembolso"),
    MenuEntry("support", "Suporte"),
)

// Menu em folha: fecha ao arrastar para baixo ou ao tocar no X. O item escolhido e entregue depois de fechar.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(hasMyStory: Boolean, onDismiss: () -> Unit, onItem: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() }
    }

    @Composable
    fun Entry(entry: MenuEntry, color: androidx.compose.ui.graphics.Color = PiColors.White) {
        Text(
            entry.label, color = color, fontSize = 17.sp,
            modifier = Modifier.fillMaxWidth().clickable { close { onItem(entry.key) } }.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PiColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PiColors.GrayDark) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Menu", color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { close() }) {
                    Icon(PiIcons.Close, contentDescription = "Fechar", tint = PiColors.Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (hasMyStory) Entry(MenuEntry("mystory", "Ver o meu story"))
            main.forEach { Entry(it) }
            Spacer(Modifier.height(8.dp))
            legal.forEach { Entry(it, PiColors.TextSecondary) }
            Entry(MenuEntry("signout", "Sair"), PiColors.Gray)
            Spacer(Modifier.height(16.dp))
        }
    }
}
