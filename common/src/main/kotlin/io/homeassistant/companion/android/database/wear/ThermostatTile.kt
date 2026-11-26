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
    /** Whether or not to show the power button on the tile. */
    @ColumnInfo(name = "show_power_button")
    val showPowerButton: Boolean? = true,
    /** Whether the thermostat is presumed to be on after a power toggle, until the server reports it. */
    @ColumnInfo(name = "presumed_power_on")
    val presumedPowerOn: Boolean? = null,
    /** When [presumedPowerOn] was set, in milliseconds since the epoch. */
    @ColumnInfo(name = "presumed_power_at")
    val presumedPowerAt: Long? = null,
)
