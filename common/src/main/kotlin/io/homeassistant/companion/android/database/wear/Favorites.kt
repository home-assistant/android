package io.homeassistant.companion.android.database.wear

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Represents a favorite entity
 */
@Entity(tableName = "favorites")
data class Favorites(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "position")
    val position: Int,
)
