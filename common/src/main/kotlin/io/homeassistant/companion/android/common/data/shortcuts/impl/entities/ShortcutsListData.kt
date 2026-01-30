package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

data class ShortcutsListData(
    val dynamic: DynamicShortcutsData,
    val pinned: List<ShortcutSummary>,
    val pinnedError: ShortcutError? = null,
)
