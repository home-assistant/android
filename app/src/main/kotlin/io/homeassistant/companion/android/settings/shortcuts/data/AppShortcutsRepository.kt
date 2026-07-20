package io.homeassistant.companion.android.settings.shortcuts.data

import io.homeassistant.companion.android.settings.shortcuts.data.entities.AppShortcuts
import io.homeassistant.companion.android.settings.shortcuts.data.entities.Shortcut
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutDraft
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutResult

/** Source of truth for dynamic app shortcuts managed by the launcher shortcut API. */
internal interface AppShortcutsRepository {
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
