package io.homeassistant.companion.android.settings.url

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.MalformedHttpUrlException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.UrlUtil
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExternalUrlViewModel @Inject constructor(
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

    init {
        serverManager.getServer()?.let {
            canUseCloud = it.connection.canUseCloud()
            useCloud = it.connection.useCloud
            externalUrl = it.connection.getUrl(isInternal = false, force = true).toString()
        }
    }

    fun toggleCloud(use: Boolean) {
        viewModelScope.launch {
            useCloud = use && canUseCloud
            serverManager.getServer()?.let {
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
            serverManager.getServer()?.let {
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
