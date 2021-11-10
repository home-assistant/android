package io.homeassistant.companion.android.tiles

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface TilesComponent {

    fun inject(shortcutsTile: ShortcutsTile)

    fun inject(tileActionActivity: TileActionActivity)
}
