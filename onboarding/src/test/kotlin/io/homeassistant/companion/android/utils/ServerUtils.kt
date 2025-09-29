package io.homeassistant.companion.android.utils

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.every
import io.mockk.mockk
import java.net.URL

internal val testHAVersion = HomeAssistantVersion(2025, 1, 1)

internal fun mockServer(url: String?, haVersion: HomeAssistantVersion? = testHAVersion, name: String): Server {
    return mockk<Server> {
        every { connection } returns mockk<ServerConnectionInfo> {
            every { getUrl(isInternal = false) } returns url?.let { URL(url) }
            every { version } returns haVersion
            every { friendlyName } returns name
        }
    }
}
