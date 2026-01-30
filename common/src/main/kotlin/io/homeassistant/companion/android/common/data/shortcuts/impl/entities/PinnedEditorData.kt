package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

sealed interface PinnedEditorData {
    val draftSeed: ShortcutDraft

    data class Create(override val draftSeed: ShortcutDraft) : PinnedEditorData

    data class Edit(override val draftSeed: ShortcutDraft) : PinnedEditorData
}
