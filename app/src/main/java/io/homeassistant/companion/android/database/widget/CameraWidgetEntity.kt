package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_widgets")
data class CameraWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "entityId")
    val entityId: String
)
