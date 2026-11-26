package com.isabgroup.pimsg.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

class Uploaded(val id: String, val mime: String, val name: String?)

private const val MAX_BYTES = 50L * 1024 * 1024

// Le o ficheiro escolhido no telemovel e envia-o para a API (R2)
suspend fun uploadUri(cr: ContentResolver, uri: Uri): Uploaded {
    val mime = cr.getType(uri) ?: "application/octet-stream"
    var name: String? = null
    var size = -1L
    cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            name = c.getString(0)
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    if (size <= 0) size = cr.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    if (size <= 0) throw ApiException(400, "ficheiro invalido")
    if (size > MAX_BYTES) throw ApiException(413, "ficheiro demasiado grande")
    val id = Api.upload({ cr.openInputStream(uri) }, size, mime)
    return Uploaded(id, mime, name)
}

// Descarrega so quando o utilizador pede e abre com outra app do telemovel
suspend fun openRemoteMedia(context: Context, mediaId: String, displayName: String?) {
    val d = Api.download("/v1/media/$mediaId")
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val mime = d.mime.substringBefore(";")
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
    val safe = (displayName?.takeIf { it.isNotBlank() } ?: "ficheiro").replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = File(dir, if (ext != null && !safe.endsWith(".$ext")) "$safe.$ext" else safe)
    file.writeBytes(d.bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
