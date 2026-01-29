package io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens

import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft

sealed interface ShortcutEditAction {
    data class Submit(val draft: ShortcutDraft) : ShortcutEditAction
    data class Delete(val draftId: String) : ShortcutEditAction
}
