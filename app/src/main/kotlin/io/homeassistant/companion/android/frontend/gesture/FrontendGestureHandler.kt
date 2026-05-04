package io.homeassistant.companion.android.frontend.gesture

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.NavigateToMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ShowSidebarMessage
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import javax.inject.Inject
import timber.log.Timber

/**
 * Resolves swipe gestures to actions and dispatches them.
 *
 * Reads the user's gesture preferences via [PrefsRepository], maps the detected
 * [GestureDirection] and pointer count to an [HAGesture], resolves the configured
 * [GestureAction], and executes it. Actions that send commands to the frontend
 * go through [FrontendExternalBusRepository].
 */
@ViewModelScoped
class FrontendGestureHandler @Inject constructor(
    private val prefsRepository: PrefsRepository,
    private val externalBusRepository: FrontendExternalBusRepository,
    private val serverManager: ServerManager,
) {

    /**
     * Handles a detected swipe gesture by resolving the configured action and executing it.
     *
     * @param serverId The active server ID, used for version checks
     * @param direction The swipe direction
     * @param pointerCount Number of pointers in the gesture
     * @return [GestureResult.Forwarded] if the action was executed, [GestureResult.Ignored] otherwise
     */
    suspend fun handleGesture(serverId: Int, direction: GestureDirection, pointerCount: Int): GestureResult {
        val gesture = HAGesture.fromSwipeListener(direction, pointerCount)
        if (gesture == null) {
            Timber.d("No gesture mapping for direction=$direction, pointerCount=$pointerCount")
            return GestureResult.Ignored
        }

        val action = prefsRepository.getGestureAction(gesture)
        Timber.d("Gesture $gesture resolved to action $action")

        return when (action) {
            GestureAction.NONE -> GestureResult.Ignored
            GestureAction.SHOW_SIDEBAR -> {
                externalBusRepository.send(ShowSidebarMessage)
                GestureResult.Forwarded
            }
            GestureAction.NAVIGATE_DASHBOARD -> navigateToDashboard(serverId)
            GestureAction.QUICKBAR_DEFAULT -> openQuickBarDefault(serverId)
            GestureAction.QUICKBAR_ENTITIES -> {
                dispatchKeyDown(key = "e", code = "KeyE", keyCode = 69)
                GestureResult.Forwarded
            }
            GestureAction.QUICKBAR_DEVICES -> {
                dispatchKeyDown(key = "d", code = "KeyD", keyCode = 68)
                GestureResult.Forwarded
            }
            GestureAction.QUICKBAR_COMMANDS -> {
                dispatchKeyDown(key = "c", code = "KeyC", keyCode = 67)
                GestureResult.Forwarded
            }
            GestureAction.OPEN_ASSIST -> GestureResult.Navigate(
                FrontendEvent.NavigateToAssist(
                    serverId = serverId,
                    pipelineId = null,
                    startListening = true,
                ),
            )
            GestureAction.OPEN_APP_SETTINGS -> GestureResult.Navigate(FrontendEvent.NavigateToSettings)
            GestureAction.OPEN_APP_DEVELOPER -> GestureResult.Navigate(
                FrontendEvent.NavigateToDeveloperSettings,
            )
            GestureAction.NAVIGATE_FORWARD -> GestureResult.PerformWebViewAction(WebViewAction.Forward())
            GestureAction.NAVIGATE_RELOAD -> GestureResult.PerformWebViewAction(WebViewAction.Reload())
            GestureAction.SERVER_LIST -> GestureResult.Navigate(FrontendEvent.ShowServerSwitcher)
            GestureAction.SERVER_NEXT -> switchServerBy(currentServerId = serverId, offset = 1)
            GestureAction.SERVER_PREVIOUS -> switchServerBy(currentServerId = serverId, offset = -1)
        }
    }

    /**
     * Opens the unified quick bar (Ctrl+K) on HA 2026.2+, or falls back to the entities quick bar.
     */
    private suspend fun openQuickBarDefault(serverId: Int): GestureResult {
        val version = serverManager.getServer(serverId)?.version
        if (version?.isAtLeast(2026, 2) == true) {
            dispatchKeyDown(key = "k", code = "KeyK", keyCode = 75, ctrlKey = true)
        } else {
            dispatchKeyDown(key = "e", code = "KeyE", keyCode = 69)
        }
        return GestureResult.Forwarded
    }

    /**
     * Dispatches a synthetic KeyboardEvent to the frontend document root.
     *
     * This bypasses the focused element to avoid interfering with text inputs,
     * triggering frontend keyboard shortcuts directly on the document.
     *
     * Opts into [EvaluateJavascriptUsage] because the goal is to simulate a keyboard input,
     * which is an interaction the frontend already handles through its normal DOM event
     * listeners — no frontend-specific internals are being poked. A dedicated externalBus
     * event could have been introduced to replace this, but the integration predates the
     * [EvaluateJavascriptUsage] policy and is kept as-is for backward compatibility with older
     * frontend versions that do not expose such a message type.
     */
    @OptIn(EvaluateJavascriptUsage::class)
    private suspend fun dispatchKeyDown(key: String, code: String, keyCode: Int, ctrlKey: Boolean = false) {
        val script = """
            var event = new KeyboardEvent('keydown', {
                key: '$key',
                code: '$code',
                keyCode: $keyCode,
                which: $keyCode,
                ctrlKey: $ctrlKey,
                bubbles: true,
                cancelable: true
            });
            document.dispatchEvent(event);
        """.trimIndent()
        externalBusRepository.evaluateScript(script)
    }

    /**
     * Resolves the neighboring server in the user-defined order and returns a [GestureResult.SwitchServer]
     * for the ViewModel to execute.
     *
     * Wraps around: next on the last server goes to the first, previous on the first goes to the last.
     * Returns [GestureResult.Ignored] when there are fewer than two servers or the current server is
     * not in the list.
     *
     * @param currentServerId ID of the currently active server
     * @param offset `+1` for next, `-1` for previous
     */
    private suspend fun switchServerBy(currentServerId: Int, offset: Int): GestureResult {
        val servers = serverManager.servers()
        if (servers.size < 2) return GestureResult.Ignored
        val currentIndex = servers.indexOfFirst { it.id == currentServerId }
        if (currentIndex == -1) return GestureResult.Ignored
        val nextIndex = (currentIndex + offset).mod(servers.size)
        return GestureResult.SwitchServer(servers[nextIndex].id)
    }

    private suspend fun navigateToDashboard(serverId: Int): GestureResult {
        val version = serverManager.getServer(serverId)?.version
        if (!NavigateToMessage.isAvailable(version)) {
            Timber.w(
                "Server version $version does not support navigate command, requires 2025.6+",
            )
            return GestureResult.Ignored
        }
        return GestureResult.PerformWebViewActionThen(
            action = WebViewAction.ClearHistory(),
            then = {
                externalBusRepository.send(NavigateToMessage(path = "/", replace = true))
                GestureResult.Forwarded
            },
        )
    }
}
