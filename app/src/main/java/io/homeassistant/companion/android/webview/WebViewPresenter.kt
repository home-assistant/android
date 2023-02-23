package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.matter.MatterFrontendCommissioningStatus
import kotlinx.coroutines.flow.Flow

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun getActiveServer(): Int
    fun updateActiveServer()
    fun setActiveServer(id: Int)
    fun switchActiveServer(id: Int)
    fun nextServer()
    fun previousServer()

    fun onGetExternalAuth(context: Context, callback: String, force: Boolean)

    fun checkSecurityVersion()

    fun onRevokeExternalAuth(callback: String)

    fun isFullScreen(): Boolean

    fun isKeepScreenOnEnabled(): Boolean

    fun isPinchToZoomEnabled(): Boolean
    fun isWebViewDebugEnabled(): Boolean

    fun isAppLocked(): Boolean
    fun setAppActive(active: Boolean)

    fun isLockEnabled(): Boolean
    fun isAutoPlayVideoEnabled(): Boolean
    fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun onFinish()

    fun isSsidUsed(): Boolean

    fun getAuthorizationHeader(): String

    suspend fun parseWebViewColor(webViewColor: String): Int

    fun appCanCommissionMatterDevice(): Boolean
    fun startCommissioningMatterDevice(context: Context)
    fun getMatterCommissioningStatusFlow(): Flow<MatterFrontendCommissioningStatus>
    fun getMatterCommissioningIntent(): IntentSender?
    fun onMatterCommissioningIntentResult(context: Context, result: ActivityResult)
    fun confirmMatterCommissioningError()
}
