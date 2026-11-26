package com.isabgroup.pimsg.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import com.isabgroup.pimsg.data.uploadUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class Tab(val label: String, val key: String) {
    All("Bate Papo", "all"),
    New("Novas", "new"),
    Favorites("Favoritos", "favorites"),
    Groups("Grupos", "groups"),
}

data class ChatItem(
    val id: String,
    val title: String,
    val preview: String,
    val unread: Int,
    val isGroup: Boolean,
    val ringColor: String?,
    val hasStory: Boolean = false,
    val otherId: String? = null,
    val inviteCode: String? = null,
)

data class UserItem(
    val id: String,
    val name: String,
    val ringColor: String?,
    val linkId: String? = null,
    val requestCode: String? = null,
    val linkSubject: String? = null,
)
data class GroupItem(val id: String, val name: String, val description: String?)

data class StoryItem(
    val id: String,
    val userId: String,
    val userName: String,
    val mediaId: String,
    val caption: String?,
    val createdAt: Long,
    val mime: String?,
    val viewed: Boolean,
)

data class HomeState(
    val loading: Boolean = true,
    val tab: Tab = Tab.All,
    val chats: List<ChatItem> = emptyList(),
    val results: List<UserItem> = emptyList(),
    val groupResults: List<GroupItem> = emptyList(),
    val myId: String? = null,
    val stories: Map<String, List<StoryItem>> = emptyMap(),
    val error: String? = null,
    val notice: String? = null,
)

private fun JSONObject.str(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

private fun previewOf(kind: String?, body: String?): String = when (kind) {
    null -> "Nova conversa"
    "text" -> body.orEmpty()
    "image" -> "Foto"
    "video" -> "Vídeo"
    "audio" -> "Áudio"
    "music" -> "Música"
    "file" -> "Ficheiro"
    else -> body.orEmpty()
}

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
        load()
    }

    fun load() {
        val tab = _state.value.tab
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val json = Api.get("/v1/chats?tab=${tab.key}")
                val arr = json.getJSONArray("chats")
                val list = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val group = o.getString("type") == "group"
                    ChatItem(
                        id = o.getString("id"),
                        title = (if (group) o.str("name") else o.str("other_name")) ?: "Conversa",
                        preview = previewOf(o.str("last_kind"), o.str("last_body")),
                        unread = o.optInt("unread", 0),
                        isGroup = group,
                        ringColor = o.str("other_ring"),
                        otherId = o.str("other_id"),
                        inviteCode = o.str("invite_code"),
                    )
                }
                _state.update { it.copy(loading = false, chats = list) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Não foi possível carregar as conversas.") }
            }
            loadStories()
        }
    }

    private suspend fun loadStories() {
        try {
            val me = Api.get("/v1/me").getString("id")
            val array = Api.get("/v1/stories").getJSONArray("stories")
            val items = (0 until array.length()).map { index ->
                val story = array.getJSONObject(index)
                StoryItem(
                    id = story.getString("id"),
                    userId = story.getString("user_id"),
                    userName = story.optString("user_name", "PiMsg"),
                    mediaId = story.getString("media_id"),
                    caption = story.str("caption"),
                    createdAt = story.optLong("created_at"),
                    mime = story.str("mime"),
                    viewed = story.optBoolean("viewed"),
                )
            }
            _state.update { it.copy(myId = me, stories = items.groupBy { story -> story.userId }) }
        } catch (_: Exception) {
            // A lista de conversas continua utilizável se os stories estiverem indisponíveis.
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun postStory(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(notice = null) }
            try {
                val uploaded = uploadUri(contentResolver, uri)
                if (!uploaded.mime.startsWith("image/") && !uploaded.mime.startsWith("video/")) {
                    throw ApiException(400, "story exige imagem ou vídeo")
                }
                Api.post("/v1/stories", JSONObject().put("media_id", uploaded.id))
                loadStories()
                _state.update { it.copy(notice = "Story publicado.") }
            } catch (_: Exception) {
                _state.update { it.copy(notice = "Não foi possível publicar o story. Verifique o ficheiro e tente novamente.") }
            }
        }
    }

    // Abre (ou cria) a conversa direta com um utilizador encontrado na pesquisa.
    fun openDirect(user: UserItem, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val body = JSONObject().put("user_id", user.id)
                user.linkId?.let { body.put("link_id", it) }
                user.requestCode?.let { body.put("request_code", it) }
                val json = Api.post("/v1/chats/direct", body)
                _state.update { it.copy(notice = null) }
                onOpen(json.getString("id"))
            } catch (e: ApiException) {
                val msg = when {
                    e.code == 402 && e.reason == "limit_contacts" ->
                        "Limite de 30 conversas do plano Free atingido. Ative o plano Pro para falar com mais pessoas."
                    e.code == 403 -> "Não é possível conversar com este utilizador."
                    else -> "Não foi possível abrir a conversa."
                }
                _state.update { it.copy(notice = msg) }
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Não foi possível abrir a conversa.") }
            }
        }
    }

    fun openRequestCode(code: String, onOpen: (String, UserItem) -> Unit) {
        viewModelScope.launch {
            try {
                val result = Api.get("/v1/links/requests/${Api.encode(code.uppercase())}")
                val o = result.getJSONObject("user")
                val user = UserItem(
                    o.getString("id"), o.getString("name"), o.str("ring_color"),
                    result.getString("link_id"), result.getString("request_code"), result.str("subject"),
                )
                openDirect(user) { id -> onOpen(id, user) }
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Não foi possível recuperar o destino deste link.") }
            }
        }
    }

    fun openContactSlug(slug: String, onOpen: (String, UserItem) -> Unit) {
        viewModelScope.launch {
            try {
                val result = Api.post("/v1/links/${Api.encode(slug)}/open", JSONObject())
                val u = result.getJSONObject("user")
                val user = UserItem(
                    u.getString("id"), u.getString("name"), null,
                    result.getString("link_id"), result.getString("request_code"), result.str("subject"),
                )
                openDirect(user) { id -> onOpen(id, user) }
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Não foi possível abrir este link. Pode estar expirado ou indisponível.") }
            }
        }
    }

    fun joinGroup(code: String, seeHistory: Boolean, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = Api.post(
                    "/v1/groups/join",
                    JSONObject().put("code", code).put("see_history", seeHistory),
                )
                onJoined(result.getString("id"))
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Não foi possível entrar neste grupo. Verifique o convite.") }
            }
        }
    }

    fun joinGroupCode(code: String, onOpen: (String, String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val preview = Api.get("/v1/groups/preview/${Api.encode(code)}")
                val result = Api.post(
                    "/v1/groups/join",
                    JSONObject().put("code", code).put("see_history", false),
                )
                onOpen(result.getString("id"), preview.getString("name"), code.uppercase())
            } catch (e: Exception) {
                _state.update { it.copy(notice = "O convite do grupo expirou ou já não está disponível.") }
            }
        }
    }

    fun createGroup(name: String, onCreated: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = Api.post("/v1/groups", JSONObject().put("name", name))
                onCreated(result.getString("id"), result.getString("invite_code"))
            } catch (e: Exception) {
                _state.update { it.copy(notice = "Não foi possível criar o grupo.") }
            }
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        viewModelScope.launch {
            try {
                val json = Api.get("/v1/search?q=${Api.encode(q)}")
                val arr = json.getJSONArray("users")
                val users = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    UserItem(
                        o.getString("id"), o.getString("name"), o.str("ring_color"),
                        o.str("link_id"), o.str("request_code"), o.str("link_subject"),
                    )
                }
                val groupsJson = json.optJSONArray("groups")
                val groups = if (groupsJson == null) emptyList() else (0 until groupsJson.length()).map { i ->
                    val g = groupsJson.getJSONObject(i)
                    GroupItem(g.getString("id"), g.getString("name"), g.str("description"))
                }
                _state.update { it.copy(results = users, groupResults = groups) }
            } catch (e: Exception) {
                _state.update { it.copy(results = emptyList()) }
            }
        }
    }
}
