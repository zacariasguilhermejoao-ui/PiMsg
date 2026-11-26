package com.isabgroup.pimsg.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

data class Message(
    val id: String,
    val senderId: String,
    val kind: String,
    val body: String?,
    val mediaId: String?,
    val createdAt: Long,
    val sourceLang: String?,
)

data class ChatState(
    val messages: List<Message> = emptyList(),
    val myId: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

private const val MAX_MEDIA = 50L * 1024 * 1024

private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

class ChatViewModel(private val convId: String) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Fotos so sao descarregadas quando o utilizador toca nelas.
    val images = mutableStateMapOf<String, Bitmap>()
    val translations = mutableStateMapOf<String, String>()
    private val translationRequested = mutableSetOf<String>()

    private var lastNewestId: String? = null

    suspend fun refresh() {
        try {
            if (_state.value.myId == null) {
                val me = Api.get("/v1/me")
                _state.update { it.copy(myId = me.getString("id")) }
            }
            val arr = Api.get("/v1/chats/$convId/messages?limit=60").getJSONArray("messages")
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Message(o.getString("id"), o.getString("sender_id"), o.getString("kind"), o.str("body"), o.str("media_id"), o.getLong("created_at"), o.str("source_lang"))
            }
            _state.update { it.copy(messages = list, loading = false) }
            translateIncoming(list)
            val newest = list.firstOrNull()?.id
            if (newest != null && newest != lastNewestId) {
                lastNewestId = newest
                runCatching { Api.post("/v1/chats/$convId/read", JSONObject()) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false) }
        }
    }

    private fun translateIncoming(messages: List<Message>) {
        val target = Locale.getDefault().language.lowercase(Locale.ROOT)
        if (target.length !in 2..3) return
        messages.asSequence()
            .filter { it.kind == "text" && it.senderId != _state.value.myId && !it.body.isNullOrBlank() }
            .filter { it.sourceLang != null && it.sourceLang != target && !translations.containsKey(it.id) }
            .filter { translationRequested.add(it.id) }
            .forEach { message ->
                viewModelScope.launch {
                    try {
                        val result = Api.translate(message.body.orEmpty(), message.sourceLang!!, target)
                        val translated = result.optString("translated_text")
                        if (translated.isNotBlank()) translations[message.id] = translated
                    } catch (e: Exception) {
                        if (e is ApiException && e.code == 402 && e.reason == "pro_required") {
                            _state.update { it.copy(error = "A tradução automática é exclusiva do plano Pro.") }
                        } else if (e is ApiException && e.code == 429 && e.reason == "translation_daily_limit") {
                            _state.update { it.copy(error = "Atingiste o limite diário de traduções do plano Pro.") }
                        }
                        // A mensagem original continua sempre disponível.
                    }
                }
            }
    }

    fun sendText(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            post(JSONObject().put("kind", "text").put("body", body).put("source_lang", Locale.getDefault().language.lowercase(Locale.ROOT)))
        }
    }

    // hint: "file" para qualquer ficheiro, "music" para audio escolhido em Musica
    fun sendMedia(cr: ContentResolver, uri: Uri, hint: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
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
                if (size > MAX_MEDIA) {
                    _state.update { it.copy(busy = false, error = "O ficheiro passa de 50 MB.") }
                    return@launch
                }
                val mediaId = Api.upload({ cr.openInputStream(uri) }, size, mime)
                val kind = when {
                    hint == "music" -> "music"
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> "file"
                }
                post(JSONObject().put("kind", kind).put("media_id", mediaId).put("body", name ?: ""))
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = errorText(e, upload = true)) }
            }
        }
    }

    private suspend fun post(body: JSONObject) {
        _state.update { it.copy(busy = true, error = null) }
        try {
            Api.post("/v1/chats/$convId/messages", body)
            _state.update { it.copy(busy = false) }
            refresh()
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = errorText(e, upload = false)) }
        }
    }

    suspend fun loadImage(mediaId: String) {
        if (images.containsKey(mediaId)) return
        try {
            val d = Api.download("/v1/media/$mediaId")
            BitmapFactory.decodeByteArray(d.bytes, 0, d.bytes.size)?.let { images[mediaId] = it }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Não foi possível abrir a foto.") }
        }
    }

    fun showError(text: String) = _state.update { it.copy(error = text) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun errorText(e: Exception, upload: Boolean): String = when {
        e is ApiException && e.code == 402 && e.reason == "limit_messages" ->
            "Limite diário de mensagens atingido. Volte amanhã, compre créditos ou ative o plano Pro."
        e is ApiException && e.code == 403 -> "Mensagem não enviada."
        e is ApiException && e.code == 413 -> "O ficheiro passa de 50 MB."
        upload -> "Não foi possível enviar o ficheiro."
        else -> "Não foi possível enviar a mensagem."
    }
}
