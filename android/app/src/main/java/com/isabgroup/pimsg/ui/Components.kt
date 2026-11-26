package com.isabgroup.pimsg.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.R

/** Ícone estático reutilizável do assistente PiBot. */
@Composable
fun PiBotAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    contentDescription: String = "PiBot",
) {
    Image(
        painter = painterResource(R.drawable.pi_bot),
        contentDescription = contentDescription,
        modifier = modifier.then(Modifier.height(size)),
    )
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // sem navegador disponivel
    }
}

@Composable
fun piFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PiColors.White, unfocusedBorderColor = PiColors.GrayDark,
    focusedTextColor = PiColors.White, unfocusedTextColor = PiColors.White, cursorColor = PiColors.White,
    focusedLabelColor = PiColors.White, unfocusedLabelColor = PiColors.Gray,
)

/** Botão principal de ação — toca som de clique. */
@Composable
fun PiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    sound: Boolean = true,
) {
    val view = LocalView.current
    Button(
        onClick = {
            if (sound && enabled) view.playClick()
            onClick()
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) PiColors.White else PiColors.SurfaceHigh,
            contentColor = if (primary) Color.Black else PiColors.White,
            disabledContainerColor = PiColors.GrayDark, disabledContentColor = PiColors.Gray,
        ),
    ) { Text(text, fontSize = 16.sp) }
}

@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Voltar: sem som (navegação comum)
            IconButton(onClick = onBack) { Icon(PiIcons.Back, "Voltar", tint = PiColors.White) }
            Text(title, color = PiColors.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            content = content,
        )
    }
}
