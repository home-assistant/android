package io.homeassistant.companion.android.database.widget

import androidx.room.TypeConverter

enum class CameraWidgetTapAction {
    OPEN_CAMERA,
    UPDATE_IMAGE
}

class CameraWidgetActionConverter {

    @TypeConverter
    fun toDomainType(setting: String): CameraWidgetTapAction = CameraWidgetTapAction.valueOf(setting)

    @TypeConverter
    fun toDatabaseType(setting: CameraWidgetTapAction): String = setting.name
}
