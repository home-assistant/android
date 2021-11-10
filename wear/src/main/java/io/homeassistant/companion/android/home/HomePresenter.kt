package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity

interface HomePresenter {

    fun onViewReady()
    fun onEntityClicked(entityId: String)
    fun onLogoutClicked()
    fun onFinish()
    suspend fun getEntities(): List<Entity<Any>>
    suspend fun getWearHomeFavorites(): Set<String>
    suspend fun setWearHomeFavorites(favorites: Set<String>)
}
