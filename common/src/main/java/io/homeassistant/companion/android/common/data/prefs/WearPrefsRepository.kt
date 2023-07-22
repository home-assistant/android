package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.wear.tiles.ShortcutsTileId

interface WearPrefsRepository {
    suspend fun getAllTileShortcuts(): Map<ShortcutsTileId, List<String>>
    suspend fun getTileShortcuts(id: ShortcutsTileId): List<String>
    suspend fun setTileShortcuts(id: ShortcutsTileId, entities: List<String>)
    suspend fun getShowShortcutText(): Boolean
    suspend fun setShowShortcutTextEnabled(enabled: Boolean)
    suspend fun getTemplateTileContent(): String
    suspend fun setTemplateTileContent(content: String)
    suspend fun getTemplateTileRefreshInterval(): Int
    suspend fun setTemplateTileRefreshInterval(interval: Int)
    suspend fun getWearHapticFeedback(): Boolean
    suspend fun setWearHapticFeedback(enabled: Boolean)
    suspend fun getWearToastConfirmation(): Boolean
    suspend fun setWearToastConfirmation(enabled: Boolean)
    suspend fun getWearFavoritesOnly(): Boolean
    suspend fun setWearFavoritesOnly(enabled: Boolean)
}
