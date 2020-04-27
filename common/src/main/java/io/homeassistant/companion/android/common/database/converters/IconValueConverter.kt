package io.homeassistant.companion.android.common.database.converters

import androidx.room.TypeConverter
import net.steamcrafted.materialiconlib.MaterialDrawableBuilder

object IconValueConverter {

    @JvmStatic
    @TypeConverter
    fun fromIconValue(iconValue: MaterialDrawableBuilder.IconValue): String = iconValue.name

    @JvmStatic
    @TypeConverter
    fun toIconValue(iconValue: String): MaterialDrawableBuilder.IconValue? {
        return MaterialDrawableBuilder.IconValue.values().firstOrNull { value -> value.name == iconValue }
    }

}