package io.homeassistant.companion.android.common.data.shortcuts

import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinnedEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutRepositoryResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData

interface ShortcutsRepository {
    // Server metadata
    suspend fun getServers(): ShortcutRepositoryResult<ServersData>

    // Shortcuts list (dynamic + pinned)
    suspend fun loadShortcutsList(): ShortcutRepositoryResult<ShortcutsListData>

    // Editor screen reference data (servers + registries)
    suspend fun loadEditorData(): ShortcutRepositoryResult<ShortcutEditorData>

    // Dynamic editor entry points
    suspend fun loadDynamicEditor(index: Int): ShortcutRepositoryResult<DynamicEditorData>

    suspend fun loadDynamicEditorFirstAvailable(): ShortcutRepositoryResult<DynamicEditorData>

    // Pinned editor entry points
    suspend fun loadPinnedEditor(shortcutId: String): ShortcutRepositoryResult<PinnedEditorData>

    suspend fun loadPinnedEditorForCreate(): ShortcutRepositoryResult<PinnedEditorData>

    // Dynamic mutations
    suspend fun upsertDynamicShortcut(
        index: Int,
        shortcut: ShortcutDraft,
        isEditing: Boolean,
    ): ShortcutRepositoryResult<DynamicEditorData>

    suspend fun deleteDynamicShortcut(index: Int): ShortcutRepositoryResult<Unit>

    // Pinned mutations
    suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutRepositoryResult<PinResult>

    suspend fun deletePinnedShortcut(shortcutId: String): ShortcutRepositoryResult<Unit>
}
