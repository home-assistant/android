package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay

internal const val RESOURCE_POWER = "power"
private const val BUTTON_SIZE_DP = 40f
private const val TARGET_TEMPERATURE_SIZE_SP = 30f
private const val SPACING_DP = 10f
private const val NAME_ARC_THICKNESS_DP = 30f
private const val FULL_CIRCLE_DEGREES = 360f
private const val HALF_CIRCLE_DEGREES = 180f

/** [entityName] is null when the name isn't shown on the tile. */
@VisibleForTesting
internal fun thermostatTimeline(
    context: Context,
    entity: EntityDisplay,
    entityName: String?,
    targetTemperature: Float?,
    temperatureUnit: String,
): Timeline = Timeline.fromLayoutElement(
    LayoutElementBuilders.Box.Builder().apply {
        addContent(getControlsColumn(context, entity, targetTemperature, temperatureUnit))
        addContent(getStatusRing(context, entity))
        if (entityName != null) addContent(getNameArc(entityName))
        // Refresh button
        addContent(getRefreshButton())
        setModifiers(getRefreshModifiers())
    }.build(),
)

private fun getControlsColumn(
    context: Context,
    entity: EntityDisplay,
    targetTemperature: Float?,
    temperatureUnit: String,
): LayoutElement {
    val hvacAction = entity.climateControls?.hvacAction.toString()

    return LayoutElementBuilders.Column.Builder()
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText(getFriendlyHvacAction(context, hvacAction))
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("${targetTemperature ?: "--"} $temperatureUnit")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder().setSize(
                        DimensionBuilders.sp(TARGET_TEMPERATURE_SIZE_SP),
                    ).build(),
                )
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("${entity.climateControls?.currentTemperature ?: "--"} $temperatureUnit")
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(SPACING_DP)).build(),
        )
        .addContent(getButtonsRow(context, entity, hvacAction))
        .build()
}

private fun getButtonsRow(context: Context, entity: EntityDisplay, hvacAction: String): LayoutElement {
    val buttonsEnabled = hvacAction != "off" && entity.rawState != "unavailable"

    return LayoutElementBuilders.Row.Builder()
        .addContent(getTempButton(context, buttonsEnabled, ThermostatTile.TAP_ACTION_DOWN))
        .addContent(getSpacer())
        .addContent(getPowerButton(context, entity.rawState != "unavailable"))
        .addContent(getSpacer())
        .addContent(getTempButton(context, buttonsEnabled, ThermostatTile.TAP_ACTION_UP))
        .build()
}

private fun getSpacer(): LayoutElement = LayoutElementBuilders.Spacer.Builder()
    .setWidth(DimensionBuilders.dp(SPACING_DP)).build()

private fun getFriendlyHvacAction(context: Context, hvacAction: String): String = when (hvacAction) {
    "heating" -> context.getString(commonR.string.climate_heating)
    "cooling" -> context.getString(commonR.string.climate_cooling)
    "idle" -> context.getString(commonR.string.state_idle)
    "off" -> context.getString(commonR.string.state_off)
    else -> hvacAction.replaceFirstChar { it.uppercase() }
}

private fun getStatusRing(context: Context, entity: EntityDisplay): LayoutElement {
    val hvacActionColor = when (entity.climateControls?.hvacAction.toString()) {
        "heating" -> context.getColor(commonR.color.colorDeviceControlsThermostatHeat)
        "cooling" -> context.getColor(commonR.color.colorDeviceControlsDefaultOn)
        else -> 0x00000000
    }

    return LayoutElementBuilders.Arc.Builder()
        .addContent(
            LayoutElementBuilders.ArcLine.Builder()
                .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
                .setThickness(DimensionBuilders.DpProp.Builder(2f).build())
                .setColor(ColorBuilders.argb(hvacActionColor))
                .build(),
        )
        .build()
}

private fun getNameArc(entityName: String): LayoutElement = LayoutElementBuilders.Arc.Builder()
    .setAnchorAngle(
        DimensionBuilders.DegreesProp.Builder(HALF_CIRCLE_DEGREES).build(),
    )
    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_CENTER)
    .addContent(
        LayoutElementBuilders.ArcLine.Builder()
            .setLength(DimensionBuilders.DegreesProp.Builder(FULL_CIRCLE_DEGREES).build())
            .setThickness(DimensionBuilders.DpProp.Builder(NAME_ARC_THICKNESS_DP).build())
            .setColor(ColorBuilders.argb(0x00000000)) // Fully transparent
            .build(),
    )
    .addContent(
        LayoutElementBuilders.ArcText.Builder()
            .setText(entityName)
            .build(),
    )
    .build()

private fun getTempButton(context: Context, enabled: Boolean, action: String): LayoutElement {
    val clickable = Clickable.Builder()
    if (enabled) {
        clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
            .setId(action)
    }

    return Button.Builder(context, clickable.build())
        .setSize(BUTTON_SIZE_DP)
        .setTextContent(if (action == ThermostatTile.TAP_ACTION_DOWN) "—" else "+")
        .setButtonColors(
            ButtonColors(
                ColorBuilders.argb(
                    context.getColor(
                        if (enabled) commonR.color.colorPrimary else commonR.color.colorDeviceControlsOff,
                    ),
                ),
                ColorBuilders.argb(context.getColor(commonR.color.colorWidgetButtonLabelBlack)),
            ),
        )
        .build()
}

private fun getPowerButton(context: Context, enabled: Boolean): LayoutElement {
    val clickable = Clickable.Builder()
    if (enabled) {
        clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
            .setId(ThermostatTile.TAP_ACTION_TOGGLE)
    }

    return Button.Builder(context, clickable.build())
        .setSize(BUTTON_SIZE_DP)
        .setIconContent(RESOURCE_POWER)
        .setButtonColors(
            ButtonColors(
                ColorBuilders.argb(
                    context.getColor(
                        if (enabled) {
                            android.R.color.white
                        } else {
                            commonR.color.colorDeviceControlsOff
                        },
                    ),
                ),
                ColorBuilders.argb(context.getColor(commonR.color.colorWidgetButtonLabelBlack)),
            ),
        )
        .build()
}
