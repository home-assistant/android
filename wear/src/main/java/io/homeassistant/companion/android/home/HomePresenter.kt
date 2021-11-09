package io.homeassistant.companion.android.home

import android.content.Context
import io.homeassistant.companion.android.common.data.integration.Entity

interface HomePresenter {

    fun onViewReady()
    fun onEntityClicked(entityId: String)
    fun onLogoutClicked()
    fun onFinish()
    suspend fun getEntities(): Array<Entity<Any>>
    suspend fun getWearHomeFavorites(): Set<String>
    suspend fun setWearHomeFavorites(favorites: Set<String>)
    suspend fun getTileShortcuts(): List<String>
    suspend fun setTileShortcuts(entities: List<String>, context: Context)
}
