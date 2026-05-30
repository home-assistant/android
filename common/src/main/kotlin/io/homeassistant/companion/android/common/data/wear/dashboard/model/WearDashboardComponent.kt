package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Semantic dashboard component tree node independent of any Android UI framework.
 */
@Serializable
sealed interface WearDashboardComponent {
    val id: String?
    val visible: WearDashboardBinding?
    val tapAction: WearDashboardAction?
    val holdAction: WearDashboardAction?

    /** Static or dynamically bound text content. */
    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val text: WearDashboardBinding,
    ) : WearDashboardComponent

    /** An icon resolved from a binding, typically an MDI icon name. */
    @Serializable
    @SerialName("icon")
    data class Icon(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val icon: WearDashboardBinding,
    ) : WearDashboardComponent

    /** A tappable control with optional icon and label bindings. */
    @Serializable
    @SerialName("button")
    data class Button(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val text: WearDashboardBinding? = null,
        val icon: WearDashboardBinding? = null,
    ) : WearDashboardComponent

    /** A compact status indicator for entity states such as doors, locks, or batteries. */
    @Serializable
    @SerialName("status_chip")
    data class StatusChip(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val state: WearDashboardBinding,
        val label: WearDashboardBinding? = null,
    ) : WearDashboardComponent

    /** A circular progress indicator bound to a numeric value. */
    @Serializable
    @SerialName("progress_ring")
    data class ProgressRing(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val value: WearDashboardBinding,
        val min: Int = 0,
        val max: Int = 100,
    ) : WearDashboardComponent

    /** Horizontally arranges a limited number of child components. */
    @Serializable
    @SerialName("row")
    data class Row(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val children: List<WearDashboardComponent> = emptyList(),
    ) : WearDashboardComponent

    /** Vertically arranges a limited number of child components. */
    @Serializable
    @SerialName("column")
    data class Column(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val children: List<WearDashboardComponent> = emptyList(),
    ) : WearDashboardComponent

    /** Overlays child components, useful for progress rings with centered text. */
    @Serializable
    @SerialName("box")
    data class Box(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val children: List<WearDashboardComponent> = emptyList(),
    ) : WearDashboardComponent

    /** Renders one branch based on binding truthiness. */
    @Serializable
    @SerialName("conditional")
    data class Conditional(
        override val id: String? = null,
        override val visible: WearDashboardBinding? = null,
        override val tapAction: WearDashboardAction? = null,
        override val holdAction: WearDashboardAction? = null,
        val condition: WearDashboardBinding,
        val then: WearDashboardComponent,
        @SerialName("else")
        val elseComponent: WearDashboardComponent? = null,
    ) : WearDashboardComponent
}
