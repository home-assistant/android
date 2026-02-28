package io.homeassistant.companion.android.common.data.shortcuts

import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.AppEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.HomeEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutEditorData
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutsListData

interface ShortcutsRepository {
    // Shortcuts list (app + home)
    suspend fun loadShortcutsList(): ShortcutResult<ShortcutsListData>

    // Editor screen reference data (servers + registries)
    suspend fun loadEditorData(): ShortcutResult<ShortcutEditorData>

    // App editor entry points
    suspend fun loadAppEditor(index: Int): ShortcutResult<AppEditorData>

    suspend fun loadAppEditorFirstAvailable(): ShortcutResult<AppEditorData>

    // Home editor entry points
    suspend fun loadHomeEditor(shortcutId: String): ShortcutResult<HomeEditorData>

    suspend fun loadHomeEditorForCreate(): ShortcutResult<HomeEditorData>

    // App mutations
    suspend fun upsertAppShortcut(
        index: Int,
        shortcut: ShortcutDraft,
        isEditing: Boolean,
    ): ShortcutResult<AppEditorData>

    suspend fun deleteAppShortcut(index: Int): ShortcutResult<Unit>

    // Home mutations
    suspend fun upsertHomeShortcut(shortcut: ShortcutDraft): ShortcutResult<PinResult>

    suspend fun deleteHomeShortcut(shortcutId: String): ShortcutResult<Unit>
}
