package io.homeassistant.companion.android.utils

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.every
import io.mockk.mockk
import java.net.URL

internal val testHAVersion = HomeAssistantVersion(2025, 1, 1)

internal fun mockServer(
    url: String?,
    name: String,
    haVersion: HomeAssistantVersion? = testHAVersion,
    externalUrl: String = url ?: "",
    internalUrl: String? = null,
    cloudUrl: String? = null,
): Server {
    return mockk<Server> {
        every { version } returns haVersion
        every { friendlyName } returns name
        every { connection } returns mockk<ServerConnectionInfo> {
            every { getUrl(isInternal = false) } returns url?.let { URL(url) }
            every { this@mockk.externalUrl } returns externalUrl
            every { this@mockk.internalUrl } returns internalUrl
            every { this@mockk.cloudUrl } returns cloudUrl
        }
    }
}
