package io.homeassistant.companion.android.settings.shortcuts.v2.views.screens

import androidx.compose.runtime.Immutable
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server

@Immutable
data class ShortcutEditorScreenState(
    val isLoading: Boolean,
    val isSaving: Boolean = false,
    val error: ShortcutError? = null,
    val servers: List<Server> = emptyList(),
    val entities: Map<Int, List<Entity>> = emptyMap(),
    val entityRegistry: Map<Int, List<EntityRegistryResponse>> = emptyMap(),
    val deviceRegistry: Map<Int, List<DeviceRegistryResponse>> = emptyMap(),
    val areaRegistry: Map<Int, List<AreaRegistryResponse>> = emptyMap(),
)
