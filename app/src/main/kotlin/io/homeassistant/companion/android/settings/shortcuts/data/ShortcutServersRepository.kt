package io.homeassistant.companion.android.settings.shortcuts.data

import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutResult
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutServersSnapshot

/** Source of truth for server data needed by shortcut editing. */
internal interface ShortcutServersRepository {
    /** Loads all available shortcut servers and the default server used for fallbacks. */
    suspend fun loadServers(): ShortcutResult<ShortcutServersSnapshot>
}
