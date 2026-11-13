package io.homeassistant.companion.android.settings.url

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ExtraUrlsViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    application: Application,
) : AndroidViewModel(application) {

    var urls by mutableStateOf<List<String>>(emptyList())
        private set

    /** Error of the last add attempt, cleared once the input changes. */
    var error by mutableStateOf<ExtraUrlError?>(null)
        private set

    private var serverId = -1

    init {
        state.get<Int>(ExtraUrlsFragment.EXTRA_SERVER)?.let { serverId = it }
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let { urls = it.connection.extraUrls }
        }
    }

    fun clearError() {
        error = null
    }

    /** @return `true` if [input] was added, so the caller can clear its text field. */
    suspend fun addUrl(input: String): Boolean {
        val server = serverManager.getServer(serverId) ?: return false
        val connection = server.connection
        val known = listOfNotNull(connection.externalUrl, connection.internalUrl)
        return when (val result = ExtraUrlsList.add(urls, input, known)) {
            is AddExtraUrlResult.Added -> {
                save(result.urls)
                error = null
                true
            }
            AddExtraUrlResult.Invalid -> {
                error = ExtraUrlError.Invalid
                false
            }
            AddExtraUrlResult.Duplicate -> {
                error = ExtraUrlError.Duplicate
                false
            }
        }
    }

    fun removeUrl(index: Int) = viewModelScope.launch { save(ExtraUrlsList.remove(urls, index)) }

    fun moveUrl(index: Int, offset: Int) = viewModelScope.launch { save(ExtraUrlsList.move(urls, index, offset)) }

    private suspend fun save(newUrls: List<String>) {
        serverManager.getServer(serverId)?.let {
            serverManager.updateServer(it.copy(connection = it.connection.copy(extraUrls = newUrls)))
            urls = newUrls
        }
    }
}

enum class ExtraUrlError { Invalid, Duplicate }
