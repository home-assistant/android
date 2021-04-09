package io.homeassistant.companion.android.qs

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface TileComponent {

    fun inject(tile1Service: Tile1Service)
    fun inject(tile2Service: Tile2Service)
    fun inject(tile3Service: Tile3Service)
    fun inject(tile4Service: Tile4Service)
    fun inject(tile5Service: Tile5Service)
}
