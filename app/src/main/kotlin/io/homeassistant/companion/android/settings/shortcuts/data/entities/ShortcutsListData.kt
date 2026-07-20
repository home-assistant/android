package io.homeassistant.companion.android.settings.shortcuts.data.entities

import androidx.compose.runtime.Immutable

@Immutable
internal data class ShortcutsListData(val appShortcuts: AppShortcuts, val homeShortcuts: HomeShortcuts)

@Immutable
internal data class AppShortcuts(val items: List<ShortcutListItem>, val maxAppShortcuts: Int)

@Immutable
internal data class HomeShortcuts(val items: List<HomeShortcutListItem>, val canPinShortcuts: Boolean)

@Immutable
internal data class ShortcutListItem(val id: String, val label: String, val icon: ShortcutIcon = ShortcutIcon.Default)

@Immutable
internal data class HomeShortcutListItem(val shortcut: ShortcutListItem, val isEnabled: Boolean)
