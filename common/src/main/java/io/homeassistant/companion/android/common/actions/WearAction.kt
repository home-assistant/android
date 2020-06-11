package io.homeassistant.companion.android.common.actions

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.homeassistant.companion.android.common.database.converters.IconValueConverter
import kotlinx.android.parcel.Parcelize
import net.steamcrafted.materialiconlib.MaterialDrawableBuilder

@Entity(
    tableName = "wear_actions"
)
@Parcelize
@TypeConverters(value = [IconValueConverter::class])
data class WearAction(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "wear_action_id")
    val id: Long? = null,

    @ColumnInfo(name = "wear_action_icon")
    val icon: MaterialDrawableBuilder.IconValue,

    @ColumnInfo(name = "wear_action_name")
    val name: String,

    @ColumnInfo(name = "wear_action")
    val action: String
) : Parcelable
