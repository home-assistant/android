package io.homeassistant.companion.android.common.data.prefs

import android.os.Parcelable
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import kotlinx.parcelize.Parcelize

enum class NightModeTheme(val storageValue: String) {
    LIGHT("light"),
    DARK("dark"),

    @Deprecated("Kept for backwards compatibility see https://github.com/home-assistant/android/pull/2923")
    ANDROID("android"),
    SYSTEM("system"),
    ;

    companion object {
        fun fromStorageValue(value: String?): NightModeTheme? = entries.firstOrNull { it.storageValue == value }
    }
}

@Parcelize
data class AutoFavorite(val serverId: Int, val entityId: String) : Parcelable

interface PrefsRepository {
    suspend fun getAppVersion(): String?

    suspend fun saveAppVersion(ver: String)

    suspend fun getCurrentNightModeTheme(): NightModeTheme?

    suspend fun saveNightModeTheme(nightModeTheme: NightModeTheme)

    suspend fun getCurrentLang(): String?

    suspend fun saveLang(lang: String)

    suspend fun getLocales(): String?

    suspend fun saveLocales(lang: String)

    suspend fun getControlsAuthRequired(): ControlsAuthRequiredSetting

    suspend fun setControlsAuthRequired(setting: ControlsAuthRequiredSetting)

    suspend fun getControlsEnableStructure(): Boolean

    suspend fun setControlsEnableStructure(enabled: Boolean)

    suspend fun getControlsAuthEntities(): List<String>

    suspend fun setControlsAuthEntities(entities: List<String>)

    suspend fun getControlsPanelServer(): Int?

    suspend fun setControlsPanelServer(serverId: Int)

    suspend fun getControlsPanelPath(): String?

    suspend fun setControlsPanelPath(path: String?)

    suspend fun isFullScreenEnabled(): Boolean

    suspend fun setFullScreenEnabled(enabled: Boolean)

    suspend fun isKeepScreenOnEnabled(): Boolean

    suspend fun setKeepScreenOnEnabled(enabled: Boolean)

    suspend fun getScreenOrientation(): String?

    suspend fun saveScreenOrientation(orientation: String?)

    suspend fun getPageZoomLevel(): Int

    suspend fun setPageZoomLevel(level: Int?)

    suspend fun isPinchToZoomEnabled(): Boolean

    suspend fun setPinchToZoomEnabled(enabled: Boolean)

    suspend fun isAutoPlayVideoEnabled(): Boolean

    suspend fun setAutoPlayVideo(enabled: Boolean)

    suspend fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean

    suspend fun setAlwaysShowFirstViewOnAppStart(enabled: Boolean)

    suspend fun isWebViewDebugEnabled(): Boolean

    suspend fun setWebViewDebugEnabled(enabled: Boolean)

    suspend fun isCrashReporting(): Boolean

    suspend fun setCrashReporting(crashReportingEnabled: Boolean)

    suspend fun saveKeyAlias(alias: String)

    suspend fun getKeyAlias(): String?

    suspend fun getIgnoredSuggestions(): List<String>

    suspend fun setIgnoredSuggestions(ignored: List<String>)

    suspend fun getAutoFavorites(): List<AutoFavorite>

    suspend fun setAutoFavorites(favorites: List<AutoFavorite>)

    suspend fun addAutoFavorite(favorite: AutoFavorite)

    suspend fun isLocationHistoryEnabled(): Boolean

    suspend fun setLocationHistoryEnabled(enabled: Boolean)

    suspend fun getImprovPermissionDisplayedCount(): Int

    suspend fun addImprovPermissionDisplayedCount()

    suspend fun getGestureAction(gesture: HAGesture): GestureAction

    suspend fun setGestureAction(gesture: HAGesture, action: GestureAction)

    suspend fun isChangeLogPopupEnabled(): Boolean

    suspend fun setChangeLogPopupEnabled(enabled: Boolean)

    /** Clean up any app-level preferences that might reference servers */
    suspend fun removeServer(serverId: Int)

    suspend fun showPrivacyHint(): Boolean

    suspend fun setShowPrivacyHint(showPrivacyHint: Boolean)
}
