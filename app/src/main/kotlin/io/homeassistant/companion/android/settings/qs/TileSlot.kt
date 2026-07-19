package io.homeassistant.companion.android.settings.qs

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.qs.Tile10Service
import io.homeassistant.companion.android.qs.Tile11Service
import io.homeassistant.companion.android.qs.Tile12Service
import io.homeassistant.companion.android.qs.Tile13Service
import io.homeassistant.companion.android.qs.Tile14Service
import io.homeassistant.companion.android.qs.Tile15Service
import io.homeassistant.companion.android.qs.Tile16Service
import io.homeassistant.companion.android.qs.Tile17Service
import io.homeassistant.companion.android.qs.Tile18Service
import io.homeassistant.companion.android.qs.Tile19Service
import io.homeassistant.companion.android.qs.Tile1Service
import io.homeassistant.companion.android.qs.Tile20Service
import io.homeassistant.companion.android.qs.Tile21Service
import io.homeassistant.companion.android.qs.Tile22Service
import io.homeassistant.companion.android.qs.Tile23Service
import io.homeassistant.companion.android.qs.Tile24Service
import io.homeassistant.companion.android.qs.Tile25Service
import io.homeassistant.companion.android.qs.Tile26Service
import io.homeassistant.companion.android.qs.Tile27Service
import io.homeassistant.companion.android.qs.Tile28Service
import io.homeassistant.companion.android.qs.Tile29Service
import io.homeassistant.companion.android.qs.Tile2Service
import io.homeassistant.companion.android.qs.Tile30Service
import io.homeassistant.companion.android.qs.Tile31Service
import io.homeassistant.companion.android.qs.Tile32Service
import io.homeassistant.companion.android.qs.Tile33Service
import io.homeassistant.companion.android.qs.Tile34Service
import io.homeassistant.companion.android.qs.Tile35Service
import io.homeassistant.companion.android.qs.Tile36Service
import io.homeassistant.companion.android.qs.Tile37Service
import io.homeassistant.companion.android.qs.Tile38Service
import io.homeassistant.companion.android.qs.Tile39Service
import io.homeassistant.companion.android.qs.Tile3Service
import io.homeassistant.companion.android.qs.Tile40Service
import io.homeassistant.companion.android.qs.Tile4Service
import io.homeassistant.companion.android.qs.Tile5Service
import io.homeassistant.companion.android.qs.Tile6Service
import io.homeassistant.companion.android.qs.Tile7Service
import io.homeassistant.companion.android.qs.Tile8Service
import io.homeassistant.companion.android.qs.Tile9Service
import io.homeassistant.companion.android.qs.TileExtensions
import kotlin.math.min

/** Strongly typed identifier of a tile slot, stored as a raw string in the database and deeplinks. */
@JvmInline
internal value class TileId(val value: String) {
    override fun toString(): String = value
}

/** A quick settings tile slot supported by the app. */
internal data class TileSlot(val id: TileId, @StringRes val nameRes: Int, val serviceClass: Class<out TileExtensions>)

/**
 * The static list of tile slots supported by the app, in the order they are shown and enabled.
 */
@SuppressLint("InlinedApi", "NewApi")
internal val tileSlots: List<TileSlot> = listOf(
    TileSlot(
        id = Tile1Service.TILE_ID,
        nameRes = commonR.string.tile_1,
        serviceClass = Tile1Service::class.java,
    ),
    TileSlot(
        id = Tile2Service.TILE_ID,
        nameRes = commonR.string.tile_2,
        serviceClass = Tile2Service::class.java,
    ),
    TileSlot(
        id = Tile3Service.TILE_ID,
        nameRes = commonR.string.tile_3,
        serviceClass = Tile3Service::class.java,
    ),
    TileSlot(
        id = Tile4Service.TILE_ID,
        nameRes = commonR.string.tile_4,
        serviceClass = Tile4Service::class.java,
    ),
    TileSlot(
        id = Tile5Service.TILE_ID,
        nameRes = commonR.string.tile_5,
        serviceClass = Tile5Service::class.java,
    ),
    TileSlot(
        id = Tile6Service.TILE_ID,
        nameRes = commonR.string.tile_6,
        serviceClass = Tile6Service::class.java,
    ),
    TileSlot(
        id = Tile7Service.TILE_ID,
        nameRes = commonR.string.tile_7,
        serviceClass = Tile7Service::class.java,
    ),
    TileSlot(
        id = Tile8Service.TILE_ID,
        nameRes = commonR.string.tile_8,
        serviceClass = Tile8Service::class.java,
    ),
    TileSlot(
        id = Tile9Service.TILE_ID,
        nameRes = commonR.string.tile_9,
        serviceClass = Tile9Service::class.java,
    ),
    TileSlot(
        id = Tile10Service.TILE_ID,
        nameRes = commonR.string.tile_10,
        serviceClass = Tile10Service::class.java,
    ),
    TileSlot(
        id = Tile11Service.TILE_ID,
        nameRes = commonR.string.tile_11,
        serviceClass = Tile11Service::class.java,
    ),
    TileSlot(
        id = Tile12Service.TILE_ID,
        nameRes = commonR.string.tile_12,
        serviceClass = Tile12Service::class.java,
    ),
    TileSlot(
        id = Tile13Service.TILE_ID,
        nameRes = commonR.string.tile_13,
        serviceClass = Tile13Service::class.java,
    ),
    TileSlot(
        id = Tile14Service.TILE_ID,
        nameRes = commonR.string.tile_14,
        serviceClass = Tile14Service::class.java,
    ),
    TileSlot(
        id = Tile15Service.TILE_ID,
        nameRes = commonR.string.tile_15,
        serviceClass = Tile15Service::class.java,
    ),
    TileSlot(
        id = Tile16Service.TILE_ID,
        nameRes = commonR.string.tile_16,
        serviceClass = Tile16Service::class.java,
    ),
    TileSlot(
        id = Tile17Service.TILE_ID,
        nameRes = commonR.string.tile_17,
        serviceClass = Tile17Service::class.java,
    ),
    TileSlot(
        id = Tile18Service.TILE_ID,
        nameRes = commonR.string.tile_18,
        serviceClass = Tile18Service::class.java,
    ),
    TileSlot(
        id = Tile19Service.TILE_ID,
        nameRes = commonR.string.tile_19,
        serviceClass = Tile19Service::class.java,
    ),
    TileSlot(
        id = Tile20Service.TILE_ID,
        nameRes = commonR.string.tile_20,
        serviceClass = Tile20Service::class.java,
    ),
    TileSlot(
        id = Tile21Service.TILE_ID,
        nameRes = commonR.string.tile_21,
        serviceClass = Tile21Service::class.java,
    ),
    TileSlot(
        id = Tile22Service.TILE_ID,
        nameRes = commonR.string.tile_22,
        serviceClass = Tile22Service::class.java,
    ),
    TileSlot(
        id = Tile23Service.TILE_ID,
        nameRes = commonR.string.tile_23,
        serviceClass = Tile23Service::class.java,
    ),
    TileSlot(
        id = Tile24Service.TILE_ID,
        nameRes = commonR.string.tile_24,
        serviceClass = Tile24Service::class.java,
    ),
    TileSlot(
        id = Tile25Service.TILE_ID,
        nameRes = commonR.string.tile_25,
        serviceClass = Tile25Service::class.java,
    ),
    TileSlot(
        id = Tile26Service.TILE_ID,
        nameRes = commonR.string.tile_26,
        serviceClass = Tile26Service::class.java,
    ),
    TileSlot(
        id = Tile27Service.TILE_ID,
        nameRes = commonR.string.tile_27,
        serviceClass = Tile27Service::class.java,
    ),
    TileSlot(
        id = Tile28Service.TILE_ID,
        nameRes = commonR.string.tile_28,
        serviceClass = Tile28Service::class.java,
    ),
    TileSlot(
        id = Tile29Service.TILE_ID,
        nameRes = commonR.string.tile_29,
        serviceClass = Tile29Service::class.java,
    ),
    TileSlot(
        id = Tile30Service.TILE_ID,
        nameRes = commonR.string.tile_30,
        serviceClass = Tile30Service::class.java,
    ),
    TileSlot(
        id = Tile31Service.TILE_ID,
        nameRes = commonR.string.tile_31,
        serviceClass = Tile31Service::class.java,
    ),
    TileSlot(
        id = Tile32Service.TILE_ID,
        nameRes = commonR.string.tile_32,
        serviceClass = Tile32Service::class.java,
    ),
    TileSlot(
        id = Tile33Service.TILE_ID,
        nameRes = commonR.string.tile_33,
        serviceClass = Tile33Service::class.java,
    ),
    TileSlot(
        id = Tile34Service.TILE_ID,
        nameRes = commonR.string.tile_34,
        serviceClass = Tile34Service::class.java,
    ),
    TileSlot(
        id = Tile35Service.TILE_ID,
        nameRes = commonR.string.tile_35,
        serviceClass = Tile35Service::class.java,
    ),
    TileSlot(
        id = Tile36Service.TILE_ID,
        nameRes = commonR.string.tile_36,
        serviceClass = Tile36Service::class.java,
    ),
    TileSlot(
        id = Tile37Service.TILE_ID,
        nameRes = commonR.string.tile_37,
        serviceClass = Tile37Service::class.java,
    ),
    TileSlot(
        id = Tile38Service.TILE_ID,
        nameRes = commonR.string.tile_38,
        serviceClass = Tile38Service::class.java,
    ),
    TileSlot(
        id = Tile39Service.TILE_ID,
        nameRes = commonR.string.tile_39,
        serviceClass = Tile39Service::class.java,
    ),
    TileSlot(
        id = Tile40Service.TILE_ID,
        nameRes = commonR.string.tile_40,
        serviceClass = Tile40Service::class.java,
    ),
)

/**
 * Enables/disables services for tiles to ensure that only the required number of tiles + a few more
 * are available, instead of all possible tiles.
 */
internal fun updateActiveTileServices(highestInUse: Int, context: Context) {
    val packageManager = context.packageManager
    val activeTilesRequired = min(tileSlots.size, highestInUse + 4)
    tileSlots.forEachIndexed { index, slot ->
        packageManager.setComponentEnabledSetting(
            ComponentName(context, slot.serviceClass),
            if (index < activeTilesRequired) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
