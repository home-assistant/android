package io.homeassistant.companion.android.common.data.prefs

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.util.jsonArrayOrNull
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.common.util.toJsonObject
import io.homeassistant.companion.android.common.util.toJsonObjectOrNull
import io.homeassistant.companion.android.common.util.toStringList
import io.homeassistant.companion.android.di.qualifiers.NamedIntegrationStorage
import io.homeassistant.companion.android.di.qualifiers.NamedWearStorage
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class WearPrefsRepositoryImpl @Inject constructor(
    @NamedWearStorage private val localStorage: LocalStorage,
    @NamedIntegrationStorage private val integrationStorage: LocalStorage,
) : WearPrefsRepository {

    companion object {
        @VisibleForTesting
        const val MIGRATION_PREF = "migration"

        @VisibleForTesting
        const val MIGRATION_VERSION = 2

        private const val PREF_TILE_SHORTCUTS = "tile_shortcuts_list"
        private const val PREF_SHOW_TILE_SHORTCUTS_TEXT = "show_tile_shortcuts_text"

        @VisibleForTesting
        const val PREF_TILE_TEMPLATES = "tile_templates"
        private const val PREF_WEAR_HAPTIC_FEEDBACK = "wear_haptic_feedback"
        private const val PREF_WEAR_TOAST_CONFIRMATION = "wear_toast_confirmation"
        private const val PREF_WEAR_FAVORITES_ONLY = "wear_favorites_only"

        private const val UNKNOWN_TEMPLATE_TILE_ID = -1
    }

    init {
        runBlocking {
            val legacyPrefTileTemplate = "tile_template"
            val legacyPrefTileTemplateRefreshInterval = "tile_template_refresh_interval"

            val currentVersion = localStorage.getInt(MIGRATION_PREF)
            if (currentVersion == null || currentVersion < 1) {
                integrationStorage.getString(PREF_TILE_SHORTCUTS)?.let {
                    localStorage.putString(PREF_TILE_SHORTCUTS, it)
                }
                integrationStorage.getBooleanOrNull(PREF_SHOW_TILE_SHORTCUTS_TEXT)?.let {
                    localStorage.putBoolean(PREF_SHOW_TILE_SHORTCUTS_TEXT, it)
                }
                integrationStorage.getString(legacyPrefTileTemplate)?.let {
                    localStorage.putString(legacyPrefTileTemplate, it)
                }
                integrationStorage.getInt(legacyPrefTileTemplateRefreshInterval)?.let {
                    localStorage.putInt(legacyPrefTileTemplateRefreshInterval, it)
                }
                integrationStorage.getBooleanOrNull(PREF_WEAR_HAPTIC_FEEDBACK)?.let {
                    localStorage.putBoolean(PREF_WEAR_HAPTIC_FEEDBACK, it)
                }
                integrationStorage.getBooleanOrNull(PREF_WEAR_TOAST_CONFIRMATION)?.let {
                    localStorage.putBoolean(PREF_WEAR_TOAST_CONFIRMATION, it)
                }

                localStorage.putInt(MIGRATION_PREF, MIGRATION_VERSION)
            }

            if (currentVersion == null || currentVersion < 2) {
                val template = localStorage.getString(legacyPrefTileTemplate)
                val templateRefreshInterval = localStorage.getInt(
                    legacyPrefTileTemplateRefreshInterval,
                )

                if (template != null && templateRefreshInterval != null) {
                    val templates = mapOf(
                        UNKNOWN_TEMPLATE_TILE_ID.toString() to
                            kotlinJsonMapper.encodeToString(TemplateTileConfig(template, templateRefreshInterval)),
                    )

                    localStorage.putString(PREF_TILE_TEMPLATES, templates.toJsonObject().toString())
                }

                localStorage.remove(legacyPrefTileTemplate)
                localStorage.remove(legacyPrefTileTemplateRefreshInterval)

                localStorage.putInt(MIGRATION_PREF, MIGRATION_VERSION)
            }
        }
    }

    override suspend fun getTileShortcutsAndSaveTileId(tileId: Int): List<String> {
        val tileIdToShortcutsMap = getAllTileShortcuts()
        return if (null in tileIdToShortcutsMap && tileId !in tileIdToShortcutsMap) {
            // if there are shortcuts with an unknown (null) tileId key from a previous installation,
            // and the tileId parameter is not already present in the map, associate it with those shortcuts
            val entities = removeTileShortcuts(null)!!
            setTileShortcuts(tileId, entities)
            entities
        } else {
            val entities = tileIdToShortcutsMap[tileId]
            if (entities == null) {
                setTileShortcuts(tileId, emptyList())
            }
            entities ?: emptyList()
        }
    }

    override suspend fun getAllTileShortcuts(): Map<Int?, List<String>> {
        return localStorage.getString(PREF_TILE_SHORTCUTS)?.let { jsonStr ->
            runCatching {
                jsonStr.toJsonObjectOrNull()
            }.fold(
                onSuccess = { jsonObject ->
                    buildMap {
                        jsonObject?.keys?.forEach { stringKey ->
                            val intKey = stringKey.takeUnless { it == "null" }?.toInt()
                            val jsonArray = jsonObject[stringKey]?.jsonArrayOrNull()
                            val entities = jsonArray?.toStringList() ?: emptyList()
                            put(intKey, entities)
                        }
                    }
                },
                onFailure = {
                    // backward compatibility with the previous format when there was only one Shortcut Tile:
                    val jsonArray = jsonStr.toJsonObjectOrNull()?.jsonArrayOrNull()
                    jsonArray?.let {
                        val entities = jsonArray.toStringList()
                        mapOf(
                            // the key is null since we don't (yet) have the tileId
                            null to entities,
                        )
                    }
                },
            )
        } ?: emptyMap()
    }

    override suspend fun setTileShortcuts(tileId: Int?, entities: List<String>) {
        val map = getAllTileShortcuts() + mapOf(tileId to entities)
        setTileShortcuts(map)
    }

    private suspend fun setTileShortcuts(map: Map<Int?, List<String>>) {
        val jsonStr = Json.encodeToString(map)
        localStorage.putString(PREF_TILE_SHORTCUTS, jsonStr)
    }

    override suspend fun removeTileShortcuts(tileId: Int?): List<String>? {
        val tileShortcutsMap = getAllTileShortcuts().toMutableMap()
        val entities = tileShortcutsMap.remove(tileId)
        setTileShortcuts(tileShortcutsMap)
        return entities
    }

    override suspend fun getShowShortcutText(): Boolean {
        return localStorage.getBoolean(PREF_SHOW_TILE_SHORTCUTS_TEXT)
    }

    override suspend fun setShowShortcutTextEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_SHOW_TILE_SHORTCUTS_TEXT, enabled)
    }

    override suspend fun getAllTemplateTiles(): Map<Int, TemplateTileConfig> {
        return localStorage.getString(PREF_TILE_TEMPLATES)?.let { jsonStr ->
            kotlinJsonMapper.decodeFromString<Map<String, TemplateTileConfig>>(jsonStr)
                .mapKeys { it.key.toInt() }
        } ?: emptyMap()
    }

    override suspend fun getTemplateTileAndSaveTileId(tileId: Int): TemplateTileConfig {
        val tileIdToTemplatesMap = getAllTemplateTiles()
        return if (UNKNOWN_TEMPLATE_TILE_ID in tileIdToTemplatesMap && tileId !in tileIdToTemplatesMap) {
            // if there are Templates with an unknown (-1) tileId key from a previous installation,
            // and the tileId parameter is not already present in the map, associate it with that Template
            val templateData = removeTemplateTile(UNKNOWN_TEMPLATE_TILE_ID)!!
            setTemplateTile(tileId, templateData.template, templateData.refreshInterval)
            templateData
        } else {
            var templateData = tileIdToTemplatesMap[tileId]
            if (templateData == null) {
                templateData = setTemplateTile(tileId, "", 0)
            }
            templateData
        }
    }

    override suspend fun setAllTemplateTiles(templateTiles: Map<Int, TemplateTileConfig>) {
        val jsonStr = kotlinJsonMapper.encodeToString(templateTiles.mapKeys { it.key.toString() })
        localStorage.putString(PREF_TILE_TEMPLATES, jsonStr)
    }

    override suspend fun setTemplateTile(tileId: Int, content: String, refreshInterval: Int): TemplateTileConfig {
        val templateTileConfig = TemplateTileConfig(content, refreshInterval)
        val map = getAllTemplateTiles() + mapOf(tileId to templateTileConfig)
        setAllTemplateTiles(map)
        return templateTileConfig
    }

    override suspend fun removeTemplateTile(tileId: Int): TemplateTileConfig? {
        val templateTilesMap = getAllTemplateTiles().toMutableMap()
        val templateTile = templateTilesMap.remove(tileId)
        setAllTemplateTiles(templateTilesMap)
        return templateTile
    }

    override suspend fun getWearHapticFeedback(): Boolean {
        return localStorage.getBoolean(PREF_WEAR_HAPTIC_FEEDBACK)
    }

    override suspend fun setWearHapticFeedback(enabled: Boolean) {
        localStorage.putBoolean(PREF_WEAR_HAPTIC_FEEDBACK, enabled)
    }

    override suspend fun getWearToastConfirmation(): Boolean {
        return localStorage.getBoolean(PREF_WEAR_TOAST_CONFIRMATION)
    }

    override suspend fun setWearToastConfirmation(enabled: Boolean) {
        localStorage.putBoolean(PREF_WEAR_TOAST_CONFIRMATION, enabled)
    }

    override suspend fun getWearFavoritesOnly(): Boolean {
        return localStorage.getBoolean(PREF_WEAR_FAVORITES_ONLY)
    }

    override suspend fun setWearFavoritesOnly(enabled: Boolean) {
        localStorage.putBoolean(PREF_WEAR_FAVORITES_ONLY, enabled)
    }
}
