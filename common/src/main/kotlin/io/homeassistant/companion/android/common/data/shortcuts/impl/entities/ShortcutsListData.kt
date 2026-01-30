package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

data class ShortcutsListData(
    val dynamic: DynamicShortcutsData,
    val pinned: List<ShortcutDraft>,
    val pinnedError: ShortcutRepositoryError? = null,
)
