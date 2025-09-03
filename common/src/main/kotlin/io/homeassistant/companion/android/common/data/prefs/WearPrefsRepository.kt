package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig

interface WearPrefsRepository {
    suspend fun getAllTileShortcuts(): Map<Int?, List<String>>
    suspend fun getTileShortcutsAndSaveTileId(tileId: Int): List<String>
    suspend fun setTileShortcuts(tileId: Int?, entities: List<String>)
    suspend fun removeTileShortcuts(tileId: Int?): List<String>?
    suspend fun getShowShortcutText(): Boolean
    suspend fun setShowShortcutTextEnabled(enabled: Boolean)
    suspend fun getAllTemplateTiles(): Map<Int, TemplateTileConfig>
    suspend fun getTemplateTileAndSaveTileId(tileId: Int): TemplateTileConfig
    suspend fun setAllTemplateTiles(templateTiles: Map<Int, TemplateTileConfig>)
    suspend fun setTemplateTile(tileId: Int, content: String, refreshInterval: Int): TemplateTileConfig
    suspend fun removeTemplateTile(tileId: Int): TemplateTileConfig?
    suspend fun getWearHapticFeedback(): Boolean
    suspend fun setWearHapticFeedback(enabled: Boolean)
    suspend fun getWearToastConfirmation(): Boolean
    suspend fun setWearToastConfirmation(enabled: Boolean)
    suspend fun getWearFavoritesOnly(): Boolean
    suspend fun setWearFavoritesOnly(enabled: Boolean)
}
