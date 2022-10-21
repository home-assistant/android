package io.homeassistant.companion.android.database.wear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a favorite entity
 */
@Entity(tableName = "cache")
data class Caches(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "friendly_name")
    val friendly_name: String,
    @ColumnInfo(name = "icon")
    val icon: String?
)
