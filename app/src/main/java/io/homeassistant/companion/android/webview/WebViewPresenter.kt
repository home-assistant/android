package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun getActiveServer(): Int
    fun getActiveServerName(): String?
    fun updateActiveServer()
    fun setActiveServer(id: Int)
    fun switchActiveServer(id: Int)
    fun nextServer()
    fun previousServer()

    fun onGetExternalAuth(context: Context, callback: String, force: Boolean)

    fun checkSecurityVersion()

    fun onRevokeExternalAuth(callback: String)

    fun isFullScreen(): Boolean

    fun getScreenOrientation(): String?

    fun isKeepScreenOnEnabled(): Boolean

    fun getPageZoomLevel(): Int
    fun isPinchToZoomEnabled(): Boolean
    fun isWebViewDebugEnabled(): Boolean

    fun isAppLocked(): Boolean
    fun setAppActive(active: Boolean)

    fun isLockEnabled(): Boolean
    fun isAutoPlayVideoEnabled(): Boolean
    fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun onExternalBusMessage(message: JSONObject)

    fun onStart(context: Context)

    fun onFinish()

    fun isSsidUsed(): Boolean

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
}
