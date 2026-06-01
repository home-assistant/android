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
 * Notifies the frontend that an improv-capable BLE device has been discovered while scanning.
 *
 * Emitted once per device — the frontend deduplicates by [name] when adding the entry to its
 * "Add device" list. This is a one-way command; the frontend does not respond.
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
 * Notifies the frontend that the user-selected improv device has finished its Wi-Fi onboarding
 * (BLE state moved to provisioned). The frontend uses this to dismiss its progress UI before
 * the app navigates the WebView to the `config_flow_start` URL for the device's domain.
 *
 * One-way command; no response is expected.
 *
 * @see CommandMessage
 */
val ImprovDeviceSetupDoneMessage: OutgoingExternalBusMessage = CommandMessage(command = "improv/device_setup_done")
