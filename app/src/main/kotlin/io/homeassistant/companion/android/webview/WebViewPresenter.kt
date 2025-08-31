package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GestureDirection
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

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

    fun isFullScreen(): Boolean

    fun getScreenOrientation(): String?

    fun isKeepScreenOnEnabled(): Boolean

    fun getPageZoomLevel(): Int
    fun isPinchToZoomEnabled(): Boolean
    fun isWebViewDebugEnabled(): Boolean

    suspend fun isAppLocked(): Boolean
    fun setAppActive(active: Boolean)

    fun isLockEnabled(): Boolean
    fun isAutoPlayVideoEnabled(): Boolean
    fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun onExternalBusMessage(message: JSONObject)

    suspend fun getGestureAction(direction: GestureDirection, pointerCount: Int): GestureAction

    fun onStart(context: Context)

    fun onFinish()

    suspend fun isSsidUsed(): Boolean

    fun getAuthorizationHeader(): String

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
