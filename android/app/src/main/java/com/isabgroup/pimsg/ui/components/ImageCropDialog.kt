package com.isabgroup.pimsg.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiText as Text
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ImageCropDialog(
    bitmap: Bitmap,
    aspectRatio: Float,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    var zoom by remember(bitmap) { mutableStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(22.dp)).background(PiColors.Surface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, color = PiColors.White, fontSize = 19.sp)
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            offset += pan
                        }
                    },
            ) {
                val crop = cropRect(bitmap, size.width, size.height, zoom, offset)
                drawImage(
                    image = image,
                    srcOffset = IntOffset(crop.left, crop.top),
                    srcSize = IntSize(crop.width(), crop.height()),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }
            Text("Arraste a imagem para ajustar a posição. Use dois dedos ou a barra para ampliar.", color = PiColors.TextSecondary, fontSize = 12.sp)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..4f)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancelar", color = PiColors.TextSecondary) }
                TextButton(onClick = {
                    val crop = cropRect(bitmap, 1000f, 1000f / aspectRatio, zoom, offset)
                    onConfirm(Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height()))
                }) { Text("Usar fotografia", color = PiColors.White) }
            }
        }
    }
}

private fun cropRect(
    bitmap: Bitmap,
    viewportWidth: Float,
    viewportHeight: Float,
    zoom: Float,
    offset: Offset,
): Rect {
    val baseScale = max(viewportWidth / bitmap.width, viewportHeight / bitmap.height)
    val scale = baseScale * zoom.coerceIn(1f, 4f)
    val cropWidth = (viewportWidth / scale).coerceIn(1f, bitmap.width.toFloat())
    val cropHeight = (viewportHeight / scale).coerceIn(1f, bitmap.height.toFloat())
    val centerX = (bitmap.width / 2f - offset.x / scale)
        .coerceIn(cropWidth / 2f, bitmap.width - cropWidth / 2f)
    val centerY = (bitmap.height / 2f - offset.y / scale)
        .coerceIn(cropHeight / 2f, bitmap.height - cropHeight / 2f)
    val left = (centerX - cropWidth / 2f).roundToInt().coerceIn(0, bitmap.width - 1)
    val top = (centerY - cropHeight / 2f).roundToInt().coerceIn(0, bitmap.height - 1)
    val right = (centerX + cropWidth / 2f).roundToInt().coerceIn(left + 1, bitmap.width)
    val bottom = (centerY + cropHeight / 2f).roundToInt().coerceIn(top + 1, bitmap.height)
    return Rect(left, top, right, bottom)
}
