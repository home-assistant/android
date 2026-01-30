package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import androidx.compose.runtime.Immutable

private const val DYNAMIC_DRAFT_SEED_PREFIX = "dynamic_draft"
private fun dynamicDraftSeedId(index: Int): String {
    return "${DYNAMIC_DRAFT_SEED_PREFIX}_${index + 1}"
}

/**
 * Draft values for a shortcut editor.
 *
 * @param id The shortcut ID (generated for dynamic shortcuts, user-defined for pinned shortcuts)
 * @param serverId The server ID this shortcut is associated with
 * @param selectedIconName The MDI icon identifier selected for this shortcut (ex: "mdi:account-alert")
 * @param label The short label for the shortcut
 * @param description The long description for the shortcut
 * @param target The target value for the shortcut
 */
@Immutable
data class ShortcutDraft(
    val id: String,
    val serverId: Int,
    val selectedIconName: String?,
    val label: String,
    val description: String,
    val target: ShortcutTargetValue,
) {
    companion object
}

fun ShortcutDraft.Companion.empty(id: String): ShortcutDraft {
    return ShortcutDraft(
        id = id,
        serverId = 0,
        selectedIconName = null,
        label = "",
        description = "",
        target = ShortcutTargetValue.Lovelace(""),
    )
}

fun ShortcutDraft.Companion.empty(index: Int): ShortcutDraft {
    return empty(dynamicDraftSeedId(index))
}

fun ShortcutDraft.toSummary(): ShortcutSummary {
    return ShortcutSummary(
        id = id,
        selectedIconName = selectedIconName,
        label = label,
    )
}
