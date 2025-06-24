package io.homeassistant.companion.android.database.wear

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a cached favorite entity
 */
@Entity(tableName = "favorite_cache")
data class FavoriteCaches(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "friendly_name")
    val friendlyName: String,
    @ColumnInfo(name = "icon")
    val icon: String?,
)
