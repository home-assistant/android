package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import androidx.compose.runtime.Immutable
import com.mikepenz.iconics.typeface.IIcon

/**
 * Draft values for a shortcut editor.
 *
 * @param id The shortcut ID (generated for dynamic shortcuts, user-defined for pinned shortcuts)
 * @param serverId The server ID this shortcut is associated with
 * @param selectedIcon The icon selected for this shortcut
 * @param label The short label for the shortcut
 * @param description The long description for the shortcut
 * @param target The target value for the shortcut
 * @param isDirty Whether the user has edited the draft
 */
@Immutable
data class ShortcutDraft(
    val id: String,
    val serverId: Int,
    val selectedIcon: IIcon?,
    val label: String,
    val description: String,
    val target: ShortcutTargetValue,
    val isDirty: Boolean = false,
) {
    companion object
}

fun ShortcutDraft.Companion.empty(id: String): ShortcutDraft {
    return ShortcutDraft(
        id = id,
        serverId = 0,
        selectedIcon = null,
        label = "",
        description = "",
        target = ShortcutTargetValue.Lovelace(""),
        isDirty = false,
    )
}

fun ShortcutDraft.toSummary(isCreated: Boolean): ShortcutSummary {
    return ShortcutSummary(
        id = id,
        serverId = serverId,
        selectedIcon = selectedIcon,
        label = label,
        description = description,
        target = target,
        isCreated = isCreated,
    )
}
