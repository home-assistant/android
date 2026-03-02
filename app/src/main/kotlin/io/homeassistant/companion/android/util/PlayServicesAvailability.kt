package io.homeassistant.companion.android.util

const val PLAY_SERVICES_FLAVOR_DOC_URL = "https://companion.home-assistant.io/docs/core/android-flavors/"

/**
 * Abstraction to check whether Google Play Services are expected but unavailable.
 *
 * On the full flavor this performs the real availability check; on the minimal
 * flavor it always returns `false` because Play Services are not required.
 */
internal fun interface PlayServicesAvailability {

    /**
     * Returns `true` when Google Play Services are required by the current
     * build flavor but are not available on the device.
     */
    fun isUnavailable(): Boolean
}
