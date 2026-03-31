package io.homeassistant.companion.android.common.util

import java.net.InetAddress
import okio.ByteString.Companion.decodeHex

object WearDataMessages {
    const val PATH_LOGIN_RESULT = "/loginResult"

    const val KEY_ID = "id"
    const val KEY_SUCCESS = "success"
    const val KEY_UPDATE_TIME = "UpdateTime"

    const val CONFIG_IS_AUTHENTICATED = "isAuthenticated"
    const val CONFIG_SERVER_ID = "serverId"
    const val CONFIG_SERVER_EXTERNAL_URL = "serverExternalUrl"
    const val CONFIG_SERVER_WEBHOOK_ID = "serverWebhookId"
    const val CONFIG_SERVER_CLOUD_URL = "serverCloudUrl"
    const val CONFIG_SERVER_CLOUDHOOK_URL = "serverCloudhookUrl"
    const val CONFIG_SERVER_USE_CLOUD = "serverUseCloud"
    const val CONFIG_SERVER_REFRESH_TOKEN = "serverRefreshToken"
    const val CONFIG_SUPPORTED_DOMAINS = "supportedDomains"
    const val CONFIG_FAVORITES = "favorites"
    const val CONFIG_TEMPLATE_TILES = "templateTiles"

    const val LOGIN_RESULT_EXCEPTION = "exception"

    object DnsLookup {
        const val CAPABILITY_DNS_VIA_MOBILE = "mobile_network_helper"
        const val PATH_DNS_LOOKUP = "/network/dnsLookup"

        fun List<InetAddress>.encodeDNSResult(): ByteArray = joinToString(",") {
            it.address.toHexString()
        }.encodeToByteArray()

        fun ByteArray.decodeDNSResult(hostname: String): List<InetAddress> = decodeToString().split(",").map {
            InetAddress.getByAddress(hostname, it.decodeHex().toByteArray())
        }

        fun String.encodeDNSRequest(): ByteArray = toByteArray()

        fun ByteArray.decodeDNSRequest(): String = decodeToString()
    }
}
