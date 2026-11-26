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

private val items = listOf(
    "Perfil",
    "Definições",
    "Contas bloqueadas",
    "Termos de uso",
    "Política de privacidade",
    "Política de reembolso",
)

// Menu lateral em folha: fecha ao arrastar para baixo ou ao tocar no X.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PiColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PiColors.GrayDark) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
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
            items.forEach { label ->
                Text(
                    label,
                    color = PiColors.White,
                    fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable { }.padding(horizontal = 24.dp, vertical = 15.dp),
                )
            }
            Text(
                "Sair",
                color = PiColors.Gray,
                fontSize = 17.sp,
                modifier = Modifier.fillMaxWidth().clickable { close(onSignOut) }.padding(horizontal = 24.dp, vertical = 15.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
