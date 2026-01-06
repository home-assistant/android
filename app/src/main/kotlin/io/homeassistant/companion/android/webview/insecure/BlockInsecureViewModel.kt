package io.homeassistant.companion.android.webview.insecure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Holds the security state to be displayed by [BlockInsecureFragment].
 */
data class BlockInsecureUiState(val missingHomeSetup: Boolean = true, val missingLocation: Boolean = true)

/**
 * ViewModel for [BlockInsecureFragment] that fetches the security state
 * from the server's connection state provider.
 */
@HiltViewModel(assistedFactory = BlockInsecureViewModelFactory::class)
class BlockInsecureViewModel @AssistedInject constructor(
    @Assisted private val serverId: Int,
    private val serverManager: ServerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockInsecureUiState())
    val uiState: StateFlow<BlockInsecureUiState> = _uiState.asStateFlow()

    init {
        loadSecurityState()
    }

    /**
     * Refreshes the security state from the server's connection state provider.
     * This should be called when the user taps the retry button.
     */
    fun refresh() {
        loadSecurityState()
    }

    private fun loadSecurityState() {
        viewModelScope.launch {
            try {
                val securityState = serverManager.connectionStateProvider(serverId).getSecurityState()
                _uiState.value = BlockInsecureUiState(
                    missingHomeSetup = !securityState.hasHomeSetup,
                    missingLocation = !securityState.locationEnabled,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load security state for server $serverId")
            }
        }
    }
}

@AssistedFactory
interface BlockInsecureViewModelFactory {
    fun create(serverId: Int): BlockInsecureViewModel
}
