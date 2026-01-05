package io.homeassistant.companion.android.common.data.integration

import okhttp3.ResponseBody

open class IntegrationException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(
        message: String,
        httpCode: Int,
        errorBody: ResponseBody?,
    ) : super("$message, httpCode: $httpCode, errorBody: ${errorBody?.string()}")
}

class NoUrlAvailableException :
    IntegrationException("No URL available at the moment, check networks and URL configuration")
