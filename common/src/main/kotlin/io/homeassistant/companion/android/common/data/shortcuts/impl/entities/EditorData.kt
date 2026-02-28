package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

sealed interface AppEditorData {
    val index: Int
    val draftSeed: ShortcutDraft

    data class Create(override val index: Int, override val draftSeed: ShortcutDraft) : AppEditorData

    data class Edit(override val index: Int, override val draftSeed: ShortcutDraft) : AppEditorData
}

sealed interface HomeEditorData {
    val draftSeed: ShortcutDraft

    data class Create(override val draftSeed: ShortcutDraft) : HomeEditorData

    data class Edit(override val draftSeed: ShortcutDraft) : HomeEditorData
}
