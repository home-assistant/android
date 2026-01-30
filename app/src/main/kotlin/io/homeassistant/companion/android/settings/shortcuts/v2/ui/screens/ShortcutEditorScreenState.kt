package io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens

import androidx.compose.runtime.Immutable
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
data class ShortcutEditorScreenState(
    val isLoading: Boolean,
    val error: ShortcutRepositoryError? = null,
    val servers: ImmutableList<Server> = persistentListOf(),
    val entities: ImmutableMap<Int, ImmutableList<Entity>> = persistentMapOf(),
    val entityRegistry: ImmutableMap<Int, ImmutableList<EntityRegistryResponse>> = persistentMapOf(),
    val deviceRegistry: ImmutableMap<Int, ImmutableList<DeviceRegistryResponse>> = persistentMapOf(),
    val areaRegistry: ImmutableMap<Int, ImmutableList<AreaRegistryResponse>> = persistentMapOf(),
)
