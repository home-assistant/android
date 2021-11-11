package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity

interface HomePresenter {

    fun onViewReady()
    suspend fun onEntityClicked(entityId: String)
    fun onLogoutClicked()
    fun onFinish()
    suspend fun getEntities(): List<Entity<*>>
    suspend fun getWearHomeFavorites(): List<String>
    suspend fun setWearHomeFavorites(favorites: List<String>)
    suspend fun getTileShortcuts(): List<SimplifiedEntity>
    suspend fun setTileShortcuts(entities: List<SimplifiedEntity>)
}
