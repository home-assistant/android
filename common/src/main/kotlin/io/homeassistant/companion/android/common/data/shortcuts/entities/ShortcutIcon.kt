package io.homeassistant.companion.android.common.data.shortcuts.entities

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ShortcutIcon {
    data class Mdi(val name: String) : ShortcutIcon

    object Default : ShortcutIcon

    companion object {
        /**
         * Converts a persisted icon name to a [ShortcutIcon]. A blank or null [iconName] maps to
         * [Default]; otherwise it is treated as an MDI icon name.
         */
        fun fromIconName(iconName: String?): ShortcutIcon = iconName?.takeUnless(String::isBlank)?.let(::Mdi) ?: Default
    }
}
