package io.homeassistant.companion.android.common.data.shortcuts.entities

import androidx.compose.runtime.Immutable

@Immutable
data class Shortcut(
    val id: String,
    val serverId: Int,
    val icon: ShortcutIcon = ShortcutIcon.Default,
    val label: String,
    val description: String,
    val destination: ShortcutDestination,
)
