package io.homeassistant.companion.android.tiles

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.splash.SplashActivity

const val RESOURCE_REFRESH = "refresh"
const val MODIFIER_CLICK_REFRESH = "refresh"

/** Performs a [VibrationEffect.EFFECT_CLICK] or equivalent on older Android versions */
fun hapticClick(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService<VibratorManager>()
        val vibrator = vibratorManager?.defaultVibrator
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    } else {
        val vibrator = context.getSystemService<Vibrator>()
        @Suppress("DEPRECATION")
        vibrator?.vibrate(200)
    }
}

/**
 * A [Timeline] with a single entry, asking the user to log in to the app to start using the tile
 * with a button to open the app. The tile is using the 'Dialog' style.
 */
fun loggedOutTimeline(
    context: Context,
    requestParams: RequestBuilders.TileRequest,
    @StringRes title: Int,
    @StringRes text: Int,
): Timeline = primaryLayoutTimeline(
    context = context,
    requestParams = requestParams,
    title = title,
    text = text,
    actionText = commonR.string.login,
    action = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setClassName(SplashActivity::class.java.name)
                .setPackageName(context.packageName)
                .build(),
        ).build(),
)

/**
 * A [Timeline] with a single entry using the Material `PrimaryLayout`. The title is optional.
 */
fun primaryLayoutTimeline(
    context: Context,
    requestParams: RequestBuilders.TileRequest,
    @StringRes title: Int?,
    @StringRes text: Int,
    @StringRes actionText: Int,
    action: ActionBuilders.Action,
): Timeline {
    val theme = Colors(
        ContextCompat.getColor(context, commonR.color.colorPrimary),
        ContextCompat.getColor(context, commonR.color.colorOnPrimary),
        // Surface
        ContextCompat.getColor(context, R.color.colorOverlay),
        // On surface
        ContextCompat.getColor(context, android.R.color.white),
    )
    val chipColors = ChipColors.primaryChipColors(theme)
    val chipAction = Clickable.Builder()
        .setId("action")
        .setOnClick(action)
        .build()
    val builder = PrimaryLayout.Builder(requestParams.deviceConfiguration)
    if (title != null) {
        builder.setPrimaryLabelTextContent(
            Text.Builder(context, context.getString(title))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .setColor(argb(theme.primary))
                .build(),
        )
    }
    builder.setContent(
        Text.Builder(context, context.getString(text))
            .setTypography(Typography.TYPOGRAPHY_BODY1)
            // It is highly recommended that main content has [if] 1 label is present: content with max 3 lines
            .setMaxLines(if (title != null) 3 else 4)
            .setColor(argb(theme.onSurface))
            .build(),
    )
    builder.setPrimaryChipContent(
        CompactChip.Builder(
            context,
            context.getString(actionText),
            chipAction,
            requestParams.deviceConfiguration,
        )
            .setChipColors(chipColors)
            .build(),
    )
    return Timeline.fromLayoutElement(builder.build())
}

/**
 * An [LayoutElementBuilders.Arc] with a refresh button at the bottom (centered). When added, it is
 * expected that the TileService:
 * - handles the refresh action ([MODIFIER_CLICK_REFRESH]) in `onTileRequest`;
 * - adds a resource for [RESOURCE_REFRESH] in `onTileResourcesRequest`.
 */
fun getRefreshButton(): LayoutElementBuilders.Arc = LayoutElementBuilders.Arc.Builder()
    .setAnchorAngle(
        DimensionBuilders.DegreesProp.Builder(180f).build(),
    )
    .addContent(
        LayoutElementBuilders.ArcAdapter.Builder()
            .setContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(RESOURCE_REFRESH)
                    .setWidth(DimensionBuilders.dp(24f))
                    .setHeight(DimensionBuilders.dp(24f))
                    .setModifiers(getRefreshModifiers())
                    .build(),
            )
            .setRotateContents(false)
            .build(),
    )
    .build()

/** @return a modifier for tiles that represents a 'tap to refresh' [ActionBuilders.LoadAction] */
fun getRefreshModifiers(): ModifiersBuilders.Modifiers {
    return ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            Clickable.Builder()
                .setOnClick(
                    ActionBuilders.LoadAction.Builder().build(),
                )
                .setId(MODIFIER_CLICK_REFRESH)
                .build(),
        )
        .build()
}

fun getNotConfiguredTimeline(
    context: Context,
    requestParams: RequestBuilders.TileRequest,
    @StringRes title: Int,
    launchMode: HomeActivity.Companion.LaunchMode,
): Timeline {
    val theme = Colors(
        ContextCompat.getColor(context, commonR.color.colorPrimary),
        ContextCompat.getColor(context, commonR.color.colorOnPrimary),
        ContextCompat.getColor(context, R.color.colorOverlay),
        ContextCompat.getColor(context, android.R.color.white),
    )
    val chipColors = ChipColors.primaryChipColors(theme)
    val notConfiguredTimeline = Timeline.fromLayoutElement(
        LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(context.getString(title))
                    .setMaxLines(10)
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(10f)).build(),
            )
            .addContent(
                LayoutElementBuilders.Row.Builder()
                    .addContent(
                        CompactChip.Builder(
                            context,
                            Clickable.Builder()
                                .setOnClick(
                                    HomeActivity.getLaunchAction(
                                        context.packageName,
                                        requestParams.tileId,
                                        launchMode,
                                    ),
                                )
                                .build(),
                            requestParams.deviceConfiguration,
                        )
                            .setTextContent(context.getString(commonR.string.open_settings))
                            .setChipColors(chipColors)
                            .build(),
                    ).build(),
            )
            .build(),
    )
    return notConfiguredTimeline
}
