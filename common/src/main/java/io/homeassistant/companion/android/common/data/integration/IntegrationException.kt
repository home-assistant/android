package io.homeassistant.companion.android.common.data.integration

class IntegrationException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}
