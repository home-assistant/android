package io.homeassistant.companion.android.settings.shortcuts.data

import androidx.core.content.pm.ShortcutInfoCompat
import io.homeassistant.companion.android.settings.shortcuts.data.entities.Shortcut
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutListItem

/**
 * Decodes Android shortcut payloads into shortcut data.
 * The concrete implementation lives with the app-owned launch intent contract.
 */
internal interface ShortcutIntentSerializer {
    /**
     * Decodes an Android [shortcut] payload into the editable [Shortcut] model, using [defaultServerId]
     * when the payload has no server. Used when opening a shortcut in the editor, so a usable
     * destination is required.
     *
     * @throws IllegalArgumentException when the shortcut has no usable destination.
     */
    suspend fun decode(shortcut: ShortcutInfoCompat, defaultServerId: Int): Shortcut

    /** Decodes an Android [shortcut] payload into a [ShortcutListItem] for the shortcut list. */
    suspend fun decodeListItem(shortcut: ShortcutInfoCompat): ShortcutListItem
}
