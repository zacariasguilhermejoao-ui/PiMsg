package com.isabgroup.pimsg.chat

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import com.isabgroup.pimsg.ui.PiI18n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class Message(
    val id: String,
    val senderId: String,
    val kind: String,
    val body: String?,
    val mediaId: String?,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val pending: Boolean = false,
    val translatedBody: String? = null,
)

data class ChatState(
    val messages: List<Message> = emptyList(),
    val myId: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val typingName: String? = null,
)

private const val MAX_MEDIA = 50L * 1024 * 1024

private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

class ChatViewModel(context: Context, private val convId: String) : ViewModel() {
    private val appContext = context.applicationContext
    private val local = LocalChatStore(appContext)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    val images = mutableStateMapOf<String, Bitmap>()
    private var lastNewestId: String? = null
    private var syncing = false
    private val translationCache = mutableMapOf<String, String?>()
    private val translationInFlight = mutableSetOf<String>()

    init {
        // O histórico aparece imediatamente, mesmo sem rede.
        _state.update { it.copy(messages = local.read(convId), loading = false) }
    }

    suspend fun refresh() {
        // Sempre mantém a cópia local visível se a rede falhar.
        _state.update { it.copy(messages = local.read(convId)) }
        try {
            if (_state.value.myId == null) {
                val me = Api.get("/v1/me")
                _state.update { it.copy(myId = me.getString("id")) }
            }
            syncPending()
            val arr = Api.get("/v1/chats/$convId/messages?limit=100").getJSONArray("messages")
            val serverMessages = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Message(o.getString("id"), o.getString("sender_id"), o.getString("kind"),
                    o.str("body"), o.str("media_id"), o.getLong("created_at"),
                    if (o.isNull("expires_at")) null else o.optLong("expires_at"))
            }
            local.saveServer(convId, serverMessages)
            val combined = (serverMessages + local.pending(convId)).sortedByDescending { it.createdAt }
            val typingName = runCatching {
                val typing = Api.get("/v1/chats/$convId/typing").getJSONArray("typing")
                if (typing.length() > 0) typing.getJSONObject(0).optString("display_name").takeIf { it.isNotBlank() } else null
            }.getOrNull()
            _state.update { it.copy(messages = combined, loading = false, typingName = typingName) }
            val targetLanguage = PiI18n.language
            viewModelScope.launch {
                for (message in serverMessages.take(15)) {
                    if (message.senderId == _state.value.myId || message.kind != "text" || message.body.isNullOrBlank()) continue
                    val cacheKey = "${message.id}:$targetLanguage"
                    if (translationCache.containsKey(cacheKey) || !translationInFlight.add(cacheKey)) continue
                    try {
                        val response = Api.post("/v1/chats/$convId/messages/${message.id}/translate",
                            JSONObject().put("language", targetLanguage))
                        val translated = response.takeIf { it.optBoolean("translated") }
                            ?.optString("translation")?.takeIf { it.isNotBlank() }
                        translationCache[cacheKey] = translated
                        if (translated != null) {
                            _state.update { current ->
                                current.copy(messages = current.messages.map { item ->
                                    if (item.id == message.id) item.copy(translatedBody = translated) else item
                                })
                            }
                        }
                    } catch (_: Exception) {
                        // Uma falha de tradução não bloqueia a conversa; tenta novamente na próxima sincronização.
                    } finally {
                        translationInFlight.remove(cacheKey)
                    }
                }
            }
            val newest = serverMessages.firstOrNull()?.id
            if (newest != null && newest != lastNewestId) {
                lastNewestId = newest
                runCatching { Api.post("/v1/chats/$convId/read", JSONObject()) }
            }
        } catch (_: Exception) {
            _state.update { it.copy(messages = local.read(convId), loading = false) }
        }
    }

    private suspend fun syncPending() {
        if (syncing) return
        syncing = true
        try {
            val sender = _state.value.myId ?: return
            for (message in local.pending(convId).sortedBy { it.createdAt }) {
                try {
                    Api.post("/v1/chats/$convId/messages",
                        JSONObject().put("kind", "text").put("body", message.body.orEmpty()))
                    local.markSent(message.id)
                } catch (e: Exception) {
                    if (e is ApiException && e.reason == "limit_link_replies") {
                        local.clearPending(convId)
                        _state.update { state ->
                            state.copy(
                                messages = state.messages.filterNot { it.pending },
                                error = errorText(e, upload = false),
                            )
                        }
                        break
                    } else {
                        // Mantém na fila para tentar novamente na próxima sincronização.
                        break
                    }
                }
            }
        } finally {
            syncing = false
        }
    }

    fun setTyping(typing: Boolean) {
        viewModelScope.launch {
            runCatching {
                Api.post("/v1/chats/$convId/typing", JSONObject().put("typing", typing))
            }
        }
    }

    fun sendText(text: String) {
        setTyping(false)
        val body = text.trim()
        if (body.isEmpty()) return
        val message = Message(
            id = "local-" + java.util.UUID.randomUUID().toString(),
            senderId = _state.value.myId ?: "me",
            kind = "text",
            body = body,
            mediaId = null,
            createdAt = System.currentTimeMillis(),
            pending = true,
        )
        local.enqueue(convId, message)
        _state.update { it.copy(messages = (it.messages + message).sortedByDescending { m -> m.createdAt }, error = null) }
        viewModelScope.launch { refresh() }
    }

    // Os anexos são enviados quando há rede; mensagens de texto ficam em fila offline.
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
                Api.post("/v1/chats/$convId/messages", JSONObject().put("kind", kind)
                    .put("media_id", mediaId).put("body", name ?: ""))
                _state.update { it.copy(busy = false) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = errorText(e, upload = true)) }
            }
        }
    }

    suspend fun loadImage(mediaId: String) {
        if (images.containsKey(mediaId)) return
        try {
            val dir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "PiMsg/Images").apply { mkdirs() }
            val cached = File(dir, "$mediaId.img")
            val bytes = if (cached.exists()) cached.readBytes() else {
                Api.download("/v1/media/$mediaId").bytes.also { cached.writeBytes(it) }
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { images[mediaId] = it }
        } catch (_: Exception) {
            _state.update { it.copy(error = "Não foi possível abrir a foto. Se estiver offline, descarregue-a quando tiver Internet.") }
        }
    }

    fun showError(text: String) = _state.update { it.copy(error = text) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun errorText(e: Exception, upload: Boolean): String = when {
        e is ApiException && e.code == 402 && e.reason == "limit_link_replies" ->
            "Atingiu as 500 respostas mensais através dos links personalizados. Poderá responder novamente no dia 1, às 00h00 (hora de Angola), ou ativar o Pro."
        e is ApiException && e.code == 402 && e.reason == "limit_messages" ->
            "Limite diário de mensagens atingido. Volte amanhã, compre créditos ou ative o plano Pro."
        e is ApiException && e.code == 403 -> "Mensagem não enviada."
        e is ApiException && e.code == 413 -> "O ficheiro passa de 50 MB."
        upload -> "Não foi possível enviar o ficheiro. Verifique a ligação à Internet."
        else -> "Mensagem guardada no telefone; será enviada quando a ligação voltar."
    }
}
