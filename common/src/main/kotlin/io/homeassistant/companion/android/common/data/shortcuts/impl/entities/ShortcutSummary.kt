package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import androidx.compose.runtime.Immutable
import com.mikepenz.iconics.typeface.IIcon

/**
 * Snapshot of a shortcut stored on the system.
 *
 * @param id The shortcut ID
 * @param selectedIcon The icon selected for this shortcut
 * @param label The short label for the shortcut
 */
@Immutable
data class ShortcutSummary(val id: String, val selectedIcon: IIcon?, val label: String)
