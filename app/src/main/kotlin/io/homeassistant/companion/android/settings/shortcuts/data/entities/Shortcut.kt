package io.homeassistant.companion.android.settings.shortcuts.data.entities

import androidx.compose.runtime.Immutable

@Immutable
internal data class Shortcut(
    val id: String,
    val serverId: Int,
    val icon: ShortcutIcon = ShortcutIcon.Default,
    val label: String,
    val description: String,
    val destination: ShortcutDestination,
)
