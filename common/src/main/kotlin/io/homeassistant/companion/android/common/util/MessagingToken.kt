package io.homeassistant.companion.android.common.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.serialization.Serializable

/**
 * Holds the messaging token, to enforce strong typing in the application.
 *
 * @property value The messaging token string.
 */
@Serializable
@JvmInline
value class MessagingToken(val value: String) {
    fun isBlank(): Boolean = value.isBlank()
}

/**
 * This extension is a copy of the one in the Kotlin stdlib for CharSequence but for [MessagingToken]
 */
@OptIn(ExperimentalContracts::class)
inline fun MessagingToken?.isNullOrBlank(): Boolean {
    contract {
        returns(false) implies (this@isNullOrBlank != null)
    }
    return this == null || isBlank()
}

fun interface MessagingTokenProvider {
    suspend operator fun invoke(): MessagingToken
}
