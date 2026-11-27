package com.isabgroup.pimsg.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isabgroup.pimsg.ui.PiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onDismiss: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    onPickMusic: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PiColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PiColors.GrayDark) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AttachOption(Icons.Filled.PhotoLibrary, "Galeria", Modifier.weight(1f)) { onDismiss(); onPickMedia() }
                AttachOption(Icons.Filled.MusicNote, "Música", Modifier.weight(1f)) { onDismiss(); onPickMusic() }
                AttachOption(Icons.Filled.FolderOpen, "Ficheiros", Modifier.weight(1f)) { onPickFile() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttachOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(PiColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = PiColors.White, modifier = Modifier.size(27.dp))
        }
        Text(label, color = PiColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
