package io.homeassistant.companion.android.database.wear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the configuration of an entity state complication
 */
@Entity(tableName = "entity_state_complications")
data class EntityStateComplications(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    @ColumnInfo(name = "entityId")
    val entityId: String
)
