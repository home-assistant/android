package io.homeassistant.companion.android.settings.shortcuts.data

import androidx.core.content.pm.ShortcutInfoCompat
import io.homeassistant.companion.android.settings.shortcuts.data.entities.ShortcutDraft

/**
 * Single entry point for combining a platform-owned ID with editable [ShortcutDraft] content.
 */
internal fun interface ShortcutInfoFactory {
    /** Builds an Android shortcut info for [id] from the editable [draft] content. */
    fun create(id: String, draft: ShortcutDraft): ShortcutInfoCompat
}
