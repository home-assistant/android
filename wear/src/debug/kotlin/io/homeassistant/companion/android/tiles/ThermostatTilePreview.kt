package io.homeassistant.companion.android.tiles

import android.content.Context
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.ChevronDown
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.wear.ThermostatTile as ThermostatTileConfig

private const val PREVIEW_NAME = "Example thermostat"

private val mockTileConfig = ThermostatTileConfig(
    id = 0,
    entityId = "climate.living_room",
    showEntityName = true,
)

private fun mockEntity(rawState: String, hvacAction: String, targetTemp: Float?): EntityDisplay =
    EntityDisplayWithoutContext(
        entityId = "climate.living_room",
        name = PREVIEW_NAME,
        icon = Mdi.ChevronDown,
        rawState = rawState,
        climateControls = ClimateControls(
            currentTemperature = 19.4f,
            targetTemperature = targetTemp,
            targetTemperatureStep = 0.5f,
            hvacAction = hvacAction,
            minTemperature = 5f,
            maxTemperature = 30f,
            temperatureUnit = "°C",
            hvacModes = listOf("off", "heat", "cool"),
            supportsTargetTemperature = true,
        ),
    )

private fun previewResources(): ResourceBuilders.Resources = ResourceBuilders.Resources.Builder()
    .setVersion("preview")
    .addIdToImageMapping(
        RESOURCE_REFRESH,
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(R.drawable.ic_refresh)
                    .build(),
            ).build(),
    )
    .addIdToImageMapping(
        ThermostatTile.RESOURCE_POWER,
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(R.drawable.ic_power)
                    .build(),
            ).build(),
    )
    .build()

private fun previewData(context: Context, entity: EntityDisplay, targetTemp: Float?) = TilePreviewData(
    onTileResourceRequest = { previewResources() },
) {
    Tile.Builder()
        .setResourcesVersion("preview")
        .setTileTimeline(
            ThermostatTile.timeline(context, mockTileConfig, entity, PREVIEW_NAME, targetTemp, "°C"),
        )
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
fun thermostatHeatingPreview(context: Context): TilePreviewData =
    previewData(context, mockEntity("heat", "heating", 19.5f), 19.5f)

@Preview(device = WearDevices.SMALL_ROUND)
fun thermostatCoolingPreview(context: Context): TilePreviewData =
    previewData(context, mockEntity("cool", "cooling", 24f), 24f)

@Preview(device = WearDevices.SMALL_ROUND)
fun thermostatIdlePreview(context: Context): TilePreviewData =
    previewData(context, mockEntity("heat", "idle", 21.5f), 21.5f)

@Preview(device = WearDevices.SMALL_ROUND)
fun thermostatOffPreview(context: Context): TilePreviewData =
    previewData(context, mockEntity("off", "off", null), null)
