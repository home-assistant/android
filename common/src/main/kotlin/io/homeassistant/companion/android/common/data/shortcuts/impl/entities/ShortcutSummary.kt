package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import androidx.compose.runtime.Immutable
import com.mikepenz.iconics.typeface.IIcon

/**
 * Snapshot of a shortcut stored on the system.
 *
 * @param id The shortcut ID
 * @param serverId The server ID this shortcut is associated with
 * @param selectedIcon The icon selected for this shortcut
 * @param label The short label for the shortcut
 * @param description The long description for the shortcut
 * @param target The target value for the shortcut
 */
@Immutable
data class ShortcutSummary(
    val id: String,
    val serverId: Int,
    val selectedIcon: IIcon?,
    val label: String,
    val description: String,
    val target: ShortcutTargetValue,
)
