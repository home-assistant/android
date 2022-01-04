package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "button_widgets")
data class ButtonWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "icon_id")
    val iconId: Int,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "service")
    val service: String,
    @ColumnInfo(name = "service_data")
    val serviceData: String,
    @ColumnInfo(name = "label")
    val label: String?
)
