package io.homeassistant.companion.android.common.data.prefs

import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting

interface PrefsRepository {
    suspend fun getAppVersion(): String?

    suspend fun saveAppVersion(ver: String)

    suspend fun getCurrentTheme(): String?

    suspend fun saveTheme(theme: String)

    suspend fun getCurrentLang(): String?

    suspend fun saveLang(lang: String)

    suspend fun getLocales(): String?

    suspend fun saveLocales(lang: String)

    suspend fun getControlsAuthRequired(): ControlsAuthRequiredSetting

    suspend fun setControlsAuthRequired(setting: ControlsAuthRequiredSetting)

    suspend fun getControlsAuthEntities(): List<String>

    suspend fun setControlsAuthEntities(entities: List<String>)

    suspend fun isFullScreenEnabled(): Boolean

    suspend fun setFullScreenEnabled(enabled: Boolean)

    suspend fun isKeepScreenOnEnabled(): Boolean

    suspend fun setKeepScreenOnEnabled(enabled: Boolean)

    suspend fun isLandscapeEnabled(): Boolean

    suspend fun setLandscapeEnabled(enabled: Boolean)

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
}
