package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GestureDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun getActiveServer(): Int
    suspend fun getActiveServerName(): String?
    suspend fun updateActiveServer()
    suspend fun setActiveServer(id: Int)
    suspend fun switchActiveServer(id: Int)
    suspend fun nextServer()
    suspend fun previousServer()

    fun onGetExternalAuth(context: Context, callback: String, force: Boolean)

    fun checkSecurityVersion()

    fun onRevokeExternalAuth(callback: String)

    suspend fun isFullScreen(): Boolean

    suspend fun getScreenOrientation(): String?

    suspend fun isKeepScreenOnEnabled(): Boolean

    suspend fun getPageZoomLevel(): Int
    suspend fun isPinchToZoomEnabled(): Boolean
    suspend fun isWebViewDebugEnabled(): Boolean

    suspend fun isAppLocked(): Boolean
    suspend fun setAppActive(active: Boolean)

    suspend fun isAutoPlayVideoEnabled(): Boolean
    suspend fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean

    fun onExternalBusMessage(message: JsonObject)

    suspend fun getGestureAction(direction: GestureDirection, pointerCount: Int): GestureAction

    fun onStart(context: Context)

    fun onFinish()

    suspend fun isSsidUsed(): Boolean

    suspend fun getAuthorizationHeader(): String

    suspend fun parseWebViewColor(webViewColor: String): Int

    fun appCanCommissionMatterDevice(): Boolean
    fun startCommissioningMatterDevice(context: Context)

    /** @return `true` if the app can send this device's preferred Thread credential to the server */
    fun appCanExportThreadCredentials(): Boolean
    fun exportThreadCredentials(context: Context)
    fun getMatterThreadStepFlow(): Flow<MatterThreadStep>
    fun getMatterThreadIntent(): IntentSender?
    fun onMatterThreadIntentResult(context: Context, result: ActivityResult)
    fun finishMatterThreadFlow()

    /** @return `true` if the app should prompt the user for Improv permissions before scanning */
    suspend fun shouldShowImprovPermissions(): Boolean

    /**
     * @return Improv permission the app should request directly, without showing a prompt.
     * This may occur when one of two Bluetooth related permissions is granted and the other one
     * is not. The system should automatically grant this when requested.
     * */
    fun shouldRequestImprovPermission(): String?

    /** @return `true` if the app tried starting scanning or `false` if it was missing permissions */
    fun startScanningForImprov(): Boolean
    fun stopScanningForImprov(force: Boolean)
}
