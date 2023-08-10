package io.homeassistant.companion.android.common.data.prefs

interface WearPrefsRepository {
    suspend fun getAllTileShortcuts(): Map<Int?, List<String>>
    suspend fun getTileShortcutsAndSaveTileId(tileId: Int): List<String>
    suspend fun setTileShortcuts(tileId: Int?, entities: List<String>)
    suspend fun removeTileShortcuts(tileId: Int?): List<String>?
    suspend fun getShowShortcutText(): Boolean
    suspend fun setShowShortcutTextEnabled(enabled: Boolean)
    suspend fun getAllTemplateTiles(): Map<Int?, Pair<String, Int>>
    suspend fun getTemplateTile(tileId: Int?): Pair<String, Int>?
    suspend fun setTemplateTile(tileId: Int?, content: String, refreshInterval: Int)
    suspend fun removeTemplateTile(tileId: Int?): Pair<String, Int>?
    suspend fun getWearHapticFeedback(): Boolean
    suspend fun setWearHapticFeedback(enabled: Boolean)
    suspend fun getWearToastConfirmation(): Boolean
    suspend fun setWearToastConfirmation(enabled: Boolean)
    suspend fun getWearFavoritesOnly(): Boolean
    suspend fun setWearFavoritesOnly(enabled: Boolean)
}
