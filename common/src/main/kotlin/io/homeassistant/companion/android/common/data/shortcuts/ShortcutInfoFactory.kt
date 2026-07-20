package io.homeassistant.companion.android.common.data.shortcuts

import androidx.core.content.pm.ShortcutInfoCompat
import io.homeassistant.companion.android.common.data.shortcuts.entities.ShortcutDraft

/**
 * Single entry point for combining a platform-owned ID with editable [ShortcutDraft] content. The interface
 * exists so `common` can persist shortcuts without depending on `app`-module types (notably
 * the launch intent and icon rendering code).
 *
 * It is a `fun interface` so the single-method `app` binding can be provided as a lambda.
 */
fun interface ShortcutInfoFactory {
    /** Builds an Android shortcut info for [id] from the editable [draft] content. */
    fun create(id: String, draft: ShortcutDraft): ShortcutInfoCompat
}
