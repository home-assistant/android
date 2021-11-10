package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity

interface HomePresenter {

    fun onViewReady()
    suspend fun onEntityClicked(entityId: String)
    fun onLogoutClicked()
    fun onFinish()
    suspend fun getEntities(): List<Entity<*>>
    suspend fun getWearHomeFavorites(): List<String>
    suspend fun setWearHomeFavorites(favorites: List<String>)
}
