package com.isabgroup.pimsg.data

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Armazenamento offline de multimédia do PiMsg.
 * A pasta Android/media/<package>/PiMsg é própria da aplicação e visível no gestor de ficheiros.
 */
object PiMsgMedia {
    private const val PREFS = "pimsg_media_preferences"
    const val DOWNLOAD_NEVER = "never"
    const val DOWNLOAD_WIFI = "wifi"
    const val DOWNLOAD_ALWAYS = "always"

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun downloadMode(context: Context): String =
        preferences(context).getString("auto_download", DOWNLOAD_WIFI) ?: DOWNLOAD_WIFI
    fun setDownloadMode(context: Context, mode: String) {
        preferences(context).edit().putString("auto_download", mode).apply()
    }
    fun saveToGallery(context: Context): Boolean =
        preferences(context).getBoolean("save_to_gallery", false)
    fun setSaveToGallery(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("save_to_gallery", enabled).apply()
    }

    fun root(context: Context): File {
        val base = File(Environment.getExternalStorageDirectory(), "Android/media/${context.packageName}/PiMsg")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun mediaDir(context: Context): File = File(root(context), "Media").apply { mkdirs() }

    fun ensureFolders(context: Context) {
        listOf("Media", "Images", "Videos", "Audio", "Documents").forEach {
            val folder = File(root(context), it).apply { mkdirs() }
            File(folder, ".nomedia").takeIf { !it.exists() }?.createNewFile()
        }
    }

    fun isWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun shouldAutoDownload(context: Context): Boolean = when (downloadMode(context)) {
        DOWNLOAD_ALWAYS -> true
        DOWNLOAD_WIFI -> isWifi(context)
        else -> false
    }

    private fun folderForMime(context: Context, mime: String): File {
        val clean = mime.substringBefore(";")
        val folder = when {
            clean.startsWith("image/") -> "Images"
            clean.startsWith("video/") -> "Videos"
            clean.startsWith("audio/") -> "Audio"
            else -> "Documents"
        }
        return File(root(context), folder).apply {
            mkdirs()
            File(this, ".nomedia").takeIf { !it.exists() }?.createNewFile()
        }
    }

    fun findCached(context: Context, mediaId: String): File? =
        listOf("Media", "Images", "Videos", "Audio", "Documents")
            .asSequence()
            .flatMap { File(root(context), it).listFiles()?.asSequence() ?: emptySequence() }
            .firstOrNull { it.name.startsWith("${mediaId}_") && it.isFile }

    fun store(context: Context, mediaId: String, mime: String, displayName: String, bytes: ByteArray): File {
        val extension = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mime.substringBefore(";"))
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "ficheiro" }
        val file = File(folderForMime(context, mime), "${mediaId}_${safe}" + if (extension != null && !safe.endsWith(".$extension")) ".$extension" else "")
        if (!file.exists()) file.writeBytes(bytes)
        return file
    }

    fun wasSavedToGallery(context: Context, mediaId: String): Boolean =
        preferences(context).getStringSet("saved_gallery_ids", emptySet())?.contains(mediaId) == true

    fun markSavedToGallery(context: Context, mediaId: String) {
        val ids = preferences(context).getStringSet("saved_gallery_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(mediaId)
        preferences(context).edit().putStringSet("saved_gallery_ids", ids).apply()
    }

    fun saveToGallery(context: Context, file: File, mime: String, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val cleanMime = mime.substringBefore(";").ifBlank { "application/octet-stream" }
        val collection = when {
            cleanMime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            cleanMime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            cleanMime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val folder = when {
            cleanMime.startsWith("image/") -> Environment.DIRECTORY_PICTURES + "/PiMsg"
            cleanMime.startsWith("video/") -> Environment.DIRECTORY_MOVIES + "/PiMsg"
            cleanMime.startsWith("audio/") -> Environment.DIRECTORY_MUSIC + "/PiMsg"
            else -> Environment.DIRECTORY_DOWNLOADS + "/PiMsg"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName.ifBlank { file.name })
            put(MediaStore.MediaColumns.MIME_TYPE, cleanMime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { output -> FileInputStream(file).use { it.copyTo(output) } }
                ?: throw IllegalStateException("Não foi possível gravar o ficheiro.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
