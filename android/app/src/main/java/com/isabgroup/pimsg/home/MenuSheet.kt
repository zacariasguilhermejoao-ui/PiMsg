package com.isabgroup.pimsg.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import com.isabgroup.pimsg.ui.PiBotAvatar
import com.isabgroup.pimsg.ui.playClick
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

private data class MenuEntry(val key: String, val label: String)

private val account = listOf(
    MenuEntry("qr", "Código QR"),
    MenuEntry("links", "Meus links"),
    MenuEntry("plans", "Subscrições"),
)
private val business = listOf(
    MenuEntry("business", "PiMsg Business"),
)
private val create = listOf(
    MenuEntry("newstory", "Criar story"),
    MenuEntry("newgroup", "Criar grupo"),
)
private val app = listOf(
    MenuEntry("tools", "Ferramentas"),
    MenuEntry("accessibility", "Acessibilidade"),
    MenuEntry("bot", "PiBot"),
    MenuEntry("settings", "Definições"),
)

// O menu principal mostra o perfil no topo. Termos e políticas ficam dentro de Definições.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    hasMyStory: Boolean,
    profileName: String,
    profileAvatar: Bitmap?,
    profilePlan: String,
    onDismiss: () -> Unit,
    onItem: (String) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); after() }
    }
    @Composable
    fun Heading(value: String) {
        Text(value, color = PiColors.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 18.dp, bottom = 4.dp))
    }
    val view = LocalView.current
    @Composable
    fun Entry(entry: MenuEntry) {
        Row(
            Modifier.fillMaxWidth().clickable { view.playClick(); close { onItem(entry.key) } }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (entry.key) {
                "bot" -> PiBotAvatar(size = 25.dp)
                "plans" -> Icon(Icons.Filled.AttachMoney, contentDescription = "Subscrições", tint = PiColors.White, modifier = Modifier.size(24.dp))
                "business" -> Icon(Icons.Filled.Storefront, contentDescription = "PiMsg Business", tint = PiColors.White, modifier = Modifier.size(24.dp))
                "settings" -> Icon(PiIcons.More, contentDescription = null, tint = PiColors.White, modifier = Modifier.size(24.dp))
                "links" -> Icon(Icons.Filled.Person, contentDescription = null, tint = PiColors.White, modifier = Modifier.size(24.dp))
                "newstory" -> Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = PiColors.White, modifier = Modifier.size(24.dp))
                "newgroup" -> Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = PiColors.White, modifier = Modifier.size(24.dp))
                "qr" -> Icon(Icons.Filled.QrCode2, contentDescription = "Código QR", tint = PiColors.White, modifier = Modifier.size(24.dp))
                "tools" -> Icon(Icons.Filled.Build, contentDescription = "Ferramentas", tint = PiColors.White, modifier = Modifier.size(24.dp))
                "accessibility" -> Icon(Icons.Filled.AccessibilityNew, contentDescription = "Acessibilidade", tint = PiColors.White, modifier = Modifier.size(24.dp))
                else -> Spacer(Modifier.size(24.dp))
            }
            Text(entry.label, color = PiColors.White, fontSize = 16.sp)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PiColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PiColors.GrayDark) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().clickable { close { onItem("profile") } }.padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(PiColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                    if (profileAvatar != null) {
                        Image(profileAvatar.asImageBitmap(), contentDescription = "A minha fotografia de perfil",
                            modifier = Modifier.fillMaxWidth().size(88.dp).clip(CircleShape))
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = "Perfil", tint = PiColors.Gray, modifier = Modifier.size(46.dp))
                    }
                }
                Text(profileName.ifBlank { "O meu perfil" }, color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Ver e editar perfil", color = PiColors.TextSecondary, fontSize = 13.sp)
                val language = java.util.Locale.getDefault().language
                val planLabel = when { profilePlan.startsWith("business") -> "Business"; profilePlan.startsWith("pro") -> "Pro"; profilePlan.startsWith("plus") -> "Plus"; else -> when (language) {
                    "en" -> "Free"
                    "es" -> "Gratis"
                    "fr" -> "Gratuit"
                    else -> "Grátis"
                } }
                Text("Plano $planLabel", color = androidx.compose.ui.graphics.Color(0xFF9BE7A5), fontSize = 12.sp)
            }
            if (hasMyStory) Entry(MenuEntry("mystory", "Ver o meu story"))
            Heading("A MINHA CONTA")
            account.forEach { Entry(it) }
            Heading("CRIAR")
            create.forEach { Entry(it) }
            Heading("APLICAÇÃO")
            app.forEach { Entry(it) }
            Spacer(Modifier.size(12.dp))
        }
    }
}
