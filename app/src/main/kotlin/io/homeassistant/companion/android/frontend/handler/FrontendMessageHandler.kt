package io.homeassistant.companion.android.frontend.handler

import android.content.pm.PackageManager
import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.download.FrontendDownloadManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConfigGetMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ConnectionStatusMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.HandleBlobMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.IncomingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenAssistSettingsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.OpenSettingsMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.TagWriteMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.ThemeUpdateMessage
import io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ConfigResult
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage
import io.homeassistant.companion.android.frontend.js.FrontendJsHandler
import io.homeassistant.companion.android.frontend.session.AuthPayload
import io.homeassistant.companion.android.frontend.session.ExternalAuthResult
import io.homeassistant.companion.android.frontend.session.RevokeAuthResult
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.thread.ThreadManager
import io.homeassistant.companion.android.util.sensitive
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.JsonElement
import timber.log.Timber

/**
 * Handles external bus message routing and script evaluation for the frontend WebView.
 *
 * This handler implements [FrontendJsHandler] to receive JavaScript bridge callbacks
 * and processes incoming messages from the Home Assistant frontend via the external bus,
 * mapping them to [FrontendHandlerEvent].
 *
 * Responsibilities:
 * - Route incoming external bus messages to repository
 * - Handle the response of the messages that needs responses
 * - Exposes the needed events based on the messaged received
 * - Provide scripts flow for WebView JavaScript evaluation
 * - Provide a way to execute scripts on the frontend
 */
@ViewModelScoped
class FrontendMessageHandler @Inject constructor(
    private val externalBusRepository: FrontendExternalBusRepository,
    private val packageManager: PackageManager,
    private val matterManager: MatterManager,
    private val threadManager: ThreadManager,
    private val appVersionProvider: AppVersionProvider,
    private val sessionManager: ServerSessionManager,
    private val downloadManager: FrontendDownloadManager,
    @param:IsAutomotive private val isAutomotive: Boolean,
) : FrontendJsHandler,
    FrontendBusObserver {

    private val jsCallbackEvents = MutableSharedFlow<FrontendHandlerEvent>(extraBufferCapacity = 1)

    /**
     * Called when the frontend requests authentication.
     *
     * The bridge has already parsed and validated the callback name before calling this.
     *
     * Opts into [EvaluateJavascriptUsage] because the auth protocol runs on its own channel,
     * not the external bus: the frontend installs a callback on `window` (e.g.
     * `window.externalAuthSetToken`) and waits for the native app to invoke it directly
     * with the auth response.
     */
    @OptIn(EvaluateJavascriptUsage::class)
    override suspend fun getExternalAuth(authPayload: AuthPayload, serverId: Int) {
        Timber.d("getExternalAuth called")
        when (val result = sessionManager.getExternalAuth(serverId, authPayload)) {
            is ExternalAuthResult.Success -> {
                externalBusRepository.evaluateScript(result.callbackScript)
            }

            is ExternalAuthResult.Failed -> {
                externalBusRepository.evaluateScript(result.callbackScript)
                result.error?.let { jsCallbackEvents.tryEmit(FrontendHandlerEvent.AuthError(it)) }
            }
        }
    }

    /**
     * Called when the frontend requests to revoke the current authentication.
     *
     * Opts into [EvaluateJavascriptUsage] for the same reason as [getExternalAuth]: the frontend
     * installs a `window.externalAuthRevokeToken` callback and waits for the native app to
     * invoke it directly.
     */
    @OptIn(EvaluateJavascriptUsage::class)
    override suspend fun revokeExternalAuth(authPayload: AuthPayload, serverId: Int) {
        Timber.d("revokeExternalAuth called")
        when (val result = sessionManager.revokeExternalAuth(serverId, authPayload)) {
            is RevokeAuthResult.Success -> {
                externalBusRepository.evaluateScript(result.callbackScript)
            }

            is RevokeAuthResult.Failed -> {
                externalBusRepository.evaluateScript(result.callbackScript)
            }
        }
    }

    override suspend fun externalBus(message: JsonElement) {
        Timber.v("External bus message received: ${sensitive { message.toString() }}")
        externalBusRepository.onMessageReceived(message)
    }

    /**
     * Flow of events from incoming external bus messages and JavaScript bridge callbacks.
     *
     * Merges deserialized external bus messages with events from JS callbacks (auth errors,
     * download results) into a single stream of [FrontendHandlerEvent].
     */
    override fun messageResults(): Flow<FrontendHandlerEvent> {
        val incomingResults = externalBusRepository.incomingMessages().map { message ->
            handleMessage(message)
        }
        return merge(incomingResults, jsCallbackEvents)
    }

    override fun webViewActions(): Flow<WebViewAction> = externalBusRepository.webViewActions()

    private suspend fun handleMessage(message: IncomingExternalBusMessage): FrontendHandlerEvent {
        return when (message) {
            is ConnectionStatusMessage -> {
                val isConnected = message.payload.isConnected
                Timber.d("Connection status: ${if (isConnected) "connected" else "disconnected"}")
                if (isConnected) {
                    FrontendHandlerEvent.Connected
                } else {
                    FrontendHandlerEvent.Disconnected
                }
            }

            is ConfigGetMessage -> {
                Timber.d("Config/get request received with id: ${message.id}")
                sendConfigResponse(message.id)
                FrontendHandlerEvent.ConfigSent
            }

            is OpenSettingsMessage -> {
                Timber.d("Open settings request received with id: ${message.id}")
                FrontendHandlerEvent.OpenSettings
            }

            is OpenAssistSettingsMessage -> {
                Timber.d("Open assist settings request received with id: ${message.id}")
                FrontendHandlerEvent.OpenAssistSettings
            }

            is OpenAssistMessage -> {
                Timber.d("Open assist request received with id: ${message.id}")
                FrontendHandlerEvent.ShowAssist(
                    pipelineId = message.payload.pipelineId,
                    startListening = message.payload.startListening,
                )
            }

            is ThemeUpdateMessage -> {
                Timber.d("Theme update received")
                FrontendHandlerEvent.ThemeUpdated
            }

            is HapticMessage -> FrontendHandlerEvent.PerformHaptic(message.payload)

            is TagWriteMessage -> {
                Timber.d("Tag write request received with id: ${message.id}")
                FrontendHandlerEvent.WriteNfcTag(
                    messageId = message.id ?: -1,
                    tagId = message.payload.tag,
                )
            }

            is HandleBlobMessage -> {
                Timber.d("handleBlob called with filename=${message.filename}")
                val result = downloadManager.handleBlob(data = message.data, filename = message.filename)
                FrontendHandlerEvent.DownloadCompleted(result)
            }

            is UnknownIncomingMessage -> {
                Timber.d("Unknown message type received: ${message.content}")
                FrontendHandlerEvent.UnknownMessage
            }
        }
    }

    private suspend fun sendConfigResponse(messageId: Int?) {
        val hasNfc = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        val canCommissionMatter = matterManager.appSupportsCommissioning()
        val canExportThread = threadManager.appSupportsThread()
        val hasBarCodeScanner = if (
            packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) && !isAutomotive
        ) {
            1
        } else {
            0
        }

        val response = ResultMessage.config(
            id = messageId,
            config = ConfigResult.create(
                hasNfc = hasNfc,
                canCommissionMatter = canCommissionMatter,
                canExportThread = canExportThread,
                hasBarCodeScanner = hasBarCodeScanner,
                appVersion = appVersionProvider(),
            ),
        )
        externalBusRepository.send(response)
    }
}
