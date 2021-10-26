package io.homeassistant.companion.android.tiles

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface TilesComponent {

    fun inject(favoriteEntitiesTile: FavoriteEntitiesTile)

    fun inject(tileActionActivity: TileActionActivity)
}