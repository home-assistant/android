package io.homeassistant.companion.android.qs

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.runBlocking

@RequiresApi(Build.VERSION_CODES.N)
class TileExtensions {

    companion object {
        private const val TAG = "TileExtensions"

        @RequiresApi(Build.VERSION_CODES.N)
        fun setTileData(context: Context, tileId: String, tile: Tile): Boolean {
            val tileDao = AppDatabase.getInstance(context).tileDao()
            val tileData = tileDao.get(tileId)
            return if (tileData != null) {
                tile.label = tileData.label
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = tileData.subtitle
                }
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
                true
            } else {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.updateTile()
                false
            }
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun tileClicked(context: Context, tileId: String, tile: Tile, integrationUseCase: IntegrationRepository) {

            val tileDao = AppDatabase.getInstance(context).tileDao()
            val tileData = tileDao.get(tileId)
            val hasTile = setTileData(context, tileId, tile)
            if (hasTile) {
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
                val tileService = tileData?.entityId?.split(".")
                runBlocking {
                    try {
                        integrationUseCase.callService(tileService!![0], tileService[1], hashMapOf())
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to call service", e)
                        Toast.makeText(context, R.string.service_call_failure, Toast.LENGTH_SHORT).show()
                    }
                }
                tile.state = Tile.STATE_INACTIVE
                tile.updateTile()
            } else {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.updateTile()
                Toast.makeText(context, R.string.tile_data_missing, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
