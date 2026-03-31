package io.homeassistant.companion.android.util

import android.os.Build

/**
 * Utility for detecting Meta Quest devices.
 */
object QuestUtil {
    /**
     * Checks if the current device is a Meta Quest device.
     *
     * @return `true` if the device is a Meta Quest, `false` otherwise
     */
    val isQuest: Boolean by lazy { Build.MODEL == "Quest" }
}
