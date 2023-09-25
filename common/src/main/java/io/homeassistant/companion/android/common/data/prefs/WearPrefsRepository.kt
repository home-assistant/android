package io.homeassistant.companion.android.common.data.prefs

interface WearPrefsRepository {
    suspend fun getAllTileShortcuts(): Map<Int?, List<String>>
    suspend fun getTileShortcutsAndSaveTileId(tileId: Int): List<String>
    suspend fun setTileShortcuts(tileId: Int?, entities: List<String>)
    suspend fun removeTileShortcuts(tileId: Int?): List<String>?
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
