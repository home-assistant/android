package io.homeassistant.companion.android.tiles

import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_TOP
import androidx.wear.protolayout.ModifiersBuilders
import io.homeassistant.companion.android.data.SimplifiedEntity

/**
 * Places every shortcut of the [layout] at its own position on a round screen of [screenDiameterDp].
 * Each shortcut, built by [shortcutView], is wrapped in a box that is pushed into place with padding.
 */
internal fun shortcutsView(
    entities: List<SimplifiedEntity>,
    layout: ShortcutLayout,
    screenDiameterDp: Float,
    shortcutView: (SimplifiedEntity) -> LayoutElement,
): LayoutElement = Box.Builder().apply {
    setWidth(dp(screenDiameterDp))
    setHeight(dp(screenDiameterDp))
    setHorizontalAlignment(HORIZONTAL_ALIGN_START)
    setVerticalAlignment(VERTICAL_ALIGN_TOP)
    val radius = layout.circleSize / 2
    entities.zip(layout.centers).forEach { (entity, center) ->
        addContent(
            Box.Builder()
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(screenDiameterDp / 2 + center.x - radius))
                                .setTop(dp(screenDiameterDp / 2 + center.y - radius))
                                .build(),
                        )
                        .build(),
                )
                .addContent(shortcutView(entity))
                .build(),
        )
    }
}
    .build()
