package com.isabgroup.pimsg

import android.content.Intent
import android.net.Uri

/** Conteúdo partilhado de outra app (texto e/ou ficheiros). */
data class ShareIncoming(
    val text: String? = null,
    val uris: List<Uri> = emptyList(),
) {
    val hasContent: Boolean get() = !text.isNullOrBlank() || uris.isNotEmpty()
}

fun Intent.parseShare(): ShareIncoming? {
    when (action) {
        Intent.ACTION_SEND -> {
            val text = getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
            val uri = getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val uris = if (uri != null) listOf(uri) else emptyList()
            if (text == null && uris.isEmpty()) return null
            return ShareIncoming(text = text, uris = uris)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val text = getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
            @Suppress("DEPRECATION")
            val list = getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            if (text == null && list.isEmpty()) return null
            return ShareIncoming(text = text, uris = list)
        }
        else -> return null
    }
}
