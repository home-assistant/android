package io.homeassistant.companion.android.settings.assist

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.SuspendLazy
import javax.inject.Inject

/**
 * Manager for Assist settings and wake word model information.
 */
interface AssistConfigManager {
    /**
     * Returns a list of all available wake word models.
     */
    suspend fun getAvailableModels(): List<MicroWakeWordModelConfig>

    /**
     * Returns whether wake word detection is enabled.
     */
    suspend fun isWakeWordEnabled(): Boolean

    /**
     * Sets whether wake word detection is enabled.
     *
     * This also starts or stops the wake word detection service accordingly.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun setWakeWordEnabled(enabled: Boolean)

    /**
     * Returns the currently selected wake word model.
     *
     * Returns null if no model is selected or if the previously selected model
     * is no longer available.
     */
    suspend fun getSelectedWakeWordModel(): MicroWakeWordModelConfig?

    /**
     * Sets the selected wake word model.
     *
     * If wake word detection is enabled and the selection changed, restarts the service
     * to apply the new model.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun setSelectedWakeWordModel(model: MicroWakeWordModelConfig)
}

class AssistConfigManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: PrefsRepository,
) : AssistConfigManager {

    private val models = SuspendLazy { MicroWakeWordModelConfig.loadAvailableModels(context) }

    override suspend fun getAvailableModels(): List<MicroWakeWordModelConfig> = models.get()

    override suspend fun isWakeWordEnabled(): Boolean = prefsRepository.isWakeWordEnabled()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun setWakeWordEnabled(enabled: Boolean) {
        prefsRepository.setWakeWordEnabled(enabled)
        if (enabled) {
            AssistVoiceInteractionService.startListening(context)
        } else {
            AssistVoiceInteractionService.stopListening(context)
        }
    }

    override suspend fun getSelectedWakeWordModel(): MicroWakeWordModelConfig? {
        val wakeWordName = prefsRepository.getSelectedWakeWord() ?: return null
        return models.get().find { it.wakeWord == wakeWordName }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun setSelectedWakeWordModel(model: MicroWakeWordModelConfig) {
        val previousWakeWord = prefsRepository.getSelectedWakeWord()
        prefsRepository.setSelectedWakeWord(model.wakeWord)

        if (model.wakeWord != previousWakeWord && prefsRepository.isWakeWordEnabled()) {
            AssistVoiceInteractionService.startListening(context)
        }
    }
}
