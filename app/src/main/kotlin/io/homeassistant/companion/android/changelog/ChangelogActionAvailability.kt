package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Inject

/**
 * Decides which changelog actions this device can perform, so an entry never links to a screen or setting the device
 * does not have. Actions whose target exists everywhere are always available.
 */
class ChangelogActionAvailability @Inject constructor(private val homeAssistantMode: HomeAssistantMode) {

    /** @return whether the target of [action] exists on this device */
    fun isAvailable(action: ChangelogAction): Boolean = when (action) {
        is ChangelogAction.OpenSettings -> isAvailable(action.deeplink)
        is ChangelogAction.OpenUrl, is ChangelogAction.OpenWidgetConfig, ChangelogAction.OpenAssist -> true
    }

    private fun isAvailable(deeplink: SettingsActivity.Deeplink): Boolean = when (deeplink) {
        // The setting is hidden on devices without Modes
        SettingsActivity.Deeplink.HomeAssistantMode -> homeAssistantMode.isSupported()
        else -> true
    }
}
