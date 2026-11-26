package com.isabgroup.pimsg.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.ui.PiBotAvatar
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.ScreenFrame

@Composable
fun PiBotScreen(onBack: () -> Unit) {
    var previewWorking by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "pibot-working")
    val floatY by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (previewWorking) 8f else 2f,
        animationSpec = infiniteRepeatable(tween(if (previewWorking) 520 else 1800), RepeatMode.Reverse),
        label = "bot-float",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (previewWorking) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(if (previewWorking) 650 else 1500), RepeatMode.Reverse),
        label = "bot-pulse",
    )

    ScreenFrame(title = "PiBot", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(196.dp)) {
                Box(
                    Modifier.size(172.dp).scale(pulse).alpha(if (previewWorking) 0.22f else 0.08f)
                        .border(BorderStroke(2.dp, PiColors.White), CircleShape),
                )
                Box(
                    Modifier.size(138.dp).background(PiColors.Surface, CircleShape)
                        .border(1.dp, PiColors.GrayDark, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    PiBotAvatar(modifier = Modifier.offset(y = (-floatY).dp), size = 108.dp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("PiBot", color = PiColors.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (previewWorking) "PiBot está a trabalhar" else "O teu assistente no PiMsg",
                color = if (previewWorking) PiColors.White else PiColors.TextSecondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.fillMaxWidth().background(PiColors.Surface, RoundedCornerShape(18.dp)).padding(18.dp),
            ) {
                Text("Assistente inteligente", color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "O espaço do PiBot está preparado. A ligação à inteligência artificial e às ferramentas será ativada numa próxima implementação.",
                    color = PiColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { previewWorking = !previewWorking },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PiColors.White,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (previewWorking) "Parar demonstração" else "Ver animação de trabalho", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "A animação neste ecrã é uma pré-visualização visual. O PiBot ainda não está ligado ao serviço de IA.",
                color = PiColors.Gray,
                fontSize = 12.sp,
            )
        }
    }
}
