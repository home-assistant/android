package io.homeassistant.companion.android.widgets.grid.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GridConfiguration(
    val serverId: Int? = null,
    val label: String? = null,
    val requireAuthentication: Boolean = false,
    val items: List<GridItem> = emptyList()
) : Parcelable

@Parcelize
data class GridItem(
    val label: String = "",
    val icon: String = "",
    val domain: String = "",
    val service: String = "",
    val entityId: String = "",
    val id: Int = 0
) : Parcelable
