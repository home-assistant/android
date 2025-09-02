package io.homeassistant.companion.android.common.util

/**
 * Holds the messaging token, to enforce strong typing in the application.
 *
 * @property value The messaging token string.
 */
@JvmInline
value class MessagingToken(val value: String) {
    fun isBlank(): Boolean = value.isBlank()
}

fun interface MessagingTokenProvider {
    suspend operator fun invoke(): MessagingToken
}
