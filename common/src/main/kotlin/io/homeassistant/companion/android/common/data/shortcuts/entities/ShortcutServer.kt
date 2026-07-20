package io.homeassistant.companion.android.common.data.shortcuts.entities

import androidx.compose.runtime.Immutable

@Immutable
data class ShortcutServer(val id: Int, val name: String, val supportsEntity: Boolean)
