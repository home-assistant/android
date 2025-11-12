package io.homeassistant.companion.android.settings

import androidx.fragment.app.Fragment

/**
 * Placeholder that doesn't do anything but to allow the app to build in release.
 */
class ConnectionSecurityLevelFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "connection_security_level_result"
        const val EXTRA_SERVER = "server_id"
    }
}
