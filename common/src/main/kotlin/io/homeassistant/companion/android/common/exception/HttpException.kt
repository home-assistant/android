package io.homeassistant.companion.android.common.exception

data class HttpException(val code: Int, override val message: String?) : Exception(message)
