package io.homeassistant.companion.android.frontend

import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.toColorInt
import io.homeassistant.companion.android.frontend.WebViewAction.ReadThemeColors.Companion.THEME_COLORS_SCRIPT
import io.homeassistant.companion.android.frontend.WebViewAction.ReadThemeColors.Companion.THEME_COLOR_SPACER
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.homeassistant.companion.android.frontend.haptic.HapticFeedbackPerformer
import io.homeassistant.companion.android.util.compose.webview.settings
import io.homeassistant.companion.android.util.sensitive
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
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
 * the corresponding WebView method. Actions whose outcome matters to the emitter
 * extend [AwaitableAction].
 */
sealed interface WebViewAction {

    /**
     * A [WebViewAction] whose outcome can be awaited by its emitter.
     *
     * The emitter executes the action by calling [run], and observes the outcome through [await].
     */
    sealed class AwaitableAction<T> : WebViewAction {
        /** Completed by [run], or a callback it starts, with the action's outcome. */
        @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
        internal val result: CompletableDeferred<T> = CompletableDeferred()

        /**
         * Suspends until the action has been executed and returns its outcome. An implementation
         * that fails throws instead, e.g. [PingUrl] throws
         * [kotlinx.coroutines.TimeoutCancellationException] on timeout.
         *
         * An implementation that gives up cancels [result]; calling [await] again after that
         * throws a [kotlinx.coroutines.CancellationException], so await an action at most once.
         */
        open suspend fun await(): T {
            return result.await()
        }
    }

    fun run(webView: WebView)

    /** Navigate forward in WebView history if possible. */
    class Forward : AwaitableAction<Unit>() {
        override fun run(webView: WebView) {
            if (webView.canGoForward()) webView.goForward()
            result.complete(Unit)
        }
    }

    /** Reload the current page. */
    class Reload : AwaitableAction<Unit>() {
        override fun run(webView: WebView) {
            webView.reload()
            result.complete(Unit)
        }
    }

    /** Perform haptic feedback on the WebView. */
    data class Haptic(val type: HapticType) : AwaitableAction<Unit>() {
        override fun run(webView: WebView) {
            HapticFeedbackPerformer.perform(webView, type)
            result.complete(Unit)
        }
    }

    /** Clear the WebView navigation history. */
    class ClearHistory : AwaitableAction<Unit>() {
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
    class NavigateToDefaultPanelViaSidebar : AwaitableAction<Unit>() {
        override fun run(webView: WebView) {
            @OptIn(EvaluateJavascriptUsage::class)
            webView.evaluateJavascript(DEFAULT_PANEL_SIDEBAR_CLICK_SCRIPT) { result.complete(Unit) }
        }
    }

    /**
     * Pings [url] through the WebView network stack: a `HEAD` request that bypasses the HTTP
     * cache and discards the response. Use this when reaching the server over the network is the
     * goal.
     *
     * [await] returns once the request has finished, successfully or not: the response does not
     * matter, only that the server was reached. After [PING_TIMEOUT] it stops the polling and
     * throws [kotlinx.coroutines.TimeoutCancellationException], letting the emitter decide how
     * to handle the failure.
     */
    // Opts into [EvaluateJavascriptUsage] because the request must go through the WebView's own
    // network stack, and callers may run before the frontend is loaded, when no external bus
    // exists.
    @OptIn(EvaluateJavascriptUsage::class)
    data class PingUrl(val url: String) : AwaitableAction<Unit>() {

        /**
         * Per-action `window` variable set by the ping script once the request has completed, so
         * a superseded ping completing late cannot overwrite this ping's completion.
         */
        private val completedFlag = "$PING_COMPLETED_FLAG_PREFIX${NEXT_PING_ID.getAndIncrement()}"

        override fun run(webView: WebView) {
            val urlJson = Json.encodeToString(url)

            // The completion variable holds the pinged URL rather than a boolean so a stale completion
            // from a previous ping cannot be mistaken for this one.
            val script = """
                window.$completedFlag = null;
                fetch($urlJson, { method: 'HEAD', cache: 'no-store', mode: 'no-cors' })
                    .finally(function() { window.$completedFlag = $urlJson; });
            """.trimIndent()
            webView.evaluateJavascript(script) { pollCompletion(webView, urlJson) }
        }

        override suspend fun await() {
            try {
                val elapsed = withTimeout(PING_TIMEOUT) { measureTime { result.await() } }
                Timber.d("Ping of ${sensitive(url)} completed in $elapsed")
            } finally {
                // Stops the action's polling after a timeout or when this load gets superseded.
                result.cancel()
            }
        }

        private fun pollCompletion(webView: WebView, urlJson: String) {
            if (!result.isActive) return
            webView.evaluateJavascript("window.$completedFlag") { value ->
                if (value == urlJson) {
                    result.complete(Unit)
                } else {
                    // The variable [completedFlag] is polled every [PING_POLL_INTERVAL] because there
                    // is no completion callback available: a synchronous XHR
                    // would block the renderer's JS thread, which the timeout cannot unblock.
                    webView.postDelayed(
                        { pollCompletion(webView, urlJson) },
                        PING_POLL_INTERVAL.inWholeMilliseconds,
                    )
                }
            }
        }

        companion object {
            /** Maximum time [await] waits for the request to finish before giving up. */
            internal val PING_TIMEOUT = 5.seconds

            /** Interval between checks of [completedFlag], also the detection lag after completion. */
            private val PING_POLL_INTERVAL = 15.milliseconds

            /** Prefix of the per-action completion variable, see [completedFlag]. */
            private const val PING_COMPLETED_FLAG_PREFIX = "_haAndroidPingedUrl"

            /** Distinguishes each ping's [completedFlag] within the process. */
            private val NEXT_PING_ID = AtomicInteger()
        }
    }

    /**
     * Evaluate a JavaScript script in the WebView, the result of the execution is
     * returned by [await].
     */
    @EvaluateJavascriptUsage
    data class EvaluateScript(val script: String) : AwaitableAction<String?>() {
        override fun run(webView: WebView) {
            Timber.d("Evaluating script: ${sensitive(script)}")
            webView.evaluateJavascript(script) { scriptResult ->
                result.complete(scriptResult)
            }
        }
    }

    /**
     * Reads the frontend's current theme colors (status bar and page background); [await] returns
     * the parsed [ThemeColors], or `null` when the frontend response is unreadable.
     */
    class ReadThemeColors : AwaitableAction<ReadThemeColors.Companion.ThemeColors?>() {
        /**
         * Opts into [EvaluateJavascriptUsage] because these values only exist as computed CSS custom
         * properties in the frontend; no external bus message exposes them.
         */
        override fun run(webView: WebView) {
            @OptIn(EvaluateJavascriptUsage::class)
            webView.evaluateJavascript(THEME_COLORS_SCRIPT) { raw ->
                result.complete(parse(raw))
            }
        }

        companion object {
            /**
             * The frontend theme colors applied to the system chrome: [statusBarColor] from
             * `--app-header-background-color` and [backgroundColor] from `--primary-background-color`. A `null`
             * field means the corresponding token could not be parsed.
             */
            data class ThemeColors(val statusBarColor: Color?, val backgroundColor: Color?)

            /** Separator used to join the two theme color tokens read from the frontend into a single string. */
            private const val THEME_COLOR_SPACER = "-SPACER-"

            /** Reads the computed value of the CSS custom property [property] from the document root. */
            private fun computedStyleToken(property: String) =
                "document.getElementsByTagName('html')[0].computedStyleMap().get('$property')[0]"

            /**
             * Reads the frontend theme tokens for the status bar (`--app-header-background-color`) and the
             * page background (`--primary-background-color`) as a single string joined by [THEME_COLOR_SPACER].
             */
            private val THEME_COLORS_SCRIPT =
                "[${computedStyleToken(
                    "--app-header-background-color",
                )},${computedStyleToken("--primary-background-color")}].join('$THEME_COLOR_SPACER')"

            /** Matches the CSS `rgb(r, g, b)` notation the frontend emits for its computed theme tokens. */
            private val RGB_REGEX = Regex("""rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""")

            /**
             * Parses the [THEME_COLORS_SCRIPT] result into [ThemeColors]. Returns `null` when [raw]
             * is absent or does not contain exactly two tokens.
             */
            private fun parse(raw: String?): ThemeColors? {
                val tokens = raw?.trim('"')?.split(THEME_COLOR_SPACER)
                if (tokens?.size != 2) return null
                return ThemeColors(
                    statusBarColor = tokens[0].trim().toWebViewColorOrNull(),
                    backgroundColor = tokens[1].trim().toWebViewColorOrNull(),
                )
            }

            /**
             * Parses a color read from the frontend into a Compose [Color]. Returns `null` when
             * - the value is not a valid `rgb()` triple in the 0-255 range
             * - the value is not a valid hex color
             * - the value is not a supported color name like `red`, `blue`, `fuchsia`, ...
             */
            private fun String.toWebViewColorOrNull(): Color? {
                val match = RGB_REGEX.matchEntire(trim())
                return if (match != null) {
                    val (r, g, b) = match.destructured
                    val red = r.toColorChannelOrNull() ?: return null
                    val green = g.toColorChannelOrNull() ?: return null
                    val blue = b.toColorChannelOrNull() ?: return null
                    Color(red = red, green = green, blue = blue)
                } else {
                    try {
                        val asInt = trim().toColorInt()
                        Color(red = asInt.red, green = asInt.green, blue = asInt.blue)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
            }

            /** Parses a 0-255 color channel, returning `null` when out of range. */
            private fun String.toColorChannelOrNull(): Int? = toIntOrNull()?.takeIf { it in 0..255 }
        }
    }

    /**
     * Applies zoom settings to the WebView.
     *
     * Sets the base zoom level via [WebView.setInitialScale] (scaled by device density),
     * enables or disables pinch-to-zoom via [android.webkit.WebSettings.setSupportZoom] and
     * [android.webkit.WebSettings.setBuiltInZoomControls], and injects JavaScript to modify
     * the viewport meta tag.
     *
     * Both [android.webkit.WebSettings.setSupportZoom] and
     * [android.webkit.WebSettings.setBuiltInZoomControls] must be toggled together: when a
     * custom initial scale is applied via [WebView.setInitialScale], leaving
     * [android.webkit.WebSettings.setSupportZoom] at its default of `true` lets WebView
     * keep accepting pinch gestures to zoom with a small range around the initial scale,
     * even though the built-in controls are disabled.
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
            let overrideZoom = $enabled;
            if (overrideZoom) {
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
            webView.settings {
                setSupportZoom(pinchToZoomEnabled)
                builtInZoomControls = pinchToZoomEnabled
            }
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

    /**
     * Publishes the device safe-area [insets] to the frontend as `--app-safe-area-inset-*` CSS
     * custom properties so it can lay its content out edge-to-edge.
     */
    data class ApplySafeAreaInsets(val insets: SafeAreaInsets) : WebViewAction {
        /**
         * Opts into [EvaluateJavascriptUsage] because the safe area must be set directly on the
         * document root as early as possible, even before the frontend is ready to receive external
         * bus messages; no external bus message exposes it.
         */
        override fun run(webView: WebView) {
            @OptIn(EvaluateJavascriptUsage::class)
            webView.evaluateJavascript(insets.toCssPropertiesScript(), null)
        }

        companion object {
            /** Device safe-area insets in density-independent pixels, as reported to the frontend. */
            data class SafeAreaInsets(val top: Float, val bottom: Float, val left: Float, val right: Float)

            private fun SafeAreaInsets.toCssPropertiesScript(): String = """
                document.documentElement.style.setProperty('--app-safe-area-inset-top', '${top}px');
                document.documentElement.style.setProperty('--app-safe-area-inset-bottom', '${bottom}px');
                document.documentElement.style.setProperty('--app-safe-area-inset-left', '${left}px');
                document.documentElement.style.setProperty('--app-safe-area-inset-right', '${right}px');
            """.trimIndent()
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
