package com.isabgroup.pimsg

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TelaDataIncorreta(onVerificarNovamente: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("!", color = Color(0xFF3B82F6), fontSize = 76.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text("A data e hora estão incorretas", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Para continuares a usar o PiMsg, atualiza a data e a hora do teu telemóvel nas definições. Recomendamos ativar a opção de data e hora automáticas.",
                color = Color(0xFFA1A1AA), fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 24.sp
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6), contentColor = Color.White)
            ) {
                Text("Abrir Definições de Data e Hora", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onVerificarNovamente,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18181B), contentColor = Color(0xFFA1A1AA))
            ) {
                Text("Já atualizei, tentar novamente", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
