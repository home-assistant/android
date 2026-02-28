package io.homeassistant.companion.android.settings.shortcuts.v2.views.screens

import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft

sealed interface ShortcutEditAction {
    data class Submit(val draft: ShortcutDraft) : ShortcutEditAction
    data object Delete : ShortcutEditAction
}
