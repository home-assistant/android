package io.homeassistant.companion.android.common.data.authentication.impl

data class Session(
    val accessToken: String,
    val expiresTimestamp: Long,
    val refreshToken: String,
    val tokenType: String
) {

    fun isExpired() = expiresIn() < 0

    fun expiresIn() = expiresTimestamp - System.currentTimeMillis() / 1000
}
