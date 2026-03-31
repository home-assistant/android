package io.homeassistant.companion.android.common.data.authentication

import okhttp3.ResponseBody

class AuthorizationException : Exception {
    constructor() : super()
    constructor(
        message: String,
        httpCode: Int,
        errorBody: ResponseBody?,
    ) : super("$message, httpCode: $httpCode, errorBody: ${errorBody?.string()}")
}
