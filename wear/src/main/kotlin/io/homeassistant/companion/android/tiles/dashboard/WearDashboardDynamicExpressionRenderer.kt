package io.homeassistant.companion.android.tiles.dashboard

import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import javax.inject.Inject
import javax.inject.Singleton

private const val PROGRESS_ARC_COLOR = 0xFF03DAC5.toInt()

/**
 * Builds ProtoLayout dynamic expressions with a per-render budget.
 *
 * When the budget is exhausted, callers fall back to static layout values.
 */
@Singleton
class WearDashboardDynamicExpressionRenderer @Inject constructor() {

    private var budgetRemaining: Int = 0

    /**
     * Resets the expression budget for a new render pass.
     */
    fun beginRenderPass(maxExpressions: Int) {
        budgetRemaining = maxExpressions.coerceAtLeast(0)
    }

    /**
     * Returns an [LayoutElementBuilders.Arc] progress ring using a dynamic sweep angle, or `null`
     * when dynamic expressions are unavailable or the budget is exhausted.
     */
    fun renderProgressRing(
        progressFraction: Float,
        thicknessDp: Float = 4f,
    ): LayoutElementBuilders.Arc? {
        val dynamicDegrees = allocateDegrees(progressFraction) ?: return null
        return LayoutElementBuilders.Arc.Builder()
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(
                        DimensionBuilders.DegreesProp.Builder()
                            .setDynamicValue(dynamicDegrees)
                            .build(),
                    )
                    .setThickness(DimensionBuilders.DpProp.Builder(thicknessDp).build())
                    .setColor(androidx.wear.protolayout.ColorBuilders.argb(PROGRESS_ARC_COLOR))
                    .build(),
            )
            .build()
    }

    private fun allocateDegrees(progressFraction: Float): DynamicBuilders.DynamicFloat? {
        if (budgetRemaining <= 0) return null
        budgetRemaining -= 1
        val sweepDegrees = progressFraction.coerceIn(0f, 1f) * 360f
        return DynamicBuilders.DynamicFloat.constant(sweepDegrees)
    }
}
