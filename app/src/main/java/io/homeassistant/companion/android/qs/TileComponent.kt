package io.homeassistant.companion.android.qs

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface TileComponent {

    fun inject(tileExtensions: TileExtensions)
}
