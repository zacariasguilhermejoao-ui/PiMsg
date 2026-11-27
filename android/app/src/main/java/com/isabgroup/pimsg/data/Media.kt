package com.isabgroup.pimsg.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap

class Uploaded(val id: String, val mime: String, val name: String?)

private const val MAX_BYTES = 50L * 1024 * 1024

// Lê o conteúdo pelo ContentResolver. O URI pode apontar para a galeria, Documentos ou nuvem;
 // não deve ser convertido num caminho de ficheiro local nem depender do tamanho informado pelo provider.
suspend fun uploadUri(cr: ContentResolver, uri: Uri): Uploaded = withContext(Dispatchers.IO) {
    val mime = cr.getType(uri) ?: "application/octet-stream"
    var name: String? = null
    cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0)
    }
    val staged = File.createTempFile("pimsg_upload_", ".tmp")
    try {
        val input = cr.openInputStream(uri) ?: throw ApiException(400, "Não foi possível abrir o ficheiro seleccionado.")
        input.use { source -> staged.outputStream().use { target -> source.copyTo(target) } }
        val actualSize = staged.length()
        if (actualSize <= 0L) throw ApiException(400, "O ficheiro está vazio ou não pode ser lido.")
        if (actualSize > MAX_BYTES) throw ApiException(413, "ficheiro demasiado grande")
        val id = Api.upload({ FileInputStream(staged) }, actualSize, mime)
        Uploaded(id, mime, name)
    } finally {
        staged.delete()
    }
}

// Envia uma imagem já recortada sem depender de um URI temporário da galeria.
suspend fun uploadBitmap(context: Context, bitmap: Bitmap, displayName: String): Uploaded = withContext(Dispatchers.IO) {
    val staged = File(context.cacheDir, "pimsg_crop_${UUID.randomUUID()}.jpg")
    try {
        staged.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw ApiException(400, "Não foi possível preparar a fotografia.")
            }
        }
        val size = staged.length()
        if (size <= 0L) throw ApiException(400, "A fotografia está vazia.")
        if (size > MAX_BYTES) throw ApiException(413, "ficheiro demasiado grande")
        val id = Api.upload({ FileInputStream(staged) }, size, "image/jpeg")
        Uploaded(id, "image/jpeg", displayName)
    } finally {
        staged.delete()
    }
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
