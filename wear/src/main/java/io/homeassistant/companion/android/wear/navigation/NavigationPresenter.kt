package io.homeassistant.companion.android.wear.navigation

interface NavigationPresenter {
    fun onViewReady()
    fun getPages(): List<NavigationItem>
    fun finish()
}