package io.homeassistant.companion.android.common.data.prefs

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.di.qualifiers.NamedIntegrationStorage
import io.homeassistant.companion.android.di.qualifiers.NamedThemesStorage
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@VisibleForTesting
const val MIGRATION_PREF = "migration"

@VisibleForTesting
const val MIGRATION_VERSION = 1

private const val PREF_VER = "version"
private const val PREF_NIGHT_MODE_THEME = "theme"
private const val PREF_LANG = "lang"
private const val PREF_LOCALES = "locales"
private const val PREF_SCREEN_ORIENTATION = "screen_orientation"
private const val PREF_CONTROLS_AUTH_REQUIRED = "controls_auth_required"
private const val PREF_CONTROLS_AUTH_ENTITIES = "controls_auth_entities"
private const val PREF_CONTROLS_ENABLE_STRUCTURE = "controls_enable_structure"
private const val CONTROLS_PANEL_SERVER = "controls_panel_server"
private const val CONTROLS_PANEL_PATH = "controls_panel_path"
private const val PREF_FULLSCREEN_ENABLED = "fullscreen_enabled"
private const val PREF_KEEP_SCREEN_ON_ENABLED = "keep_screen_on_enabled"
private const val PREF_PAGE_ZOOM_LEVEL = "page_zoom_level"
private const val PREF_PINCH_TO_ZOOM_ENABLED = "pinch_to_zoom_enabled"
private const val PREF_AUTOPLAY_VIDEO = "autoplay_video"
private const val PREF_ALWAYS_SHOW_FIRST_VIEW_ON_APP_START = "always_show_first_view_on_app_start"
private const val PREF_WEBVIEW_DEBUG_ENABLED = "webview_debug_enabled"
private const val PREF_KEY_ALIAS = "key-alias"
private const val PREF_CRASH_REPORTING_DISABLED = "crash_reporting"
private const val PREF_IGNORED_SUGGESTIONS = "ignored_suggestions"
private const val PREF_AUTO_FAVORITES = "auto_favorites"
private const val PREF_LOCATION_HISTORY_DISABLED = "location_history"
private const val PREF_IMPROV_PERMISSION_DISPLAYED = "improv_permission_displayed"
private const val PREF_GESTURE_ACTION_PREFIX = "gesture_action"
private const val PREF_CHANGE_LOG_POPUP_ENABLED = "change_log_popup_enabled"
private const val PREF_SHOW_PRIVACY_HINT = "show_privacy_hint"

/**
 * This class ensure that when we use the local storage in [PrefsRepositoryImpl] the migrations has been made
 * before interacting with it.
 */
private class LocalStorageWithMigration(
    private val localStorage: LocalStorage,
    private val integrationStorage: LocalStorage,
) {
    private val migrationChecked = AtomicBoolean(false)
    private val migrationMutex = Mutex()

    private suspend fun checkMigration() {
        migrationMutex.withLock {
            if (!migrationChecked.get()) {
                val currentVersion = localStorage.getInt(MIGRATION_PREF)
                if (currentVersion == null || currentVersion < 1) {
                    integrationStorage.getString(PREF_CONTROLS_AUTH_REQUIRED)?.let {
                        localStorage.putString(PREF_CONTROLS_AUTH_REQUIRED, it)
                    }
                    integrationStorage.getStringSet(PREF_CONTROLS_AUTH_ENTITIES)?.let {
                        localStorage.putStringSet(PREF_CONTROLS_AUTH_ENTITIES, it)
                    }
                    integrationStorage.getBooleanOrNull(PREF_FULLSCREEN_ENABLED)?.let {
                        localStorage.putBoolean(PREF_FULLSCREEN_ENABLED, it)
                    }
                    integrationStorage.getBooleanOrNull(PREF_KEEP_SCREEN_ON_ENABLED)?.let {
                        localStorage.putBoolean(PREF_KEEP_SCREEN_ON_ENABLED, it)
                    }
                    integrationStorage.getBooleanOrNull(PREF_PINCH_TO_ZOOM_ENABLED)?.let {
                        localStorage.putBoolean(PREF_PINCH_TO_ZOOM_ENABLED, it)
                    }
                    integrationStorage.getBooleanOrNull(PREF_AUTOPLAY_VIDEO)?.let {
                        localStorage.putBoolean(PREF_AUTOPLAY_VIDEO, it)
                    }
                    integrationStorage.getBooleanOrNull(PREF_ALWAYS_SHOW_FIRST_VIEW_ON_APP_START)?.let {
                        localStorage.putBoolean(PREF_ALWAYS_SHOW_FIRST_VIEW_ON_APP_START, it)
                    }
                    integrationStorage.getBooleanOrNull(PREF_WEBVIEW_DEBUG_ENABLED)?.let {
                        localStorage.putBoolean(PREF_WEBVIEW_DEBUG_ENABLED, it)
                    }

                    localStorage.putInt(MIGRATION_PREF, MIGRATION_VERSION)
                }
                migrationChecked.set(true)
            }
        }
    }

    suspend operator fun invoke(): LocalStorage {
        checkMigration()
        return localStorage
    }
}

class PrefsRepositoryImpl @Inject constructor(
    @NamedThemesStorage localStorage: LocalStorage,
    @NamedIntegrationStorage integrationStorage: LocalStorage,
) : PrefsRepository {

    private val localStorage = LocalStorageWithMigration(localStorage, integrationStorage)

    override suspend fun getAppVersion(): String? {
        return localStorage().getString(PREF_VER)
    }

    override suspend fun saveAppVersion(ver: String) {
        localStorage().putString(PREF_VER, ver)
    }

    override suspend fun getCurrentNightModeTheme(): NightModeTheme? {
        return NightModeTheme.fromStorageValue(localStorage().getString(PREF_NIGHT_MODE_THEME))
    }

    override suspend fun saveNightModeTheme(nightModeTheme: NightModeTheme) {
        localStorage().putString(PREF_NIGHT_MODE_THEME, nightModeTheme.storageValue)
    }

    override suspend fun getCurrentLang(): String? {
        return localStorage().getString(PREF_LANG)
    }

    override suspend fun saveLang(lang: String) {
        localStorage().putString(PREF_LANG, lang)
    }

    override suspend fun getLocales(): String? {
        return localStorage().getString(PREF_LOCALES)
    }

    override suspend fun saveLocales(lang: String) {
        localStorage().putString(PREF_LOCALES, lang)
    }

    override suspend fun getScreenOrientation(): String? {
        return localStorage().getString(PREF_SCREEN_ORIENTATION)
    }

    override suspend fun saveScreenOrientation(orientation: String?) {
        localStorage().putString(PREF_SCREEN_ORIENTATION, orientation)
    }

    override suspend fun getControlsAuthRequired(): ControlsAuthRequiredSetting {
        val current = localStorage().getString(PREF_CONTROLS_AUTH_REQUIRED)
        return ControlsAuthRequiredSetting.entries.firstOrNull {
            it.name == current
        } ?: ControlsAuthRequiredSetting.NONE
    }

    override suspend fun setControlsAuthRequired(setting: ControlsAuthRequiredSetting) {
        localStorage().putString(PREF_CONTROLS_AUTH_REQUIRED, setting.name)
    }

    override suspend fun getControlsEnableStructure(): Boolean {
        return localStorage().getBooleanOrNull(PREF_CONTROLS_ENABLE_STRUCTURE) ?: false
    }

    override suspend fun setControlsEnableStructure(enabled: Boolean) {
        localStorage().putBoolean(PREF_CONTROLS_ENABLE_STRUCTURE, enabled)
    }

    override suspend fun getControlsAuthEntities(): List<String> {
        return localStorage().getStringSet(PREF_CONTROLS_AUTH_ENTITIES)?.toList() ?: emptyList()
    }

    override suspend fun setControlsAuthEntities(entities: List<String>) {
        localStorage().putStringSet(PREF_CONTROLS_AUTH_ENTITIES, entities.toSet())
    }

    override suspend fun getControlsPanelServer(): Int? {
        return localStorage().getInt(CONTROLS_PANEL_SERVER)
    }

    override suspend fun setControlsPanelServer(serverId: Int) {
        localStorage().putInt(CONTROLS_PANEL_SERVER, serverId)
    }

    override suspend fun getControlsPanelPath(): String? {
        return localStorage().getString(CONTROLS_PANEL_PATH)
    }

    override suspend fun setControlsPanelPath(path: String?) {
        if (path.isNullOrBlank()) {
            localStorage().remove(CONTROLS_PANEL_PATH)
        } else {
            localStorage().putString(CONTROLS_PANEL_PATH, path)
        }
    }

    override suspend fun isFullScreenEnabled(): Boolean {
        return localStorage().getBoolean(PREF_FULLSCREEN_ENABLED)
    }

    override suspend fun setFullScreenEnabled(enabled: Boolean) {
        localStorage().putBoolean(PREF_FULLSCREEN_ENABLED, enabled)
    }

    override suspend fun isKeepScreenOnEnabled(): Boolean {
        return localStorage().getBoolean(PREF_KEEP_SCREEN_ON_ENABLED)
    }

    override suspend fun setKeepScreenOnEnabled(enabled: Boolean) {
        localStorage().putBoolean(PREF_KEEP_SCREEN_ON_ENABLED, enabled)
    }

    override suspend fun getPageZoomLevel(): Int {
        return localStorage().getInt(PREF_PAGE_ZOOM_LEVEL) ?: 100
    }

    override suspend fun setPageZoomLevel(level: Int?) {
        localStorage().putInt(PREF_PAGE_ZOOM_LEVEL, level)
    }

    override suspend fun isPinchToZoomEnabled(): Boolean {
        return localStorage().getBoolean(PREF_PINCH_TO_ZOOM_ENABLED)
    }

    override suspend fun setPinchToZoomEnabled(enabled: Boolean) {
        localStorage().putBoolean(PREF_PINCH_TO_ZOOM_ENABLED, enabled)
    }

    override suspend fun isAutoPlayVideoEnabled(): Boolean {
        return localStorage().getBoolean(PREF_AUTOPLAY_VIDEO)
    }

    override suspend fun setAutoPlayVideo(enabled: Boolean) {
        localStorage().putBoolean(PREF_AUTOPLAY_VIDEO, enabled)
    }

    override suspend fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean {
        return localStorage().getBoolean(PREF_ALWAYS_SHOW_FIRST_VIEW_ON_APP_START)
    }

    override suspend fun setAlwaysShowFirstViewOnAppStart(enabled: Boolean) {
        localStorage().putBoolean(PREF_ALWAYS_SHOW_FIRST_VIEW_ON_APP_START, enabled)
    }

    override suspend fun isWebViewDebugEnabled(): Boolean {
        return localStorage().getBoolean(PREF_WEBVIEW_DEBUG_ENABLED)
    }

    override suspend fun setWebViewDebugEnabled(enabled: Boolean) {
        localStorage().putBoolean(PREF_WEBVIEW_DEBUG_ENABLED, enabled)
    }

    override suspend fun isCrashReporting(): Boolean {
        return !localStorage().getBoolean(PREF_CRASH_REPORTING_DISABLED)
    }

    override suspend fun setCrashReporting(crashReportingEnabled: Boolean) {
        localStorage().putBoolean(PREF_CRASH_REPORTING_DISABLED, !crashReportingEnabled)
    }

    override suspend fun saveKeyAlias(alias: String) {
        localStorage().putString(PREF_KEY_ALIAS, alias)
    }

    override suspend fun getKeyAlias(): String? {
        return localStorage().getString(PREF_KEY_ALIAS)
    }

    override suspend fun getIgnoredSuggestions(): List<String> {
        return localStorage().getStringSet(PREF_IGNORED_SUGGESTIONS)?.toList() ?: emptyList()
    }

    override suspend fun setIgnoredSuggestions(ignored: List<String>) {
        localStorage().putStringSet(PREF_IGNORED_SUGGESTIONS, ignored.toSet())
    }

    override suspend fun getAutoFavorites(): List<AutoFavorite> {
        return localStorage().getString(PREF_AUTO_FAVORITES)?.removeSurrounding("[", "]")?.split(", ")?.filter {
            it.isNotBlank()
        }?.mapNotNull {
            it.split("-").run {
                if (size != 2) {
                    null
                } else {
                    AutoFavorite(this[0].toInt(), this[1])
                }
            }
        } ?: emptyList()
    }

    override suspend fun setAutoFavorites(favorites: List<AutoFavorite>) {
        localStorage().putString(PREF_AUTO_FAVORITES, favorites.map { "${it.serverId}-${it.entityId}" }.toString())
    }

    override suspend fun addAutoFavorite(favorite: AutoFavorite) {
        val favorites = getAutoFavorites().toMutableList()
        if (!favorites.contains(favorite)) {
            favorites.add(favorite)
            setAutoFavorites(favorites)
        }
    }

    override suspend fun isLocationHistoryEnabled(): Boolean {
        return !localStorage().getBoolean(PREF_LOCATION_HISTORY_DISABLED)
    }

    override suspend fun setLocationHistoryEnabled(enabled: Boolean) {
        localStorage().putBoolean(PREF_LOCATION_HISTORY_DISABLED, !enabled)
    }

    override suspend fun getImprovPermissionDisplayedCount(): Int {
        return localStorage().getInt(PREF_IMPROV_PERMISSION_DISPLAYED) ?: 0
    }

    override suspend fun addImprovPermissionDisplayedCount() {
        localStorage().putInt(PREF_IMPROV_PERMISSION_DISPLAYED, getImprovPermissionDisplayedCount() + 1)
    }

    override suspend fun getGestureAction(gesture: HAGesture): GestureAction {
        val current = localStorage().getString("${PREF_GESTURE_ACTION_PREFIX}_${gesture.name}")
        val action = GestureAction.entries.firstOrNull { it.name == current }
        return when {
            // User preference
            action != null -> action
            // Defaults
            gesture == HAGesture.SWIPE_UP_THREE -> GestureAction.SERVER_LIST
            gesture == HAGesture.SWIPE_DOWN_THREE -> GestureAction.QUICKBAR_DEFAULT
            gesture == HAGesture.SWIPE_LEFT_THREE -> GestureAction.SERVER_PREVIOUS
            gesture == HAGesture.SWIPE_RIGHT_THREE -> GestureAction.SERVER_NEXT
            // No user preference and not default
            else -> GestureAction.NONE
        }
    }

    override suspend fun setGestureAction(gesture: HAGesture, action: GestureAction) {
        localStorage().putString("${PREF_GESTURE_ACTION_PREFIX}_${gesture.name}", action.name)
    }

    override suspend fun isChangeLogPopupEnabled(): Boolean {
        return localStorage().getBooleanOrNull(PREF_CHANGE_LOG_POPUP_ENABLED) ?: true
    }

    override suspend fun setChangeLogPopupEnabled(enabled: Boolean) {
        localStorage().putBoolean(PREF_CHANGE_LOG_POPUP_ENABLED, enabled)
    }

    override suspend fun removeServer(serverId: Int) {
        val controlsAuthEntities = getControlsAuthEntities().filter { it.split(".")[0].toIntOrNull() != serverId }
        setControlsAuthEntities(controlsAuthEntities)

        val autoFavorites = getAutoFavorites().filter { it.serverId != serverId }
        setAutoFavorites(autoFavorites)

        if (getControlsPanelServer() == serverId) {
            localStorage().remove(CONTROLS_PANEL_SERVER)
            setControlsPanelPath(null)
        }
    }

    override suspend fun showPrivacyHint(): Boolean {
        return localStorage().getBooleanOrNull(PREF_SHOW_PRIVACY_HINT) ?: true
    }

    override suspend fun setShowPrivacyHint(showPrivacyHint: Boolean) {
        localStorage().putBoolean(PREF_SHOW_PRIVACY_HINT, showPrivacyHint)
    }
}
