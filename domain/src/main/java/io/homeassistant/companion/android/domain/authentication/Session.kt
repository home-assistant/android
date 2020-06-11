package io.homeassistant.companion.android.domain.authentication

import java.io.Serializable
import org.threeten.bp.Instant

data class Session(
    val accessToken: String,
    val expiresTimestamp: Long,
    val refreshToken: String,
    val tokenType: String
) : Serializable {

    fun isExpired() = expiresIn() < 0

    fun expiresIn() = expiresTimestamp - Instant.now().epochSecond
}
