package io.homeassistant.companion.android.frontend.session

import kotlinx.serialization.Serializable

/**
 * Payload received from the Home Assistant frontend for authentication requests.
 *
 * @property callback JavaScript callback function name to invoke with the authentication result.
 * @property force When true, forces a token refresh even if the current token is still valid.
 */
@Serializable
data class AuthPayload(val callback: String = "", val force: Boolean = false)
