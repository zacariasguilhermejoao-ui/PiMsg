package com.isabgroup.pimsg.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Icones vetoriais proprios, brancos por defeito. A cor final vem do parametro tint do Icon.
object PiIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Search = icon("Search") {
        moveTo(18f, 11f)
        arcToRelative(7f, 7f, 0f, true, true, -14f, 0f)
        arcToRelative(7f, 7f, 0f, true, true, 14f, 0f)
        moveTo(16.5f, 16.5f)
        lineTo(21f, 21f)
    }

    val Menu = icon("Menu") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 18f); lineTo(20f, 18f)
    }

    val Back = icon("Back") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }

    val Plus = icon("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val Send = icon("Send") {
        moveTo(12f, 19f); lineTo(12f, 5f)
        moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f)
    }

    val Close = icon("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val More = icon("More") {
        moveTo(12f, 5f)
        arcToRelative(1f, 1f, 0f, true, true, 0f, 0.01f)
        moveTo(12f, 12f)
        arcToRelative(1f, 1f, 0f, true, true, 0f, 0.01f)
        moveTo(12f, 19f)
        arcToRelative(1f, 1f, 0f, true, true, 0f, 0.01f)
    }
}
