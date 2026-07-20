package io.homeassistant.companion.android.settings.shortcuts.data

import io.homeassistant.companion.android.settings.shortcuts.data.entities.HomeShortcutListItem
import io.homeassistant.companion.android.settings.shortcuts.data.entities.Shortcut
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutDraft
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutResult

/** Source of truth for pinned home-screen shortcuts managed by the launcher shortcut API. */
internal interface HomeShortcutsRepository {
    /** Whether the current platform supports pinning home-screen shortcuts. */
    fun canPinShortcuts(): Boolean

    /** Loads the list of pinned home shortcuts. */
    suspend fun load(): ShortcutResult<List<HomeShortcutListItem>>

    /** Loads the editable [Shortcut] for [id], defaulting to [defaultServerId] when unconfigured. */
    suspend fun loadEditor(id: String, defaultServerId: Int): ShortcutResult<Shortcut>

    /** Creates a new pinned home shortcut from [draft]. */
    suspend fun create(draft: ShortcutDraft): ShortcutResult<Unit>

    /** Updates the pinned home shortcut identified by [id] with [draft]. */
    suspend fun update(id: String, draft: ShortcutDraft): ShortcutResult<Unit>

    /** Disables the pinned home shortcut identified by [id]. */
    suspend fun disable(id: String): ShortcutResult<Unit>
}
