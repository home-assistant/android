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
) {
    companion object
}

private const val DYNAMIC_DRAFT_SEED_PREFIX = "dynamic_draft"

fun ShortcutDraft.Companion.empty(id: String): ShortcutDraft {
    return ShortcutDraft(
        id = id,
        serverId = 0,
        selectedIcon = null,
        label = "",
        description = "",
        target = ShortcutTargetValue.Lovelace(""),
    )
}

fun ShortcutDraft.Companion.empty(index: Int): ShortcutDraft {
    return empty(dynamicDraftSeedId(index))
}

private fun dynamicDraftSeedId(index: Int): String {
    return "${DYNAMIC_DRAFT_SEED_PREFIX}_${index + 1}"
}

fun ShortcutDraft.toSummary(): ShortcutSummary {
    return ShortcutSummary(
        id = id,
        selectedIcon = selectedIcon,
        label = label,
    )
}
