package io.homeassistant.companion.android.common.data.shortcuts

import androidx.core.content.pm.ShortcutInfoCompat
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutDraft

interface ShortcutFactory {
    fun createShortcutInfo(draft: ShortcutDraft): ShortcutInfoCompat
}
