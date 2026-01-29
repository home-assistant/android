package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ShortcutTargetValue {
    @Immutable
    data class Lovelace(val path: String) : ShortcutTargetValue

    @Immutable
    data class Entity(val entityId: String) : ShortcutTargetValue
}

fun ShortcutTargetValue.toShortcutType(): ShortcutType {
    return when (this) {
        is ShortcutTargetValue.Lovelace -> ShortcutType.LOVELACE
        is ShortcutTargetValue.Entity -> ShortcutType.ENTITY_ID
    }
}

