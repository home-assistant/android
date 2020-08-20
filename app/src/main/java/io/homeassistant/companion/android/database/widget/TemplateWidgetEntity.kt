package io.homeassistant.companion.android.database.widget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "template_widgets")
data class TemplateWidgetEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "template")
    val template: String
)
