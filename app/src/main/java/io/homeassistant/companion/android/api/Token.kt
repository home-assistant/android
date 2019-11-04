package io.homeassistant.companion.android.api

import java.util.*


data class Token(
    val accessToken: String,
    val expiresTimestamp: Long,
    val refreshToken: String,
    val tokenType: String
) {
    fun isExpired() = expiresIn() < 0

    fun expiresIn() = expiresTimestamp - Calendar.getInstance().timeInMillis / 1000
}