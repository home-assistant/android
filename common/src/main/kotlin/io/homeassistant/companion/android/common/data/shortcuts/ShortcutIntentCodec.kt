package io.homeassistant.companion.android.common.data.shortcuts

import android.content.Intent
import android.os.Bundle
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutTargetValue

/**
 * Encodes and decodes shortcut intent data for storage and parsing.
 */
interface ShortcutIntentCodec {
    /**
     * Parses a shortcut icon name from extras and resolves legacy icon IDs when needed.
     */
    fun parseIcon(extras: Bundle?, iconIdToName: Map<Int, String>): String?

    /**
     * Parses a shortcut target based on intent extras and the raw shortcut path.
     */
    fun parseTarget(extras: Bundle?, path: String): ShortcutTargetValue

    /**
     * Encodes a shortcut target to the intent path string.
     */
    fun encodeTarget(target: ShortcutTargetValue): String

    /**
     * Adds shortcut extras to an intent for later parsing.
     */
    fun applyShortcutExtras(intent: Intent, target: ShortcutTargetValue, path: String, iconName: String?)
}
