package io.homeassistant.companion.android.frontend.handler

import io.homeassistant.companion.android.frontend.externalbus.WebViewScript
import kotlinx.coroutines.flow.Flow

/**
 * Observes the frontend external bus for incoming events and scripts to evaluate.
 *
 * This interface exposes the ViewModel-facing API of [FrontendMessageHandler],
 * separating it from the bridge-facing [io.homeassistant.companion.android.frontend.js.FrontendJsHandler].
 */
interface FrontendBusObserver {

    /**
     * Flow of events from incoming external bus messages and authentication results.
     */
    fun messageResults(): Flow<FrontendHandlerEvent>

    /**
     * Returns a flow of scripts to evaluate in the WebView.
     *
     * The WebView should collect this flow and call `evaluateJavascript` for each script,
     * then complete the deferred result with the evaluation output.
     */
    fun scriptsToEvaluate(): Flow<WebViewScript>
}
