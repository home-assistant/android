package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "multi_widgets")
data class MultiWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "upper_button_present")
    val upperButton: Boolean,
    @ColumnInfo(name = "upper_icon_id")
    val upperIconId: Int?,
    @ColumnInfo(name = "upper_domain")
    val upperDomain: String?,
    @ColumnInfo(name = "upper_service")
    val upperService: String?,
    @ColumnInfo(name = "upper_service_data")
    val upperServiceData: String?,
    @ColumnInfo(name = "lower_button_present")
    val lowerButton: Boolean,
    @ColumnInfo(name = "lower_icon_id")
    val lowerIconId: Int?,
    @ColumnInfo(name = "lower_domain")
    val lowerDomain: String?,
    @ColumnInfo(name = "lower_service")
    val lowerService: String?,
    @ColumnInfo(name = "lower_service_data")
    val lowerServiceData: String?,
    @ColumnInfo(name = "label_type")
    val labelType: Int,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "template")
    val template: String?,
    @ColumnInfo(name = "label_text_size")
    val labelTextSize: Int,
    @ColumnInfo(name = "label_max_lines")
    val labelMaxLines: Int
)
