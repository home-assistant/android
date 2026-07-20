package io.homeassistant.companion.android.settings.shortcuts.data.entities

import androidx.compose.runtime.Immutable

private const val ENTITY_ID_DOMAIN = """(?!_)(?![0-9a-z_]*__)[0-9a-z_]+(?<!_)"""
private const val ENTITY_ID_OBJECT_ID = """(?!_)[0-9a-z_]+(?<!_)"""
private val ENTITY_ID_PATTERN = Regex("""^$ENTITY_ID_DOMAIN\.$ENTITY_ID_OBJECT_ID$""")

@Immutable
internal sealed interface ShortcutDestination {
    data class Dashboard(val path: String) : ShortcutDestination

    data class Entity(val entityId: String) : ShortcutDestination
}

/**
 * Shortcut destination validity is persistence/navigation policy, not part of the destination
 * value itself. Keeping it as an extension keeps callers readable without making validation a
 * sealed-interface contract.
 */
internal val ShortcutDestination.isValid: Boolean
    get() = when (this) {
        is ShortcutDestination.Dashboard -> path.isNotBlank() &&
            path.startsWith("/") &&
            !path.startsWith("//")

        is ShortcutDestination.Entity -> ENTITY_ID_PATTERN.matches(entityId)
    }
