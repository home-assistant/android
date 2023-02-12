package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.LocalStorage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Named

class WearPrefsRepositoryImpl @Inject constructor(
    @Named("wear") private val localStorage: LocalStorage,
    @Named("integration") private val integrationStorage: LocalStorage
) : WearPrefsRepository {

    companion object {
        private const val MIGRATION_PREF = "migration"
        private const val MIGRATION_VERSION = 1

        private const val PREF_TILE_SHORTCUTS = "tile_shortcuts_list"
        private const val PREF_SHOW_TILE_SHORTCUTS_TEXT = "show_tile_shortcuts_text"
        private const val PREF_TILE_TEMPLATE = "tile_template"
        private const val PREF_TILE_TEMPLATE_REFRESH_INTERVAL = "tile_template_refresh_interval"
        private const val PREF_WEAR_HAPTIC_FEEDBACK = "wear_haptic_feedback"
        private const val PREF_WEAR_TOAST_CONFIRMATION = "wear_toast_confirmation"
        private const val PREF_WEAR_FAVORITES_ONLY = "wear_favorites_only"
    }

    init {
        runBlocking {
            val currentVersion = localStorage.getInt(MIGRATION_PREF)
            if (currentVersion == null || currentVersion < 1) {
                integrationStorage.getString(PREF_TILE_SHORTCUTS)?.let {
                    localStorage.putString(PREF_TILE_SHORTCUTS, it)
                }
                integrationStorage.getBooleanOrNull(PREF_SHOW_TILE_SHORTCUTS_TEXT)?.let {
                    localStorage.putBoolean(PREF_SHOW_TILE_SHORTCUTS_TEXT, it)
                }
                integrationStorage.getString(PREF_TILE_TEMPLATE)?.let {
                    localStorage.putString(PREF_TILE_TEMPLATE, it)
                }
                integrationStorage.getInt(PREF_TILE_TEMPLATE_REFRESH_INTERVAL)?.let {
                    localStorage.putInt(PREF_TILE_TEMPLATE_REFRESH_INTERVAL, it)
                }
                integrationStorage.getBooleanOrNull(PREF_WEAR_HAPTIC_FEEDBACK)?.let {
                    localStorage.putBoolean(PREF_WEAR_HAPTIC_FEEDBACK, it)
                }
                integrationStorage.getBooleanOrNull(PREF_WEAR_TOAST_CONFIRMATION)?.let {
                    localStorage.putBoolean(PREF_WEAR_TOAST_CONFIRMATION, it)
                }

                localStorage.putInt(MIGRATION_PREF, MIGRATION_VERSION)
            }
        }
    }

    override suspend fun getTileShortcuts(): List<String> {
        val jsonArray = JSONArray(localStorage.getString(PREF_TILE_SHORTCUTS) ?: "[]")
        return List(jsonArray.length()) {
            jsonArray.getString(it)
        }
    }

    override suspend fun setTileShortcuts(entities: List<String>) {
        localStorage.putString(PREF_TILE_SHORTCUTS, JSONArray(entities).toString())
    }

    override suspend fun getTemplateTileContent(): String {
        return localStorage.getString(PREF_TILE_TEMPLATE) ?: ""
    }

    override suspend fun getShowShortcutText(): Boolean {
        return localStorage.getBoolean(PREF_SHOW_TILE_SHORTCUTS_TEXT)
    }

    override suspend fun setShowShortcutTextEnabled(enabled: Boolean) {
        localStorage.putBoolean(PREF_SHOW_TILE_SHORTCUTS_TEXT, enabled)
    }

    override suspend fun setTemplateTileContent(content: String) {
        localStorage.putString(PREF_TILE_TEMPLATE, content)
    }

    override suspend fun getTemplateTileRefreshInterval(): Int {
        return localStorage.getInt(PREF_TILE_TEMPLATE_REFRESH_INTERVAL) ?: 0
    }

    override suspend fun setTemplateTileRefreshInterval(interval: Int) {
        localStorage.putInt(PREF_TILE_TEMPLATE_REFRESH_INTERVAL, interval)
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
