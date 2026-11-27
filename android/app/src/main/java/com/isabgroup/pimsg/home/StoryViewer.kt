package com.isabgroup.pimsg.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.openRemoteMedia
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiIcons
import kotlinx.coroutines.launch
import org.json.JSONObject

// Visualizador de stories: X volta, Pular passa para o seguinte. Fotos avancam sozinhas ao fim de 6 segundos.
@Composable
fun StoryViewer(stories: List<StoryItem>, onClose: () -> Unit) {
    if (stories.isEmpty()) { onClose(); return }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    val story = stories[index]
    val isVideo = story.mime?.startsWith("video/") == true
    var bitmap by remember(story.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(story.id) { mutableStateOf(false) }
    val progress = remember(story.id) { Animatable(0f) }

    fun next() { if (index < stories.lastIndex) index++ else onClose() }

    LaunchedEffect(story.id) {
        runCatching { Api.post("/v1/stories/${story.id}/view", JSONObject()) }
        if (!isVideo) {
            try {
                val d = Api.download("/v1/media/${story.mediaId}")
                bitmap = BitmapFactory.decodeByteArray(d.bytes, 0, d.bytes.size)
                if (bitmap == null) failed = true
            } catch (e: Exception) {
                failed = true
            }
        }
    }
    LaunchedEffect(story.id, bitmap) {
        if (bitmap != null) {
            progress.animateTo(1f, tween(6000, easing = LinearEasing))
            next()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (isVideo) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Vídeo", color = PiColors.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Abrir vídeo", color = Color.Black, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(PiColors.White)
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                        .let { m -> m.then(Modifier.clickableNoRipple { scope.launch { runCatching { openRemoteMedia(context, story.mediaId, story.userName) } } }) },
                )
            }
        }
        if (failed) Text("Não foi possível abrir o story.", color = PiColors.Gray, modifier = Modifier.align(Alignment.Center))

        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                stories.forEachIndexed { i, _ ->
                    Box(Modifier.weight(1f).padding(horizontal = 2.dp).height(3.dp).clip(CircleShape).background(PiColors.GrayDark)) {
                        val fill = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(PiColors.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(story.userName, color = PiColors.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(PiIcons.Close, "Voltar", tint = PiColors.White) }
            }
        }

        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(20.dp), verticalAlignment = Alignment.Bottom) {
            Text(story.caption.orEmpty(), color = PiColors.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                "Pular", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(PiColors.White)
                    .clickableNoRipple { next() }.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
