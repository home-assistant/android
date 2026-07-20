package io.homeassistant.companion.android.common.data.shortcuts.entities

import androidx.compose.runtime.Immutable

@Immutable
data class ShortcutsListData(val appShortcuts: AppShortcuts, val homeShortcuts: HomeShortcuts)

@Immutable
data class AppShortcuts(val items: List<ShortcutListItem>, val maxAppShortcuts: Int)

@Immutable
data class HomeShortcuts(val items: List<HomeShortcutListItem>, val canPinShortcuts: Boolean)

@Immutable
data class ShortcutListItem(val id: String, val label: String, val icon: ShortcutIcon = ShortcutIcon.Default)

@Immutable
data class HomeShortcutListItem(val shortcut: ShortcutListItem, val isEnabled: Boolean)
