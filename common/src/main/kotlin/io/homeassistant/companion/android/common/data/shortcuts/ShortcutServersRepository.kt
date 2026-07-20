package io.homeassistant.companion.android.common.data.shortcuts

import io.homeassistant.companion.android.common.data.shortcuts.entities.ShortcutResult
import io.homeassistant.companion.android.common.data.shortcuts.entities.ShortcutServersSnapshot

/** Source of truth for server data needed by shortcut editing. */
interface ShortcutServersRepository {
    /** Loads all available shortcut servers and the default server used for fallbacks. */
    suspend fun loadServers(): ShortcutResult<ShortcutServersSnapshot>
}
