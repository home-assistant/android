package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import io.homeassistant.companion.android.database.server.Server

sealed interface ServersResult {
    data class Success(val servers: List<Server>, val defaultServerId: Int) : ServersResult
    data object NoServers : ServersResult
}
