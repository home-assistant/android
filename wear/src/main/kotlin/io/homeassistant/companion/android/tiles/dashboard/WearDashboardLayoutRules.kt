package io.homeassistant.companion.android.tiles.dashboard

/**
 * Pure layout rules shared by the ProtoLayout renderer and unit tests.
 */
internal object WearDashboardLayoutRules {

    /**
     * Returns whether a component should render based on its optional visibility binding value.
     */
    fun isVisible(visibleValue: String?): Boolean {
        if (visibleValue == null) return true
        val normalized = visibleValue.trim().lowercase()
        return normalized.isEmpty() ||
            normalized !in HIDDEN_VALUES &&
            normalized != "0" &&
            normalized != "false"
    }

    /**
     * Limits [children] to [maxChildren] while preserving order.
     */
    fun <T> limitChildren(children: List<T>, maxChildren: Int): List<T> =
        if (children.size <= maxChildren) children else children.take(maxChildren)

    /**
     * Returns whether another tree level can be rendered at [depth].
     */
    fun canDescend(depth: Int, maxDepth: Int): Boolean = depth < maxDepth

    private val HIDDEN_VALUES = setOf("false", "off", "no", "hidden", "none")
}
