package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import io.homeassistant.companion.android.database.server.Server

data class ShortcutEditorData(val servers: List<Server>, val serverDataById: Map<Int, ServerData>)
