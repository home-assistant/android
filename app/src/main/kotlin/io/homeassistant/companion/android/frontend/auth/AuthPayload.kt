package io.homeassistant.companion.android.frontend.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthPayload(val callback: String = "", val force: Boolean = false)
