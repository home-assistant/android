package io.homeassistant.companion.android.settings.shortcuts.data.entities

import androidx.compose.runtime.Immutable

@Immutable
internal data class ShortcutDraft(
    val serverId: Int,
    val icon: ShortcutIcon = ShortcutIcon.Default,
    val label: String,
    val description: String,
    val destination: ShortcutDestination,
) {
    companion object {
        /** Initial editor draft. The default icon selects the app's default shortcut icon. */
        fun initial(serverId: Int) = ShortcutDraft(
            serverId = serverId,
            icon = ShortcutIcon.Default,
            label = "",
            description = "",
            destination = ShortcutDestination.Dashboard(""),
        )
    }
}

/** Converts this [Shortcut] into an editable [ShortcutDraft], keeping its typed icon. */
internal fun Shortcut.toDraft(): ShortcutDraft = ShortcutDraft(
    serverId = serverId,
    icon = icon,
    label = label,
    description = description,
    destination = destination,
)
