package io.homeassistant.companion.android.settings.wear

import android.content.Context
import android.content.res.AssetManager
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.wearDashboardJson
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import timber.log.Timber

private const val TEMPLATE_ASSET_PATH = "wear_dashboard_templates"

/**
 * Describes an entity placeholder in a starter dashboard template.
 */
data class WearDashboardEntitySlot(
    val slotId: String,
    @StringRes val labelRes: Int,
    val placeholderEntityId: String,
)

/**
 * A starter dashboard template loaded from bundled JSON assets.
 */
data class WearDashboardTemplateDefinition(
    val templateId: String,
    @StringRes val titleRes: Int,
    val entitySlots: List<WearDashboardEntitySlot>,
)

/**
 * Loads bundled Wear Dashboard starter templates and instantiates them with entity assignments.
 */
class SettingsWearDashboardTemplates @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val assets: AssetManager = context.assets

    /** Returns all available starter templates. */
    fun getTemplateDefinitions(): List<WearDashboardTemplateDefinition> = listOf(
        WearDashboardTemplateDefinition(
            templateId = "home",
            titleRes = commonR.string.wear_dashboard_template_home,
            entitySlots = listOf(
                WearDashboardEntitySlot(
                    slotId = "temperature",
                    labelRes = commonR.string.wear_dashboard_entity_temperature,
                    placeholderEntityId = "sensor.wear_template_home_temperature",
                ),
                WearDashboardEntitySlot(
                    slotId = "occupancy",
                    labelRes = commonR.string.wear_dashboard_entity_occupancy,
                    placeholderEntityId = "binary_sensor.wear_template_home_occupancy",
                ),
                WearDashboardEntitySlot(
                    slotId = "living_room",
                    labelRes = commonR.string.wear_dashboard_entity_living_room_light,
                    placeholderEntityId = "light.wear_template_home_living_room",
                ),
            ),
        ),
        WearDashboardTemplateDefinition(
            templateId = "car",
            titleRes = commonR.string.wear_dashboard_template_car,
            entitySlots = listOf(
                WearDashboardEntitySlot(
                    slotId = "battery",
                    labelRes = commonR.string.wear_dashboard_entity_car_battery,
                    placeholderEntityId = "sensor.wear_template_car_battery",
                ),
                WearDashboardEntitySlot(
                    slotId = "lock",
                    labelRes = commonR.string.wear_dashboard_entity_car_lock,
                    placeholderEntityId = "button.wear_template_car_lock",
                ),
                WearDashboardEntitySlot(
                    slotId = "climate",
                    labelRes = commonR.string.wear_dashboard_entity_car_climate,
                    placeholderEntityId = "button.wear_template_car_climate",
                ),
            ),
        ),
        WearDashboardTemplateDefinition(
            templateId = "energy",
            titleRes = commonR.string.wear_dashboard_template_energy,
            entitySlots = listOf(
                WearDashboardEntitySlot(
                    slotId = "solar",
                    labelRes = commonR.string.wear_dashboard_entity_solar,
                    placeholderEntityId = "sensor.wear_template_energy_solar",
                ),
                WearDashboardEntitySlot(
                    slotId = "grid",
                    labelRes = commonR.string.wear_dashboard_entity_grid,
                    placeholderEntityId = "sensor.wear_template_energy_grid",
                ),
                WearDashboardEntitySlot(
                    slotId = "battery",
                    labelRes = commonR.string.wear_dashboard_entity_home_battery,
                    placeholderEntityId = "sensor.wear_template_energy_battery",
                ),
            ),
        ),
        WearDashboardTemplateDefinition(
            templateId = "security",
            titleRes = commonR.string.wear_dashboard_template_security,
            entitySlots = listOf(
                WearDashboardEntitySlot(
                    slotId = "alarm",
                    labelRes = commonR.string.wear_dashboard_entity_alarm,
                    placeholderEntityId = "alarm_control_panel.wear_template_security_alarm",
                ),
                WearDashboardEntitySlot(
                    slotId = "front_door",
                    labelRes = commonR.string.wear_dashboard_entity_front_door,
                    placeholderEntityId = "lock.wear_template_security_front_door",
                ),
                WearDashboardEntitySlot(
                    slotId = "motion",
                    labelRes = commonR.string.wear_dashboard_entity_motion,
                    placeholderEntityId = "binary_sensor.wear_template_security_motion",
                ),
            ),
        ),
    )

    /**
     * Loads the template definition for [templateId], or `null` when it is unknown.
     */
    fun getTemplateDefinition(templateId: String): WearDashboardTemplateDefinition? {
        return getTemplateDefinitions().firstOrNull { it.templateId == templateId }
    }

    /**
     * Instantiates a dashboard config from [templateId], replacing placeholder entity IDs
     * with the values in [entityAssignments] keyed by slot ID.
     */
    fun instantiateTemplate(
        templateId: String,
        entityAssignments: Map<String, String>,
    ): WearDashboardConfig? {
        val definition = getTemplateDefinition(templateId) ?: return null
        var json = loadTemplateJson(templateId) ?: return null
        definition.entitySlots.forEach { slot ->
            val assignedEntityId = entityAssignments[slot.slotId] ?: return@forEach
            json = json.replace(slot.placeholderEntityId, assignedEntityId)
        }
        return parseDashboardJson(json)
    }

    /** Parses dashboard JSON from a bundled template file. */
    fun loadTemplateConfig(templateId: String): WearDashboardConfig? {
        val json = loadTemplateJson(templateId) ?: return null
        return parseDashboardJson(json)
    }

    private fun loadTemplateJson(templateId: String): String? {
        return try {
            assets.open("$TEMPLATE_ASSET_PATH/$templateId.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load wear dashboard template $templateId")
            null
        }
    }

    private fun parseDashboardJson(json: String): WearDashboardConfig? {
        return try {
            wearDashboardJson.decodeFromString<WearDashboardConfig>(json)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse wear dashboard template JSON")
            null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to parse wear dashboard template JSON")
            null
        }
    }
}
