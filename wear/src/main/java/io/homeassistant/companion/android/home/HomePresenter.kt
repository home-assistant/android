package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity

interface HomePresenter {

    fun onViewReady()
    fun onEntityClicked(entity: Entity<Any>)
    fun onLogoutClicked()
    fun onFinish()
    suspend fun getEntities(): Array<Entity<Any>>
}
