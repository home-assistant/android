package io.homeassistant.companion.android.settings.qs

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import io.homeassistant.companion.android.R as commonR
import kotlin.math.min

data class TileSlot(val id: String, val name: String)

/**
 * Loads the list of tile slots.
 * This list will never change and is based on the stored resources in the app.
 */
fun loadTileSlots(resources: Resources): List<TileSlot> {
    val tileIdArray = resources.getStringArray(commonR.array.tile_ids)
    val tileNameArray = resources.getStringArray(commonR.array.tile_name)
    return tileIdArray.zip(tileNameArray).map { (id, name) -> TileSlot(id, name) }
}

/**
 * Enables/disables services for tiles to ensure that only the required number of tiles + a few more
 * are available, instead of all possible tiles.
 */
fun updateActiveTileServices(highestInUse: Int, context: Context) {
    val tileIdArray = context.resources.getStringArray(commonR.array.tile_ids)
    val packageManager = context.packageManager
    val activeTilesRequired = min(tileIdArray.size, highestInUse + 4)
    ManageTilesViewModel.idToTileService.toList().forEachIndexed { index, (_, service) ->
        packageManager.setComponentEnabledSetting(
            ComponentName(context, service),
            if (index < activeTilesRequired) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
