package io.homeassistant.companion.android.frontend.externalbus.outgoing

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Outgoing command message sent to the Home Assistant frontend via the external bus.
 *
 * Command messages instruct the frontend to perform an action.
 *
 * The Home Assistant frontend uses `"type": "command"` for all commands, distinguishing them
 * only by the `command` string field. This means kotlinx.serialization's sealed polymorphism
 * cannot give each command its own subtype (all would need the same `@SerialName("command")`).
 *
 * Instead, this single private class handles serialization, and each command is exposed as a
 * top-level factory: [NavigateToMessage] uses `operator fun invoke` for constructor-like syntax,
 * and [ShowSidebarMessage] is a pre-built instance. Call sites read naturally:
 * ```
 * send(NavigateTo(path = "/dashboard", replace = true))
 * send(ShowSidebar)
 * ```
 */
@Serializable
@SerialName("command")
private data class CommandMessage(
    override val id: Int? = null,
    val command: String,
    val payload: JsonElement? = null,
) : OutgoingExternalBusMessage

/**
 * Creates a navigation command to navigate the frontend to the given path.
 *
 * When [replace] is `true`, the current history entry is replaced instead of
 * pushing a new one (useful for resetting to the default dashboard).
 *
 * Requires Home Assistant 2025.6 or later. Callers must check the server version
 * before sending this command.
 *
 * @see CommandMessage
 */
object NavigateToMessage {
    operator fun invoke(path: String, replace: Boolean = false): OutgoingExternalBusMessage = CommandMessage(
        command = "navigate",
        payload = frontendExternalBusJson.encodeToJsonElement(
            NavigatePayload(path = path, options = NavigateOptions(replace = replace)),
        ),
    )

    fun isAvailable(homeAssistantVersion: HomeAssistantVersion?): Boolean {
        return homeAssistantVersion?.isAtLeast(2025, 6, 0) == true
    }

    @Serializable
    private data class NavigatePayload(val path: String, val options: NavigateOptions = NavigateOptions())

    @Serializable
    private data class NavigateOptions(val replace: Boolean = false)
}

/**
 * Command to toggle the frontend sidebar visibility.
 *
 * @see CommandMessage
 */
val ShowSidebarMessage: OutgoingExternalBusMessage = CommandMessage(command = "sidebar/show")

/**
 * Reports an Improv-capable BLE device to the frontend by its advertised [name].
 *
 * This is a one-way command; the frontend does not respond.
 *
 * @see CommandMessage
 */
object ImprovDiscoveredDeviceMessage {
    operator fun invoke(name: String): OutgoingExternalBusMessage = CommandMessage(
        command = "improv/discovered_device",
        payload = frontendExternalBusJson.encodeToJsonElement(DiscoveredDevicePayload(name = name)),
    )

    @Serializable
    private data class DiscoveredDevicePayload(val name: String)
}

/**
 * Notifies the frontend that the user-selected Improv device has finished its Wi-Fi onboarding.
 *
 * This is a one-way command; the frontend does not respond.
 *
 * @see CommandMessage
 */
val ImprovDeviceSetupDoneMessage: OutgoingExternalBusMessage = CommandMessage(command = "improv/device_setup_done")

/**
 * Notifies the frontend that the user scanned a code.
 *
 * Sent in response to a [io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeScanMessage].
 * The frontend correlates by [id] back to its original request.
 *
 * @param id The id of the originating [io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeScanMessage]
 * @param rawValue The decoded barcode contents, verbatim
 * @param format The decoded format name, lowercased — `qr_code`, `code_128`, `pdf417`, etc., or
 *   `unknown` for formats the frontend does not recognise.
 *
 * @see CommandMessage
 */
object BarcodeScanResultMessage {
    operator fun invoke(id: Int, rawValue: String, format: String): OutgoingExternalBusMessage = CommandMessage(
        id = id,
        command = "bar_code/scan_result",
        payload = frontendExternalBusJson.encodeToJsonElement(
            ScanResultPayload(rawValue = rawValue, format = format),
        ),
    )

    @Serializable
    private data class ScanResultPayload(val rawValue: String, val format: String)
}

/**
 * Notifies the frontend that the user closed the scanner without producing a result.
 *
 * Sent in response to a [io.homeassistant.companion.android.frontend.externalbus.incoming.BarcodeScanMessage]
 * when the user cancels.
 *
 * @see CommandMessage
 */
object BarcodeScanAbortedMessage {
    operator fun invoke(id: Int, forAction: Boolean): OutgoingExternalBusMessage = CommandMessage(
        id = id,
        command = "bar_code/aborted",
        payload = frontendExternalBusJson.encodeToJsonElement(
            ScanAbortedPayload(reason = if (forAction) REASON_ALTERNATIVE_OPTIONS else REASON_CANCELED),
        ),
    )

    private const val REASON_ALTERNATIVE_OPTIONS = "alternative_options"
    private const val REASON_CANCELED = "canceled"

    @Serializable
    private data class ScanAbortedPayload(val reason: String)
}
