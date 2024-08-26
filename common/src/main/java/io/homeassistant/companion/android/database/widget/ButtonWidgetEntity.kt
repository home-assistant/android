package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "button_widgets")
data class ButtonWidgetEntity(
    @PrimaryKey
    override val id: Int,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    override val serverId: Int,
    @ColumnInfo(name = "icon_name")
    val iconName: String,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "service")
    val service: String,
    @ColumnInfo(name = "service_data")
    val serviceData: String,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "icon_color")
    val iconColor: String?,
    @ColumnInfo(name = "background_type", defaultValue = "DAYNIGHT")
    override val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    @ColumnInfo(name = "text_color")
    override val textColor: String? = null,
    @ColumnInfo(name = "require_authentication", defaultValue = "0")
    val requireAuthentication: Boolean
) : WidgetEntity, ThemeableWidgetEntity
