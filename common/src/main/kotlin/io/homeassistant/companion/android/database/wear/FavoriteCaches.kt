package io.homeassistant.companion.android.database.wear

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Represents a cached favorite entity
 */
@Entity(tableName = "favorite_cache")
data class FavoriteCaches(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** Name to display for the entity, resolved when it was cached. */
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon")
    val icon: String?,
)
