package com.isabgroup.pimsg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Aviso discreto quando não há Internet. As conversas guardadas continuam a abrir. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Text(
        "Sem ligação. Podes ler as tuas conversas; o que escreveres é enviado quando a Internet voltar.",
        color = PiColors.TextSecondary,
        fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().background(PiColors.Surface).padding(horizontal = 16.dp, vertical = 7.dp),
    )
}
