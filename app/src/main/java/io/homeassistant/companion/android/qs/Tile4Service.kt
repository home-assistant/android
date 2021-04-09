package io.homeassistant.companion.android.qs

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
class Tile4Service : TileService() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository
    companion object {
        private const val TILE_ID = "tile_4"
    }
    override fun onClick() {
        super.onClick()
        TileExtensions.setTileData(applicationContext, TILE_ID, qsTile)
        TileExtensions.tileClicked(applicationContext, TILE_ID, qsTile, integrationUseCase)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        TileExtensions.setTileData(applicationContext, TILE_ID, qsTile)
    }

    override fun onStartListening() {
        super.onStartListening()
        TileExtensions.setTileData(applicationContext, TILE_ID, qsTile)
    }
}
