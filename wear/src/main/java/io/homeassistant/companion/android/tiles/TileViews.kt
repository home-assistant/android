package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.splash.SplashActivity
import io.homeassistant.companion.android.common.R as commonR

const val RESOURCE_REFRESH = "refresh"
const val MODIFIER_CLICK_REFRESH = "refresh"

/**
 * A [Timeline] with a single entry, asking the user to log in to the app to start using the tile
 * with a button to open the app. The tile is using the 'Dialog' style.
 */
fun loggedOutTimeline(
    context: Context,
    requestParams: RequestBuilders.TileRequest,
    @StringRes title: Int,
    @StringRes text: Int
): Timeline {
    val theme = Colors(
        ContextCompat.getColor(context, R.color.colorPrimary), // Primary
        ContextCompat.getColor(context, R.color.colorOnPrimary), // On primary
        ContextCompat.getColor(context, R.color.colorOverlay), // Surface
        ContextCompat.getColor(context, android.R.color.white) // On surface
    )
    val chipColors = ChipColors.primaryChipColors(theme)
    val chipAction = ModifiersBuilders.Clickable.Builder()
        .setId("login")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(SplashActivity::class.java.name)
                        .setPackageName(context.packageName)
                        .build()
                ).build()
        ).build()
    return Timeline.fromLayoutElement(
        PrimaryLayout.Builder(requestParams.deviceConfiguration)
            .setPrimaryLabelTextContent(
                Text.Builder(context, context.getString(title))
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(theme.primary))
                    .build()
            )
            .setContent(
                Text.Builder(context, context.getString(text))
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setMaxLines(10)
                    .setColor(argb(theme.onSurface))
                    .build()
            )
            .setPrimaryChipContent(
                CompactChip.Builder(
                    context,
                    context.getString(commonR.string.login),
                    chipAction,
                    requestParams.deviceConfiguration
                )
                    .setChipColors(chipColors)
                    .build()
            )
            .build()
    )
}

/**
 * An [LayoutElementBuilders.Arc] with a refresh button at the bottom (centered). When added, it is
 * expected that the TileService:
 * - handles the refresh action ([MODIFIER_CLICK_REFRESH]) in `onTileRequest`;
 * - adds a resource for [RESOURCE_REFRESH] in `onTileResourcesRequest`.
 */
fun getRefreshButton(): LayoutElementBuilders.Arc =
    LayoutElementBuilders.Arc.Builder()
        .setAnchorAngle(
            DimensionBuilders.DegreesProp.Builder(180f).build()
        )
        .addContent(
            LayoutElementBuilders.ArcAdapter.Builder()
                .setContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(RESOURCE_REFRESH)
                        .setWidth(DimensionBuilders.dp(24f))
                        .setHeight(DimensionBuilders.dp(24f))
                        .setModifiers(getRefreshModifiers())
                        .build()
                )
                .setRotateContents(false)
                .build()
        )
        .build()

/** @return a modifier for tiles that represents a 'tap to refresh' [ActionBuilders.LoadAction] */
fun getRefreshModifiers(): ModifiersBuilders.Modifiers {
    return ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setOnClick(
                    ActionBuilders.LoadAction.Builder().build()
                )
                .setId(MODIFIER_CLICK_REFRESH)
                .build()
        )
        .build()
}
