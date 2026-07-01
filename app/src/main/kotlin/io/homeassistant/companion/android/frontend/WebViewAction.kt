package io.homeassistant.companion.android.frontend

import android.webkit.WebView
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.homeassistant.companion.android.frontend.haptic.HapticFeedbackPerformer
import io.homeassistant.companion.android.util.compose.webview.settings
import io.homeassistant.companion.android.util.sensitive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Clicks the sidebar anchor of the frontend's default panel (read from `localStorage.defaultPanel`,
 * falling back to the first sidebar item) and scrolls to the top. Used by
 * [WebViewAction.NavigateToDefaultPanelViaSidebar].
 */
private const val DEFAULT_PANEL_SIDEBAR_CLICK_SCRIPT = """
    var anchor = 'a:nth-child(1)';
    var defaultPanel = window.localStorage.getItem('defaultPanel')?.replaceAll('"',"");
    if(defaultPanel) anchor = 'a[href="/' + defaultPanel + '"]';
    document.querySelector('body > home-assistant').shadowRoot.querySelector('home-assistant-main')
                                                   .shadowRoot.querySelector('ha-sidebar')
                                                   .shadowRoot.querySelector('paper-listbox > ' + anchor).click();
    window.scrollTo(0, 0);
"""

/**
 * Actions that require direct interaction with the WebView.
 *
 * These actions are emitted by the ViewModel and consumed by the Screen layer,
 * which holds the WebView reference. This decouples WebView operations from
 * business logic while keeping them type-safe.
 *
 * Any feature that needs to trigger a WebView operation from the ViewModel
 * (e.g., gestures, script evaluation, haptic feedback) should use this sealed
 * interface rather than passing the WebView reference to non-UI layers.
 *
 * The Screen collects these via [FrontendViewModel.webViewActions] and executes
 * the corresponding WebView method.
 */
sealed interface WebViewAction {

    sealed interface AwaitableAction<T> : WebViewAction {
        /**
         * Marker for actions that signal completion via [CompletableDeferred].
         *
         * The Screen executes the action by calling [run], and the action
         * implementation is responsible for completing [result] when processing has
         * finished. Completion may happen directly inside [run] or asynchronously
         * from a callback started by [run].
         */
        val result: CompletableDeferred<T>
    }

    fun run(webView: WebView)

    /** Navigate forward in WebView history if possible. */
    data class Forward(override val result: CompletableDeferred<Unit> = CompletableDeferred()) : AwaitableAction<Unit> {
        override fun run(webView: WebView) {
            if (webView.canGoForward()) webView.goForward()
            result.complete(Unit)
        }
    }

    /** Reload the current page. */
    data class Reload(override val result: CompletableDeferred<Unit> = CompletableDeferred()) : AwaitableAction<Unit> {
        override fun run(webView: WebView) {
            webView.reload()
            result.complete(Unit)
        }
    }

    /** Perform haptic feedback on the WebView. */
    data class Haptic(val type: HapticType, override val result: CompletableDeferred<Unit> = CompletableDeferred()) :
        AwaitableAction<Unit> {
        override fun run(webView: WebView) {
            HapticFeedbackPerformer.perform(webView, type)
            result.complete(Unit)
        }
    }

    /** Clear the WebView navigation history. */
    data class ClearHistory(override val result: CompletableDeferred<Unit> = CompletableDeferred()) :
        AwaitableAction<Unit> {
        override fun run(webView: WebView) {
            webView.clearHistory()
            result.complete(Unit)
        }
    }

    /**
     * Navigates the frontend to its default panel by clicking the matching sidebar anchor.
     */
    @Deprecated(
        "Legacy fallback for Home Assistant servers older than 2025.6 that lack the `navigate` " +
            "external bus command. Prefer NavigateToMessage on supported servers; remove this once " +
            "the minimum supported server version is 2025.6 or later.",
    )
    data class NavigateToDefaultPanelViaSidebar(
        override val result: CompletableDeferred<Unit> = CompletableDeferred(),
    ) : AwaitableAction<Unit> {
        override fun run(webView: WebView) {
            @OptIn(EvaluateJavascriptUsage::class)
            webView.evaluateJavascript(DEFAULT_PANEL_SIDEBAR_CLICK_SCRIPT) { result.complete(Unit) }
        }
    }

    /**
     * Evaluate a JavaScript script in the WebView, the result of the execution is
     * emitted through [result].
     */
    @EvaluateJavascriptUsage
    data class EvaluateScript(
        val script: String,
        override val result: CompletableDeferred<String?> = CompletableDeferred(),
    ) : AwaitableAction<String?> {
        override fun run(webView: WebView) {
            Timber.d("Evaluating script: ${sensitive(script)}")
            webView.evaluateJavascript(script) { scriptResult ->
                result.complete(scriptResult)
            }
        }
    }

    /**
     * Applies zoom settings to the WebView.
     *
     * Sets the base zoom level via [WebView.setInitialScale] (scaled by device density),
     * enables or disables pinch-to-zoom via [android.webkit.WebSettings.setBuiltInZoomControls],
     * and injects JavaScript to modify the viewport meta tag.
     *
     * @param zoomLevel Zoom level percentage (e.g. 100 for no zoom, 150 for 150%).
     * @param pinchToZoomEnabled Whether the user has enabled pinch-to-zoom.
     */
    data class ApplyZoom(val zoomLevel: Int, val pinchToZoomEnabled: Boolean) : WebViewAction {
        /**
         * JavaScript that adjusts the viewport meta tag for pinch-to-zoom support.
         *
         * When [pinchToZoom] is true, removes `user-scalable`, `minimum-scale`, and `maximum-scale`
         * restrictions and adds `user-scalable=yes`.
         * When false, restores the original viewport content.
         *
         * Idea from https://github.com/home-assistant/iOS/pull/1472
         */
        private fun viewportZoomScript(pinchToZoom: Boolean): String {
            val enabled = if (pinchToZoom) "true" else "false"
            return """
        if (typeof viewport === 'undefined') {
            var viewport = document.querySelector('meta[name="viewport"]');
            if (viewport != null && typeof original_elements === 'undefined') {
                var original_elements = viewport['content'];
            }
        }
        if (viewport != null) {
            if ($enabled) {
                const ignoredBits = ['user-scalable', 'minimum-scale', 'maximum-scale'];
                let elements = viewport['content'].split(',').filter(contentItem => {
                    return ignoredBits.every(ignoredBit => !contentItem.includes(ignoredBit));
                });
                elements.push('user-scalable=yes');
                viewport['content'] = elements.join(',');
            } else {
                viewport['content'] = original_elements;
            }
        }
            """.trimIndent()
        }

        override fun run(webView: WebView) {
            val density = webView.resources.displayMetrics.density
            webView.setInitialScale((density * zoomLevel).toInt())
            webView.settings { builtInZoomControls = pinchToZoomEnabled }
            // Opts into [EvaluateJavascriptUsage] to rewrite the `<meta name="viewport">` tag
            // and toggle pinch-to-zoom. Viewport configuration is a WebView/HTML concern that
            // sits below the frontend, so no external bus message can express it — this script
            // is the only way to adjust these settings at runtime.
            @OptIn(EvaluateJavascriptUsage::class)
            webView.evaluateJavascript(viewportZoomScript(pinchToZoomEnabled)) {}
        }
    }

    /**
     * Opens the more-info dialog for [entityId] by dispatching the frontend's `hass-more-info`
     * DOM event.
     *
     * Fallback for servers older than HA 2025.6, which ignore the `more-info-entity-id` URL query
     * parameter. There is no external bus message to open more-info on those servers, so dispatching
     * the frontend DOM event is the only option.
     */
    data class OpenMoreInfo(val entityId: String) : WebViewAction {
        // [entityId] originates from server/registry data, so it is treated as untrusted: it is
        // JSON-encoded (quotes/backslashes escaped) so it cannot break out of the JS string literal.
        private fun moreInfoScript(entityId: String): String {
            val entityIdJson = Json.encodeToString(entityId)
            return """document.querySelector("home-assistant")""" +
                """.dispatchEvent(new CustomEvent("hass-more-info", { detail: { entityId: $entityIdJson }}))"""
        }

        override fun run(webView: WebView) {
            @OptIn(EvaluateJavascriptUsage::class)
            webView.evaluateJavascript(moreInfoScript(entityId)) {}
        }
    }
}

/** Gates direct JavaScript evaluation in the WebView behind an explicit opt-in. */
@RequiresOptIn(
    message =
    "Evaluating raw JavaScript tightly couples the app to frontend internals and is fragile across frontend changes. " +
        "Prefer collaborating with the frontend team to add a dedicated externalBus message. " +
        "Only opt in as a last resort, and document on the opt-in site why the externalBus is not a viable option so reviewers can challenge the usage.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class EvaluateJavascriptUsage
