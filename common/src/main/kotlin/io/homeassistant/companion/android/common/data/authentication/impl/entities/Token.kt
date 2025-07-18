package io.homeassistant.companion.android.common.data.authentication.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class Token(val accessToken: String, val expiresIn: Int, val refreshToken: String? = null, val tokenType: String)
