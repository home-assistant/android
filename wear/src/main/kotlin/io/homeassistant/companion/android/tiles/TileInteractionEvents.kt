package io.homeassistant.companion.android.tiles

import android.os.Build
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.TileService
import io.homeassistant.companion.android.common.util.SdkVersion
import timber.log.Timber

/** Refresh interval value for updating the tile when it becomes visible. */
internal const val REFRESH_INTERVAL_ON_VIEWED = 1

/**
 * Whether refreshing a tile when it becomes visible works on this device: from Wear OS 6 enter
 * events are batched and can arrive after the user left the tile.
 */
internal fun isRefreshOnViewedSupported(): Boolean = !SdkVersion.isAtLeast(Build.VERSION_CODES.BAKLAVA)

/**
 * Requests an update of this [TileService]'s tiles when [events] contains an enter event for a
 * tile that [wantsUpdateOnEnter].
 *
 * From Wear OS 6 events are batched and can arrive after the user left the tile, so the freshness
 * interval of the tile remains the primary refresh mechanism.
 */
internal suspend fun TileService.requestUpdateOnEnter(
    events: List<EventBuilders.TileInteractionEvent>,
    wantsUpdateOnEnter: suspend (tileId: Int) -> Boolean,
) {
    val entered = events.filter { it.eventType == EventBuilders.TileInteractionEvent.ENTER }
    if (entered.any { wantsUpdateOnEnter(it.tileId) }) {
        try {
            TileService.getUpdater(this).requestUpdate(this::class.java)
        } catch (e: Exception) {
            Timber.w(e, "Unable to request tile update on enter")
        }
    }
}
