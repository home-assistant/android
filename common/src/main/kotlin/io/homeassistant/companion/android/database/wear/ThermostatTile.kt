package io.homeassistant.companion.android.database.wear

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Represents the configuration of a thermostat tile.
 * If the tile was added but not configured, everything except the tile ID will be `null`.
 */
@Entity(tableName = "thermostat_tiles")
data class ThermostatTile(
    /** The system's tile ID */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    /** The climate entity ID */
    @ColumnInfo(name = "entity_id")
    val entityId: String? = null,
    /** The refresh interval of this tile, in seconds */
    @ColumnInfo(name = "refresh_interval")
    val refreshInterval: Long? = null,
    /** The target temperature to allow quick repeated changes */
    @ColumnInfo(name = "target_temperature")
    val targetTemperature: Float? = null,
    /** Whether or not to show the entity name on the tile. */
    @ColumnInfo(name = "show_entity_name")
    val showEntityName: Boolean? = true,
    /** Every control is per-tile; unsupported controls are additionally hidden at runtime. */
    @ColumnInfo(name = "show_hvac_modes", defaultValue = "1")
    val showHvacModes: Boolean = true,
    @ColumnInfo(name = "show_fan_control", defaultValue = "1")
    val showFanControl: Boolean = true,
    @ColumnInfo(name = "show_activity", defaultValue = "1")
    val showActivity: Boolean = true,
    @ColumnInfo(name = "show_secondary_buttons", defaultValue = "0")
    val showSecondaryButtons: Boolean = false,
    @ColumnInfo(name = "secondary_step", defaultValue = "3")
    val secondaryStep: Int = 3,
    /** Classic preserves the original tile; Enhanced uses the fast direct controls. */
    @ColumnInfo(name = "layout_style", defaultValue = "'classic'")
    val layoutStyle: String = "classic",
    /** Temperature units for this tile; default preserves the server unit and original controls. */
    @ColumnInfo(name = "temperature_unit", defaultValue = "'default'")
    val temperatureUnit: String = "default",
)
