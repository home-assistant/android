package io.homeassistant.companion.android.settings.url

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExternalUrlViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ExternalUrlViewModel"
    }

    var canUseCloud by mutableStateOf(false)
        private set

    var useCloud by mutableStateOf(false)
        private set

    var externalUrl by mutableStateOf("")
        private set

    private var serverId = -1

    init {
        state.get<Int>(ExternalUrlFragment.EXTRA_SERVER)?.let { serverId = it }
        serverManager.getServer(serverId)?.let {
            canUseCloud = it.connection.canUseCloud()
            useCloud = it.connection.useCloud
            externalUrl = it.connection.getUrl(isInternal = false, force = true).toString()
        }
    }

    fun toggleCloud(use: Boolean) {
        viewModelScope.launch {
            useCloud = use && canUseCloud
            serverManager.getServer(serverId)?.let {
                serverManager.updateServer(
                    it.copy(
                        connection = it.connection.copy(
                            useCloud = useCloud
                        )
                    )
                )
            }
        }
    }

    fun updateExternalUrl(url: String) {
        viewModelScope.launch {
            serverManager.getServer(serverId)?.let {
                try {
                    val formatted = UrlUtil.formattedUrlString(url)
                    serverManager.updateServer(
                        it.copy(
                            connection = it.connection.copy(
                                externalUrl = formatted
                            )
                        )
                    )
                    externalUrl = formatted
                } catch (e: MalformedHttpUrlException) {
                    Log.e(TAG, "Invalid external URL, ignoring", e)
                }
            }
        }
    }
}
