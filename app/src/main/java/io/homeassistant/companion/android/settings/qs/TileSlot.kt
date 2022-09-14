package io.homeassistant.companion.android.settings.qs

import android.content.res.Resources
import io.homeassistant.companion.android.R as commonR

data class TileSlot(
    val id: String,
    val name: String
)

/**
 * Loads the list of tile slots.
 * This list will never change and is based on the stored resources in the app.
 */
fun loadTileSlots(resources: Resources): List<TileSlot> {
    val tileIdArray = resources.getStringArray(commonR.array.tile_ids)
    val tileNameArray = resources.getStringArray(commonR.array.tile_name)
    return tileIdArray.zip(tileNameArray).map { (id, name) -> TileSlot(id, name) }
}
