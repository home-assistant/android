package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

sealed interface DynamicEditorData {
    val index: Int
    val draftSeed: ShortcutDraft

    data class Create(override val index: Int, override val draftSeed: ShortcutDraft) : DynamicEditorData

    data class Edit(override val index: Int, override val draftSeed: ShortcutDraft) : DynamicEditorData
}

sealed interface PinnedEditorData {
    val draftSeed: ShortcutDraft

    data class Create(override val draftSeed: ShortcutDraft) : PinnedEditorData

    data class Edit(override val draftSeed: ShortcutDraft) : PinnedEditorData
}
