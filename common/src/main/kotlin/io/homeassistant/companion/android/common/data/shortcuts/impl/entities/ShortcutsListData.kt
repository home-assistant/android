package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import androidx.compose.runtime.Immutable

data class ShortcutsListData(
    val appShortcuts: AppShortcutsData,
    val homeShortcuts: List<ShortcutSummary>,
    val homeShortcutsError: ShortcutError? = null,
)

data class AppShortcutsData(val maxAppShortcuts: Int, val shortcuts: Map<Int, ShortcutDraft>) {
    val orderedShortcuts: List<Map.Entry<Int, ShortcutDraft>>
        get() = shortcuts.entries.sortedBy { it.key }
}

/**
 * Snapshot of a shortcut stored on the system.
 *
 * @param id The shortcut ID
 * @param selectedIconName The MDI icon identifier selected for this shortcut (ex: "mdi:account-alert")
 * @param label The short label for the shortcut
 */
@Immutable
data class ShortcutSummary(val id: String, val selectedIconName: String?, val label: String)
