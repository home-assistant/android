package io.homeassistant.companion.android.qs

import android.os.Build
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.settings.qs.TileId

@RequiresApi(Build.VERSION_CODES.N)
internal class Tile32Service : TileExtensions() {

    override val tileId: TileId = TILE_ID

    override fun getTile(): Tile? {
        return if (qsTile != null) {
            qsTile
        } else {
            null
        }
    }

    companion object {
        val TILE_ID = TileId("tile_32")
    }
}
