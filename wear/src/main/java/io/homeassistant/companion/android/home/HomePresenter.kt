package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity

interface HomePresenter {

    fun onViewReady()
    fun onEntityClicked(entity: Entity<Any>)
    fun onButtonClicked(id: String)
    fun onLogoutClicked()
    fun onFinish()
}
