package com.isabgroup.pimsg.chat

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.isabgroup.pimsg.ui.PiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GalleryItem(val uri: Uri, val isVideo: Boolean)

private fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasGalleryAccess(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 ->
            (ok(Manifest.permission.READ_MEDIA_IMAGES) && ok(Manifest.permission.READ_MEDIA_VIDEO)) ||
                ok(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> ok(Manifest.permission.READ_MEDIA_IMAGES) || ok(Manifest.permission.READ_MEDIA_VIDEO)
        else -> ok(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

// As 90 fotos e videos mais recentes do telemovel
private suspend fun queryRecent(cr: ContentResolver): List<GalleryItem> = withContext(Dispatchers.IO) {
    val out = ArrayList<GalleryItem>()
    try {
        val files = MediaStore.Files.getContentUri("external")
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, type)
        val selection = "$type=? OR $type=?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        cr.query(files, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext() && out.size < 90) {
                val id = c.getLong(0)
                val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out.add(GalleryItem(ContentUris.withAppendedId(base, id), video))
            }
        }
    } catch (e: Exception) {
        // sem acesso: devolve a lista vazia
    }
    out
}

private suspend fun loadThumb(cr: ContentResolver, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            cr.loadThumbnail(uri, Size(256, 256), null)
        } else {
            cr.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onDismiss: () -> Unit,
    onPickMedia: (List<Uri>) -> Unit,
    onPickFile: () -> Unit,
    onPickMusic: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var galleryOpen by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PiColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PiColors.GrayDark) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachOption("Galeria", Modifier.weight(1f), selected = galleryOpen) { galleryOpen = !galleryOpen }
                AttachOption("Ficheiro", Modifier.weight(1f)) { onPickFile() }
                AttachOption("Música", Modifier.weight(1f)) { onPickMusic() }
            }
            Spacer(Modifier.height(14.dp))
            if (galleryOpen) {
                GalleryGrid(
                    selected = selectedUris.toSet(),
                    onPick = { uri ->
                        selectedUris = if (uri in selectedUris) selectedUris.filterNot { it == uri }
                            else selectedUris + uri
                    },
                )
                if (selectedUris.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    AttachOption("Adicionar ${selectedUris.size} seleccionado(s)", Modifier.fillMaxWidth(), selected = true) {
                        onPickMedia(selectedUris)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttachOption(label: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (selected) PiColors.White else PiColors.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.Black else PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GalleryGrid(selected: Set<Uri>, onPick: (Uri) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasGalleryAccess(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasGalleryAccess(ctx)
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(galleryPermissions()) }

    var items by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    LaunchedEffect(granted) { if (granted) items = queryRecent(ctx.contentResolver) }

    if (!granted) {
        Column {
            Text("Permita o acesso às fotos para escolher da galeria.", color = PiColors.TextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            AttachOption("Permitir acesso", Modifier.fillMaxWidth()) { launcher.launch(galleryPermissions()) }
        }
        return
    }
    if (items.isEmpty()) {
        Text("Nenhuma foto ou vídeo encontrado.", color = PiColors.TextSecondary, fontSize = 15.sp)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(380.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            val thumb by produceState<Bitmap?>(null, item.uri) { value = loadThumb(ctx.contentResolver, item.uri) }
            Box(Modifier.aspectRatio(1f).background(PiColors.SurfaceHigh).clickable { onPick(item.uri) }) {
                thumb?.let {
                    Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
                if (item.uri in selected) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(20.dp))
                        .background(PiColors.White).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("✓", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (item.isVideo) {
                    Text(
                        "Vídeo", color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
