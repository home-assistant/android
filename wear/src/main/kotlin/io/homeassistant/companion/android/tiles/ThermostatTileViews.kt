package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ButtonDefaults
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay

internal const val RESOURCE_POWER = "power"
private const val COMPACT_BUTTON_SIZE_DP = 40f
private const val TARGET_TEMPERATURE_SIZE_SP = 30f
private const val SPACING_DP = 10f
private const val NAME_ARC_THICKNESS_DP = 30f
private const val FULL_CIRCLE_DEGREES = 360f
private const val HALF_CIRCLE_DEGREES = 180f

/**
 * What a thermostat tile shows. [entityName] is null when the name isn't shown on the tile, and the
 * power button is only drawn when [showPowerButton] is set.
 */
internal data class ThermostatTileContent(
    val entity: EntityDisplay,
    val entityName: String?,
    val targetTemperature: Float?,
    val temperatureUnit: String,
    val showPowerButton: Boolean,
)

internal fun thermostatTimeline(context: Context, content: ThermostatTileContent): Timeline =
    Timeline.fromLayoutElement(
        LayoutElementBuilders.Box.Builder().apply {
            addContent(getControlsColumn(context, content))
            addContent(getStatusRing(context, content.entity))
            content.entityName?.let { addContent(getNameArc(it)) }
            addContent(getRefreshButton())
            setModifiers(getRefreshModifiers())
        }.build(),
    )

private fun getControlsColumn(context: Context, content: ThermostatTileContent): LayoutElement {
    val entity = content.entity
    val hvacAction = entity.climateControls?.hvacAction.toString()

    return LayoutElementBuilders.Column.Builder()
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText(getFriendlyHvacAction(context, hvacAction))
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("${content.targetTemperature ?: "--"} ${content.temperatureUnit}")
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder().setSize(
                        DimensionBuilders.sp(TARGET_TEMPERATURE_SIZE_SP),
                    ).build(),
                )
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Text.Builder()
                .setText("${entity.climateControls?.currentTemperature ?: "--"} ${content.temperatureUnit}")
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(SPACING_DP)).build(),
        )
        .addContent(getButtonsRow(context, entity, hvacAction, content.showPowerButton))
        .build()
}

private fun getButtonsRow(
    context: Context,
    entity: EntityDisplay,
    hvacAction: String,
    showPowerButton: Boolean,
): LayoutElement {
    val buttonsEnabled = hvacAction != "off" && entity.rawState != "unavailable"
    val tempButtonSize = if (showPowerButton) {
        DimensionBuilders.dp(COMPACT_BUTTON_SIZE_DP)
    } else {
        ButtonDefaults.DEFAULT_SIZE
    }

    return LayoutElementBuilders.Row.Builder()
        .addContent(getTempButton(context, buttonsEnabled, ThermostatTile.TAP_ACTION_DOWN, tempButtonSize))
        .apply {
            if (showPowerButton) {
                addContent(getSpacer(SPACING_DP))
                addContent(getPowerButton(context, entity.rawState != "unavailable"))
                addContent(getSpacer(SPACING_DP))
            } else {
                addContent(getSpacer(SPACING_DP * 2))
            }
        }
        .addContent(getTempButton(context, buttonsEnabled, ThermostatTile.TAP_ACTION_UP, tempButtonSize))
        .build()
}

private fun getSpacer(widthDp: Float): LayoutElement = LayoutElementBuilders.Spacer.Builder()
    .setWidth(DimensionBuilders.dp(widthDp)).build()

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

private fun getTempButton(
    context: Context,
    enabled: Boolean,
    action: String,
    size: DimensionBuilders.DpProp,
): LayoutElement {
    val clickable = Clickable.Builder()
    if (enabled) {
        clickable.setOnClick(ActionBuilders.LoadAction.Builder().build())
            .setId(action)
    }

    return Button.Builder(context, clickable.build())
        .setSize(size)
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
        .setSize(COMPACT_BUTTON_SIZE_DP)
        .setIconContent(RESOURCE_POWER)
        .setContentDescription(context.getString(commonR.string.thermostat_tile_power_button_description))
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
