package io.homeassistant.companion.android.common.actions

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "wear_actions"
)
data class WearAction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "wear_action_id")
    val id: Long? = null,

    @ColumnInfo(name = "wear_action_icon")
    val icon: Int,

    @ColumnInfo(name = "wear_action_name")
    val name: String,

    @ColumnInfo(name = "wear_action")
    val action: String
)