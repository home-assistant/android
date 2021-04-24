package io.homeassistant.companion.android.qs

import android.os.Build
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class Tile4Service : TileExtensions() {

    companion object {
        private const val TILE_ID = "tile_4"
    }

    override fun getTile(): Tile {
        return qsTile
    }

    override fun getTileId(): String {
        return TILE_ID
    }
}
