package io.homeassistant.companion.android.common.data.shortcuts

import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.DynamicEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinnedEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ServersData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData

interface ShortcutsRepository {
    // Server metadata
    suspend fun getServers(): ShortcutResult<ServersData>

    // Shortcuts list (dynamic + pinned)
    suspend fun loadShortcutsList(): ShortcutResult<ShortcutsListData>

    // Editor screen reference data (servers + registries)
    suspend fun loadEditorData(): ShortcutResult<ShortcutEditorData>

    // Dynamic editor entry points
    suspend fun loadDynamicEditor(index: Int): ShortcutResult<DynamicEditorData>

    suspend fun loadDynamicEditorFirstAvailable(): ShortcutResult<DynamicEditorData>

    // Pinned editor entry points
    suspend fun loadPinnedEditor(shortcutId: String): ShortcutResult<PinnedEditorData>

    suspend fun loadPinnedEditorForCreate(): ShortcutResult<PinnedEditorData>

    // Dynamic mutations
    suspend fun upsertDynamicShortcut(
        index: Int,
        shortcut: ShortcutDraft,
        isEditing: Boolean,
    ): ShortcutResult<DynamicEditorData>

    suspend fun deleteDynamicShortcut(index: Int): ShortcutResult<Unit>

    // Pinned mutations
    suspend fun upsertPinnedShortcut(shortcut: ShortcutDraft): ShortcutResult<PinResult>

    suspend fun deletePinnedShortcut(shortcutId: String): ShortcutResult<Unit>
}
