package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity

interface HomeView {
    fun showHomeList(scenes: List<Entity<Any>>, scripts: List<Entity<Any>>, lights: List<Entity<Any>>, covers: List<Entity<Any>>)

    fun displayOnBoarding()
    fun displayMobileAppIntegration()
}
