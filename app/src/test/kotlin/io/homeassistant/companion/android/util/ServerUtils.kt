package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.coEvery
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
    serverId: Int = 0,
    serverManager: ServerManager? = null,
): Server {
    val server = mockk<Server> {
        every { id } returns serverId
        every { version } returns haVersion
        every { friendlyName } returns name
        every { connection } returns mockk<ServerConnectionInfo> {
            every { this@mockk.externalUrl } returns externalUrl
            every { this@mockk.internalUrl } returns internalUrl
            every { this@mockk.cloudUrl } returns cloudUrl
        }
    }

    // If serverManager is provided, also mock the connectionStateProvider for this server
    serverManager?.let { sm ->
        coEvery { sm.connectionStateProvider(serverId) } returns mockk<ServerConnectionStateProvider> {
            coEvery { getExternalUrl() } returns url?.let { URL(it) }
        }
    }

    return server
}
