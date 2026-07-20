package io.homeassistant.companion.android.common.data.shortcuts

import io.homeassistant.companion.android.common.data.shortcuts.entities.AppShortcuts
import io.homeassistant.companion.android.common.data.shortcuts.entities.Shortcut
import io.homeassistant.companion.android.common.data.shortcuts.entities.ShortcutDraft
import io.homeassistant.companion.android.common.data.shortcuts.entities.ShortcutResult

/** Source of truth for dynamic app shortcuts managed by the launcher shortcut API. */
interface AppShortcutsRepository {
    /** Loads all configured app shortcuts. */
    suspend fun load(): ShortcutResult<AppShortcuts>

    /** Loads the editable [Shortcut] for [id], defaulting to [defaultServerId] when unconfigured. */
    suspend fun loadEditor(id: String, defaultServerId: Int): ShortcutResult<Shortcut>

    /** Creates a new app shortcut from [draft]. */
    suspend fun create(draft: ShortcutDraft): ShortcutResult<Unit>

    /** Updates the app shortcut identified by [id] with [draft]. */
    suspend fun update(id: String, draft: ShortcutDraft): ShortcutResult<Unit>

    /** Removes the app shortcut identified by [id]. */
    suspend fun delete(id: String): ShortcutResult<Unit>
}
