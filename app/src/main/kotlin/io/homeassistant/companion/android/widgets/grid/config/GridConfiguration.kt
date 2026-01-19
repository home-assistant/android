package io.homeassistant.companion.android.widgets.grid.config

import android.os.Parcelable
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.parcelize.Parcelize

@Parcelize
data class GridConfiguration(
    val serverId: Int = ServerManager.SERVER_ID_ACTIVE,
    val label: String? = null,
    val items: List<GridItem> = emptyList(),
) : Parcelable

@Parcelize
data class GridItem(val label: String, val icon: String, val entityId: String = "", val id: Int = 0) : Parcelable
