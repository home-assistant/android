package io.homeassistant.companion.android.domain

import java.net.MalformedURLException

class MalformedHttpUrlException : MalformedURLException {
    constructor() : super()
    constructor(message: String?) : super(message)
}
