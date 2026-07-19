package io.homeassistant.companion.android.frontend

import android.webkit.WebView
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
     * Reads the frontend's current theme colors (status bar and page background) and completes
     * [result] with the parsed [ThemeColors], or `null` when the frontend response is unreadable.
     */
    data class ReadThemeColors(override val result: CompletableDeferred<ThemeColors?> = CompletableDeferred()) :
        AwaitableAction<ReadThemeColors.Companion.ThemeColors?> {
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
