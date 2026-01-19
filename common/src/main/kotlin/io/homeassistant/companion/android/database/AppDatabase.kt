package io.homeassistant.companion.android.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.location.LocationHistoryDao
import io.homeassistant.companion.android.database.location.LocationHistoryItem
import io.homeassistant.companion.android.database.migration.Migration27to28
import io.homeassistant.companion.android.database.migration.Migration36to37
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.EntriesTypeConverter
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingTypeConverter
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerDao
import io.homeassistant.companion.android.database.settings.LocalNotificationSettingConverter
import io.homeassistant.companion.android.database.settings.LocalSensorSettingConverter
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import io.homeassistant.companion.android.database.wear.FavoriteCaches
import io.homeassistant.companion.android.database.wear.FavoriteCachesDao
import io.homeassistant.companion.android.database.wear.Favorites
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundTypeConverter
import io.homeassistant.companion.android.database.widget.WidgetTapActionConverter

@Database(
    entities = [
        Attribute::class,
        Authentication::class,
        Sensor::class,
        SensorSetting::class,
        ButtonWidgetEntity::class,
        CameraWidgetEntity::class,
        MediaPlayerControlsWidgetEntity::class,
        StaticWidgetEntity::class,
        TodoWidgetEntity::class,
        TemplateWidgetEntity::class,
        NotificationItem::class,
        LocationHistoryItem::class,
        TileEntity::class,
        Favorites::class,
        FavoriteCaches::class,
        CameraTile::class,
        ThermostatTile::class,
        EntityStateComplications::class,
        Server::class,
        Setting::class,
    ],
    version = 51,
    autoMigrations = [
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28, spec = Migration27to28::class),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
        AutoMigration(from = 33, to = 34),
        AutoMigration(from = 34, to = 35),
        AutoMigration(from = 35, to = 36),
        AutoMigration(from = 36, to = 37, spec = Migration36to37::class),
        AutoMigration(from = 38, to = 39),
        AutoMigration(from = 39, to = 40),
        AutoMigration(from = 41, to = 42),
        AutoMigration(from = 42, to = 43),
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 44, to = 45),
        AutoMigration(from = 45, to = 46),
        AutoMigration(from = 46, to = 47),
        AutoMigration(from = 47, to = 48),
        AutoMigration(from = 48, to = 49),
        AutoMigration(from = 49, to = 50),
        AutoMigration(from = 50, to = 51),
    ],
)
@TypeConverters(
    LocalNotificationSettingConverter::class,
    LocalSensorSettingConverter::class,
    EntriesTypeConverter::class,
    SensorSettingTypeConverter::class,
    WidgetBackgroundTypeConverter::class,
    WidgetTapActionConverter::class,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao
    abstract fun sensorDao(): SensorDao
    abstract fun buttonWidgetDao(): ButtonWidgetDao
    abstract fun cameraWidgetDao(): CameraWidgetDao
    abstract fun mediaPlayCtrlWidgetDao(): MediaPlayerControlsWidgetDao
    abstract fun staticWidgetDao(): StaticWidgetDao
    abstract fun todoWidgetDao(): TodoWidgetDao
    abstract fun templateWidgetDao(): TemplateWidgetDao
    abstract fun notificationDao(): NotificationDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun tileDao(): TileDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun favoriteCachesDao(): FavoriteCachesDao
    abstract fun cameraTileDao(): CameraTileDao
    abstract fun thermostatTileDao(): ThermostatTileDao
    abstract fun entityStateComplicationsDao(): EntityStateComplicationsDao
    abstract fun serverDao(): ServerDao
    abstract fun settingsDao(): SettingsDao
}
