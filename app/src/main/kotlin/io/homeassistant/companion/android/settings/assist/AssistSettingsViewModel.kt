package io.homeassistant.companion.android.settings.assist

import android.annotation.SuppressLint
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Assist settings screen.
 */
data class AssistSettingsUiState(
    val isLoading: Boolean = true,
    val isDefaultAssistant: Boolean = false,
    val isWakeWordEnabled: Boolean = false,
    val selectedWakeWordModel: MicroWakeWordModelConfig? = null,
    val availableModels: List<MicroWakeWordModelConfig> = emptyList(),
    val isTestingWakeWord: Boolean = false,
    val wakeWordDetected: Boolean = false,
)

@VisibleForTesting
val WAKE_WORD_TEST_DEBOUNCE = 3.seconds

@HiltViewModel
class AssistSettingsViewModel @Inject constructor(
    private val defaultAssistantManager: DefaultAssistantManager,
    private val assistConfigManager: AssistConfigManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistSettingsUiState())
    val uiState: StateFlow<AssistSettingsUiState> = _uiState.asStateFlow()
    private var wakeWordResetJob: Job? = null

    init {
        loadState()
    }

    @SuppressLint("MissingPermission")
    private fun loadState() {
        viewModelScope.launch {
            val models = assistConfigManager.getAvailableModels()
            var isEnabled = assistConfigManager.isWakeWordEnabled()
            val selectedModel = assistConfigManager.getSelectedWakeWordModel() ?: models.firstOrNull()
            val isDefaultAssistant = defaultAssistantManager.isDefaultAssistant()

            if (!isDefaultAssistant && isEnabled) {
                // The assistant has changed and wake word cannot be enabled
                assistConfigManager.setWakeWordEnabled(false)
                isEnabled = false
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isDefaultAssistant = defaultAssistantManager.isDefaultAssistant(),
                    isWakeWordEnabled = isEnabled,
                    selectedWakeWordModel = selectedModel,
                    availableModels = models,
                )
            }
        }
    }

    /**
     * Refresh the default assistant status.
     * Call this when returning from system settings.
     */
    fun refreshDefaultAssistantStatus() {
        loadState()
    }

    /**
     * Returns the intent to set the app as the default assistant.
     *
     * Uses RoleManager on Android 10+ with fallback to system settings.
     */
    fun getSetDefaultAssistantIntent(): Intent {
        return defaultAssistantManager.getSetDefaultAssistantIntent()
    }

    /**
     * Toggle wake word detection on or off.
     */
    @SuppressLint("MissingPermission")
    fun onToggleWakeWord(enabled: Boolean) {
        viewModelScope.launch {
            assistConfigManager.setWakeWordEnabled(enabled)
            _uiState.update { it.copy(isWakeWordEnabled = enabled) }
        }
    }

    /**
     * Select a wake word model.
     */
    @SuppressLint("MissingPermission")
    fun onSelectWakeWordModel(model: MicroWakeWordModelConfig) {
        viewModelScope.launch {
            assistConfigManager.setSelectedWakeWordModel(model)
            _uiState.update { it.copy(selectedWakeWordModel = model) }
        }
    }

    /**
     * Notify the ViewModel that a wake word was detected by the service.
     *
     * Called by the Fragment when it receives the broadcast from the service.
     * Only updates the UI when test mode is active.
     * Detection feedback resets after a debounce period.
     */
    fun onWakeWordDetected() {
        if (!_uiState.value.isTestingWakeWord) return
        wakeWordResetJob?.cancel()
        _uiState.update { it.copy(wakeWordDetected = true) }
        wakeWordResetJob = viewModelScope.launch {
            delay(WAKE_WORD_TEST_DEBOUNCE)
            _uiState.update { it.copy(wakeWordDetected = false) }
        }
    }

    /**
     * Toggle the test mode UI state.
     *
     * Called by the Fragment when the user starts or stops testing.
     * The Fragment handles BroadcastReceiver registration.
     */
    fun setTestingWakeWord(testing: Boolean) {
        wakeWordResetJob?.cancel()
        _uiState.update { it.copy(isTestingWakeWord = testing, wakeWordDetected = false) }
    }
}
