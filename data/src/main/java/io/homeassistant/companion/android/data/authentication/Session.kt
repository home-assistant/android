package io.homeassistant.companion.android.data.authentication

import org.threeten.bp.Instant

data class Session(
    val accessToken: String,
    val expiresTimestamp: Long,
    val refreshToken: String,
    val tokenType: String
) {

    fun isExpired() = expiresIn() < 0

    fun expiresIn() = expiresTimestamp - Instant.now().epochSecond
}
