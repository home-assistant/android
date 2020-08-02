package io.homeassistant.companion.android.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URL

object UrlHandler {
    fun handle(base: URL?, input: String): URL? {
        return if (isAbsoluteUrl(input)) {
            URL(input)
        } else {
            base?.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments(input.trimStart('/'))
                ?.build()
                ?.toUrl()
        }
    }

    fun isAbsoluteUrl(it: String?): Boolean {
        return Regex("^https?://").containsMatchIn(it.toString())
    }

    fun isHomeAssistantUrl(it: String?): Boolean {
        return Regex("^homeassistant://").containsMatchIn(it.toString())
    }

    fun getUniversalLink(it: String?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/nfc/\\?url=(.*)").find(it.toString())
        return matches?.groups?.get(1)?.value
    }

    data class CallServiceLink(val domain: String?, val service: String?, val entity: String?)
    fun splitCallServiceLink(it: String?): CallServiceLink {
        val matches = Regex("^homeassistant://call_service/(.*?)\\.(.*?)\\?entity_id=(.*)").find(it.toString())
        val domain  = matches?.groups?.get(1)?.value
        val service = matches?.groups?.get(2)?.value
        val entity  = matches?.groups?.get(3)?.value
        return CallServiceLink(domain, service, entity)
    }

    data class FireEventLink(val event: String?, val entity: String?)

    fun splitFireEventLink(it: String?): FireEventLink {
        val matches = Regex("^homeassistant://fire_event/(.*?)?entity_id=(.*)").find(it.toString())
        val event = matches?.groups?.get(1)?.value
        val entity = matches?.groups?.get(2)?.value
        return FireEventLink(event, entity)
    }

    fun splitNfcTagId(it: String?): String? {
        val matches =
            Regex("^https?://www\\.home-assistant\\.io/nfc/([0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12})").find(
                it.toString()
            )
        return matches?.groups?.get(1)?.value
    }
}
