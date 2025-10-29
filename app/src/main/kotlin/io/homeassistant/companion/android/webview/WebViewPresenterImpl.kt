package io.homeassistant.companion.android.webview

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.improv.ImprovRepository
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.thread.ThreadManager
import io.homeassistant.companion.android.util.UrlUtil
import io.homeassistant.companion.android.util.UrlUtil.baseIsEqual
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import io.homeassistant.companion.android.webview.externalbus.ExternalBusRepository
import java.net.SocketTimeoutException
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

class WebViewPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val serverManager: ServerManager,
    private val externalBusRepository: ExternalBusRepository,
    private val improvRepository: ImprovRepository,
    private val prefsRepository: PrefsRepository,
    private val matterUseCase: MatterManager,
    private val threadUseCase: ThreadManager,
) : WebViewPresenter {

    private val view = context as WebView

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var serverId: Int = ServerManager.SERVER_ID_ACTIVE

    private var url: URL? = null
    private var urlForServer: Int? = null

    private var improvJob: Job? = null
    private var improvJobStarted = 0L

    private val mutableMatterThreadStep = MutableStateFlow(MatterThreadStep.NOT_STARTED)

    private var matterThreadIntentSender: IntentSender? = null

    init {
        mainScope.launch {
            updateActiveServer()
            externalBusRepository.getSentFlow().collect {
                try {
                    view.sendExternalBusMessage(it)
                } catch (e: Exception) {
                    Timber.w(e, "Unable to send message to external bus $it")
                }
            }
        }
    }

    override fun onViewReady(path: String?) {
        mainScope.launch {
            val oldUrl = url
            val oldUrlForServer = urlForServer

            var server = serverManager.getServer(serverId)
            if (server == null) {
                setActiveServer(ServerManager.SERVER_ID_ACTIVE)
                server = serverManager.getServer(serverId)
            }

            try {
                if (serverManager.authenticationRepository(serverId).getSessionState() ==
                    SessionState.ANONYMOUS
                ) {
                    return@launch
                }
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Unable to get server session state, not continuing")
                return@launch
            }

            val serverConnectionInfo = server?.connection
            url = serverConnectionInfo?.getUrl(
                serverConnectionInfo.isInternal() ||
                    (
                        serverConnectionInfo.prioritizeInternal &&
                            !DisabledLocationHandler.isLocationEnabled(view as Context)
                        ),
            )
            urlForServer = server?.id
            val baseUrl = url

            if (path != null && !path.startsWith("entityId:")) {
                url = UrlUtil.handle(url, path)
            }

            /*
            We only want to cause the UI to reload if the server or URL that we need to load has
            changed. An example of this would be opening the app on wifi with a local url then
            loosing wifi signal and reopening app. Without this we would still be trying to use the
            internal url externally.
             */
            if (
                oldUrlForServer != urlForServer ||
                oldUrl?.protocol != url?.protocol ||
                oldUrl?.host != url?.host ||
                oldUrl?.port != url?.port
            ) {
                view.loadUrl(
                    url = url.toString().toUri()
                        .buildUpon()
                        .appendQueryParameter("external_auth", "1")
                        .build()
                        .toString(),
                    keepHistory = oldUrlForServer == urlForServer,
                    openInApp = url?.baseIsEqual(baseUrl) ?: false,
                )
            }
        }
    }

    override fun getActiveServer(): Int = serverId

    override suspend fun getActiveServerName(): String? = if (serverManager.isRegistered()) {
        serverManager.getServer(serverId)?.friendlyName
    } else {
        null
    }

    override suspend fun updateActiveServer() {
        if (serverManager.isRegistered()) {
            serverManager.getServer()?.let {
                serverId = it.id
            }
        }
    }

    override suspend fun setActiveServer(id: Int) {
        serverManager.getServer(id)?.let {
            if (serverManager.authenticationRepository(id).getSessionState() == SessionState.CONNECTED) {
                serverManager.activateServer(id)
                serverId = id
            }
        }
    }

    override suspend fun switchActiveServer(id: Int) {
        if (serverId != id && serverId != ServerManager.SERVER_ID_ACTIVE) {
            setAppActive(false) // 'Lock' old server
        }
        setActiveServer(id)
        onViewReady(null)
        view.unlockAppIfNeeded()
    }

    override suspend fun nextServer() = moveToServer(next = true)

    override suspend fun previousServer() = moveToServer(next = false)

    private suspend fun moveToServer(next: Boolean) {
        val servers = serverManager.defaultServers
        if (servers.size < 2) return
        val currentServerIndex = servers.indexOfFirst { it.id == serverId }
        if (currentServerIndex > -1) {
            var newServerIndex = if (next) currentServerIndex + 1 else currentServerIndex - 1
            if (newServerIndex == servers.size) newServerIndex = 0
            if (newServerIndex < 0) newServerIndex = servers.size - 1
            servers.getOrNull(newServerIndex)?.let { switchActiveServer(it.id) }
        }
    }

    override fun checkSecurityVersion() {
        mainScope.launch {
            try {
                if (!serverManager.integrationRepository(serverId).isHomeAssistantVersionAtLeast(2021, 1, 5)) {
                    if (serverManager.integrationRepository(serverId).shouldNotifySecurityWarning()) {
                        view.showError(WebView.ErrorType.SECURITY_WARNING)
                    } else {
                        Timber.w("Still not updated but have already notified.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Issue getting version/notifying of security issue.")
            }
        }
    }

    override fun onGetExternalAuth(context: Context, callback: String, force: Boolean) {
        mainScope.launch {
            try {
                view.setExternalAuth(
                    "$callback(true, ${serverManager.authenticationRepository(
                        serverId,
                    ).retrieveExternalAuthentication(force)})",
                )
            } catch (e: Exception) {
                Timber.e(e, "Unable to retrieve external auth")
                val anonymousSession =
                    serverManager.getServer(serverId) == null ||
                        serverManager.authenticationRepository(serverId).getSessionState() == SessionState.ANONYMOUS
                view.setExternalAuth("$callback(false)")
                view.showError(
                    errorType = when {
                        anonymousSession -> WebView.ErrorType.AUTHENTICATION
                        e is SSLException ||
                            (
                                e is SocketTimeoutException &&
                                    e.suppressed.any {
                                        it is SSLException
                                    }
                                ) -> WebView.ErrorType.SSL
                        else -> WebView.ErrorType.TIMEOUT_GENERAL
                    },
                    description = when {
                        anonymousSession -> null
                        e is SSLHandshakeException ||
                            (
                                e is SocketTimeoutException &&
                                    e.suppressed.any {
                                        it is SSLHandshakeException
                                    }
                                ) -> context.getString(commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
                        e is SSLException ||
                            (
                                e is SocketTimeoutException &&
                                    e.suppressed.any {
                                        it is SSLException
                                    }
                                ) -> context.getString(commonR.string.webview_error_SSL_INVALID)
                        else -> null
                    },
                )
            }
        }
    }

    override fun onRevokeExternalAuth(callback: String) {
        mainScope.launch {
            try {
                serverManager.getServer(serverId)?.let {
                    serverManager.authenticationRepository(it.id).revokeSession()
                    serverManager.removeServer(it.id)
                }
                view.setExternalAuth("$callback(true)")
                view.relaunchApp()
            } catch (e: Exception) {
                Timber.e(e, "Unable to revoke session")
                view.setExternalAuth("$callback(false)")
            }
        }
    }

    override suspend fun isFullScreen(): Boolean {
        return prefsRepository.isFullScreenEnabled()
    }

    override suspend fun getScreenOrientation(): String? {
        return prefsRepository.getScreenOrientation()
    }

    override suspend fun isKeepScreenOnEnabled(): Boolean {
        return prefsRepository.isKeepScreenOnEnabled()
    }

    override suspend fun getPageZoomLevel(): Int {
        return prefsRepository.getPageZoomLevel()
    }

    override suspend fun isPinchToZoomEnabled(): Boolean {
        return prefsRepository.isPinchToZoomEnabled()
    }

    override suspend fun isWebViewDebugEnabled(): Boolean {
        return prefsRepository.isWebViewDebugEnabled()
    }

    override suspend fun isAppLocked(): Boolean = if (serverManager.isRegistered()) {
        try {
            serverManager.integrationRepository(serverId).isAppLocked()
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Cannot determine app locked state")
            false
        }
    } else {
        false
    }

    override suspend fun setAppActive(active: Boolean) {
        serverManager.getServer(serverId)?.let {
            try {
                serverManager.integrationRepository(serverId).setAppActive(active)
            } catch (e: IllegalStateException) {
                Timber.w(e, "Cannot set app active $active for server $serverId")
                Unit
            }
        } ?: Unit
        if (!active) stopScanningForImprov(true)
    }

    override suspend fun isAutoPlayVideoEnabled(): Boolean {
        return prefsRepository.isAutoPlayVideoEnabled()
    }

    override suspend fun isAlwaysShowFirstViewOnAppStartEnabled(): Boolean {
        return prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled()
    }

    override fun onExternalBusMessage(message: JsonObject) {
        mainScope.launch {
            externalBusRepository.received(message)
        }
    }

    override suspend fun getGestureAction(direction: GestureDirection, pointerCount: Int): GestureAction {
        val gesture = HAGesture.fromSwipeListener(direction, pointerCount)
        return gesture?.let { prefsRepository.getGestureAction(it) } ?: GestureAction.NONE
    }

    override fun onStart(context: Context) {
        matterUseCase.suppressDiscoveryBottomSheet(context)
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    override suspend fun isSsidUsed(): Boolean =
        serverManager.getServer(serverId)?.connection?.internalSsids?.isNotEmpty() == true

    override suspend fun getAuthorizationHeader(): String {
        return serverManager.getServer(serverId)?.let {
            serverManager.authenticationRepository(serverId).buildBearerToken()
        } ?: ""
    }

    override suspend fun parseWebViewColor(webViewColor: String): Int = withContext(Dispatchers.IO) {
        var color = 0

        Timber.d("Try getting color from webview color \"$webViewColor\".")
        if (webViewColor.isNotEmpty() && webViewColor != "null") {
            try {
                color = parseColorWithRgb(webViewColor)
                Timber.i("Found color $color.")
            } catch (e: Exception) {
                Timber.w(e, "Could not get color from webview.")
            }
        } else {
            Timber.w("Could not get color from webview. Color \"$webViewColor\" is not a valid color.")
        }

        if (color == 0) {
            Timber.w("Couldn't get color.")
        }

        return@withContext color
    }

    private fun parseColorWithRgb(colorString: String): Int {
        val c: Pattern = Pattern.compile("rgb *\\( *([0-9]+), *([0-9]+), *([0-9]+) *\\)")
        val m: Matcher = c.matcher(colorString)
        return if (m.matches()) {
            Color.rgb(
                m.group(1)!!.toInt(),
                m.group(2)!!.toInt(),
                m.group(3)!!.toInt(),
            )
        } else {
            colorString.toColorInt()
        }
    }

    override fun appCanCommissionMatterDevice(): Boolean = matterUseCase.appSupportsCommissioning()

    override fun startCommissioningMatterDevice(context: Context) {
        if (mutableMatterThreadStep.value != MatterThreadStep.REQUESTED) {
            mutableMatterThreadStep.tryEmit(MatterThreadStep.REQUESTED)

            // The app used to sync Thread credentials here until commit 26a472a, but it was
            // (temporarily?) removed due to slowing down the Matter commissioning flow for the user
            // and limited usefulness of the result (because of API limitations)

            startMatterCommissioningFlow(context)
        } // else already waiting for a result, don't send another request
    }

    private fun startMatterCommissioningFlow(context: Context) {
        matterUseCase.startNewCommissioningFlow(
            context,
            { intentSender ->
                Timber.d("Matter commissioning is ready")
                matterThreadIntentSender = intentSender
                mutableMatterThreadStep.tryEmit(MatterThreadStep.MATTER_IN_PROGRESS)
            },
            { e ->
                Timber.e(e, "Matter commissioning couldn't be prepared")
                mutableMatterThreadStep.tryEmit(MatterThreadStep.ERROR_MATTER)
            },
        )
    }

    override fun appCanExportThreadCredentials(): Boolean = threadUseCase.appSupportsThread()

    override fun exportThreadCredentials(context: Context) {
        if (mutableMatterThreadStep.value != MatterThreadStep.REQUESTED) {
            mutableMatterThreadStep.tryEmit(MatterThreadStep.REQUESTED)

            mainScope.launch {
                try {
                    val result = threadUseCase.syncPreferredDataset(
                        context,
                        serverId,
                        true,
                        CoroutineScope(
                            coroutineContext + SupervisorJob(),
                        ),
                    )
                    Timber.d("Export preferred Thread dataset returned $result")

                    when (result) {
                        is ThreadManager.SyncResult.OnlyOnDevice -> {
                            matterThreadIntentSender = result.exportIntent
                            mutableMatterThreadStep.tryEmit(MatterThreadStep.THREAD_EXPORT_TO_SERVER_ONLY)
                        }

                        is ThreadManager.SyncResult.NoneHaveCredentials,
                        is ThreadManager.SyncResult.OnlyOnServer,
                        -> {
                            mutableMatterThreadStep.tryEmit(MatterThreadStep.THREAD_NONE)
                        }

                        is ThreadManager.SyncResult.NotConnected -> {
                            mutableMatterThreadStep.tryEmit(MatterThreadStep.ERROR_THREAD_LOCAL_NETWORK)
                        }

                        else -> {
                            mutableMatterThreadStep.tryEmit(MatterThreadStep.ERROR_THREAD_OTHER)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Unable to export preferred Thread dataset")
                    mutableMatterThreadStep.tryEmit(MatterThreadStep.ERROR_THREAD_OTHER)
                }
            }
        } // else already waiting for a result, don't send another request
    }

    override fun getMatterThreadStepFlow(): Flow<MatterThreadStep> = mutableMatterThreadStep.asStateFlow()

    override fun getMatterThreadIntent(): IntentSender? {
        val intent = matterThreadIntentSender
        matterThreadIntentSender = null
        return intent
    }

    override fun onMatterThreadIntentResult(context: Context, result: ActivityResult) {
        when (mutableMatterThreadStep.value) {
            MatterThreadStep.THREAD_EXPORT_TO_SERVER_MATTER -> {
                mainScope.launch {
                    threadUseCase.sendThreadDatasetExportResult(result, serverId)
                    startMatterCommissioningFlow(context)
                }
            }

            MatterThreadStep.THREAD_EXPORT_TO_SERVER_ONLY -> {
                mainScope.launch {
                    val sent = threadUseCase.sendThreadDatasetExportResult(result, serverId)
                    Timber.d(
                        "Thread ${if (!sent.isNullOrBlank()) "sent credential for $sent" else "did not send credential"}",
                    )
                    if (sent.isNullOrBlank()) {
                        mutableMatterThreadStep.tryEmit(MatterThreadStep.THREAD_NONE)
                    } else {
                        mutableMatterThreadStep.tryEmit(MatterThreadStep.THREAD_SENT)
                    }
                }
            }

            else -> {
                // Any errors will have been shown in the UI provided by Play Services
                if (result.resultCode == Activity.RESULT_OK) {
                    Timber.d("Matter commissioning returned success")
                } else {
                    Timber.d("Matter commissioning returned with non-OK code ${result.resultCode}")
                }
            }
        }
    }

    override fun finishMatterThreadFlow() {
        mutableMatterThreadStep.tryEmit(MatterThreadStep.NOT_STARTED)
    }

    override suspend fun shouldShowImprovPermissions(): Boolean {
        return if (improvRepository.hasPermission(view as Context)) {
            false
        } else {
            prefsRepository.getImprovPermissionDisplayedCount() < 2
        }
    }

    override fun shouldRequestImprovPermission(): String? {
        val returnPermissions = try {
            improvRepository.getRequiredPermissions().filter {
                ContextCompat.checkSelfPermission(view as Context, it) != PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) {
            // Unable to check, ignore
            emptyList<String>()
        }
        return if (returnPermissions.size == 1 && returnPermissions[0] != Manifest.permission.ACCESS_FINE_LOCATION) {
            Timber.d("Should request Improv permission: $returnPermissions")
            returnPermissions[0]
        } else {
            null
        }
    }

    override fun startScanningForImprov(): Boolean {
        if (!improvRepository.hasPermission(view as Context)) {
            Timber.d("Improv scan request ignored because app doesn't have permission")
            return false
        } else {
            Timber.d("Improv scan starting")
        }
        improvJobStarted = System.currentTimeMillis()
        improvJob = mainScope.launch {
            withContext(Dispatchers.IO) {
                improvRepository.startScanning(view as Context)
            }
            improvRepository.getDevices().collect {
                it.forEach { device ->
                    val name = device.name ?: return@forEach
                    externalBusRepository.send(
                        ExternalBusMessage(
                            id = -1,
                            type = "command",
                            command = "improv/discovered_device",
                            payload = mapOf(
                                "name" to name,
                            ),
                        ),
                    )
                }
            }
        }
        return true
    }

    override fun stopScanningForImprov(force: Boolean) {
        if (improvJob?.isActive == true && (force || System.currentTimeMillis() - improvJobStarted > 1000)) {
            Timber.d("Improv scan stopping")
            improvRepository.stopScanning()
            improvJob?.cancel()
        }
    }
}
