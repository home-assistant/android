package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.IntentSender
import io.homeassistant.companion.android.matter.MatterCommissioningRequest
import kotlinx.coroutines.flow.Flow

interface WebViewPresenter {

    fun onViewReady(path: String?)

    fun onGetExternalAuth(context: Context, callback: String, force: Boolean)

    fun checkSecurityVersion()

    fun onRevokeExternalAuth(callback: String)

    fun clearKnownUrls()

    fun isFullScreen(): Boolean

    fun isKeepScreenOnEnabled(): Boolean

    fun isPinchToZoomEnabled(): Boolean
    fun isWebViewDebugEnabled(): Boolean

    fun isAppLocked(): Boolean
    fun setAppActive(active: Boolean)

    fun isLockEnabled(): Boolean
    fun isAutoPlayVideoEnabled(): Boolean

    fun sessionTimeOut(): Int

    fun onFinish()

    fun isSsidUsed(): Boolean

    fun getAuthorizationHeader(): String

    suspend fun parseWebViewColor(webViewColor: String): Int

    fun appCanCommissionMatterDevice(): Boolean
    fun startCommissioningMatterDevice(context: Context)
    fun getMatterCommissioningStatusFlow(): Flow<MatterCommissioningRequest.Status>
    fun getMatterCommissioningIntent(): IntentSender?
}
