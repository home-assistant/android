package io.homeassistant.companion.android.qs

import android.os.Build
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class Tile1Service : TileExtensions() {

    override fun getTile(): Tile {
        return qsTile
    }

    override fun getTileId(): String {
        return TILE_ID
    }

    companion object {
        private const val TILE_ID = "tile_1"
    }
}
