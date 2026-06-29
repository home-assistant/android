package io.homeassistant.companion.android.frontend.error

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR

/**
 * A single button shown on the connection-error screen.
 *
 * @property labelRes The button label.
 * @property style How prominently to render the button.
 * @property intent What happens when the button is tapped.
 */
data class ErrorAction(@param:StringRes val labelRes: Int, val style: Style, val intent: ErrorActionIntent) {
    enum class Style { Primary, Secondary }
}

/**
 * Maps a [FrontendConnectionError] to the ordered list of actions the error screen should offer.
 *
 * The first action is the recommended recovery action; the rest are secondary. "Go to Settings"
 * is always offered as the universal escape hatch.
 *
 * @param isInternalConnection whether the active connection is the internal one, used to label the
 *   [ErrorActionIntent.Refresh] action.
 */
fun errorActions(error: FrontendConnectionError, isInternalConnection: Boolean): List<ErrorAction> {
    val settings = ErrorAction(
        labelRes = commonR.string.open_settings,
        style = ErrorAction.Style.Secondary,
        intent = ErrorActionIntent.GoToSettings,
    )

    fun refresh() = ErrorAction(
        labelRes = if (isInternalConnection) commonR.string.refresh_internal else commonR.string.refresh_external,
        style = ErrorAction.Style.Primary,
        intent = ErrorActionIntent.Refresh,
    )

    return when (error) {
        is FrontendConnectionError.TlsCertNotFound,
        is FrontendConnectionError.AuthRevoked,
        -> listOf(
            ErrorAction(
                labelRes = commonR.string.error_action_remove_server,
                style = ErrorAction.Style.Primary,
                intent = ErrorActionIntent.RemoveServerAndRelaunch,
            ),
            settings,
        )

        is FrontendConnectionError.TlsCertExpired -> listOf(
            ErrorAction(
                labelRes = commonR.string.error_action_clear_credentials,
                style = ErrorAction.Style.Primary,
                intent = ErrorActionIntent.ClearKeychainAndRelaunch,
            ),
            settings,
        )

        is FrontendConnectionError.SslError,
        is FrontendConnectionError.Unrecoverable,
        -> listOf(settings.copy(style = ErrorAction.Style.Primary))

        is FrontendConnectionError.ExternalBusTimeout -> listOf(
            refresh(),
            settings,
            ErrorAction(
                labelRes = commonR.string.wait,
                style = ErrorAction.Style.Secondary,
                intent = ErrorActionIntent.Wait,
            ),
        )

        is FrontendConnectionError.Timeout,
        is FrontendConnectionError.Unreachable,
        is FrontendConnectionError.Unknown,
        -> listOf(refresh(), settings)
    }
}
