package com.isabgroup.pimsg.home

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
)

data class UserItem(val id: String, val name: String, val ringColor: String?)

data class HomeState(
    val loading: Boolean = true,
    val tab: Tab = Tab.All,
    val chats: List<ChatItem> = emptyList(),
    val results: List<UserItem> = emptyList(),
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
                    )
                }
                _state.update { it.copy(loading = false, chats = list) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Não foi possível carregar as conversas.") }
            }
        }
    }

    // Abre (ou cria) a conversa direta com um utilizador encontrado na pesquisa.
    fun openDirect(user: UserItem, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = Api.post("/v1/chats/direct", JSONObject().put("user_id", user.id))
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
                    UserItem(o.getString("id"), o.getString("name"), o.str("ring_color"))
                }
                _state.update { it.copy(results = users) }
            } catch (e: Exception) {
                _state.update { it.copy(results = emptyList()) }
            }
        }
    }
}
