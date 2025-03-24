package io.homeassistant.companion.android.qs

import android.os.Build
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class Tile2Service : TileExtensions() {

    override fun getTile(): Tile? {
        return if (qsTile != null) {
            qsTile
        } else {
            null
        }
    }

    override fun getTileId(): String {
        return TILE_ID
    }

    companion object {
        const val TILE_ID = "tile_2"
    }
}
