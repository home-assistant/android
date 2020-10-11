package io.homeassistant.companion.android.database

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Database
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.Setting
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        Attribute::class,
        Authentication::class,
        Sensor::class,
        Setting::class,
        ButtonWidgetEntity::class,
        MediaPlayerControlsWidgetEntity::class,
        StaticWidgetEntity::class,
        TemplateWidgetEntity::class
    ],
    version = 12
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao
    abstract fun sensorDao(): SensorDao
    abstract fun buttonWidgetDao(): ButtonWidgetDao
    abstract fun mediaPlayCtrlWidgetDao(): MediaPlayerControlsWidgetDao
    abstract fun staticWidgetDao(): StaticWidgetDao
    abstract fun templateWidgetDao(): TemplateWidgetDao

    companion object {
        private const val DATABASE_NAME = "HomeAssistantDB"
        internal const val TAG = "AppDatabase"
        const val channelId = "App Database"
        const val NOTIFICATION_ID = 45
        lateinit var appContext: Context
        lateinit var integrationRepository: IntegrationRepository

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            appContext = context
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            appContext = context
            return Room
                .databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .allowMainThreadQueries()
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12
                )
                .fallbackToDestructiveMigration()
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`unique_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`unique_id`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `button_widgets` (`id` INTEGER NOT NULL, `icon_id` INTEGER NOT NULL, `domain` TEXT NOT NULL, `service` TEXT NOT NULL, `service_data` TEXT NOT NULL, `label` TEXT, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_id` TEXT, `label` TEXT, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `static_widget` ADD `text_size` FLOAT NOT NULL DEFAULT '30'")
                database.execSQL("ALTER TABLE `static_widget` ADD `separator` TEXT NOT NULL DEFAULT ' '")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `template_widgets` (`id` INTEGER NOT NULL, `template` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    val contentValues: ArrayList<ContentValues> = ArrayList()
                    val widgets = database.query("SELECT * FROM `static_widget`")
                    widgets.use {
                        if (widgets.count > 0) {
                            while (widgets.moveToNext()) {
                                val cv = ContentValues()
                                cv.put("id", widgets.getInt(widgets.getColumnIndex("id")))
                                cv.put("entity_id", widgets.getString(widgets.getColumnIndex("entity_id")))
                                cv.put("attribute_ids", widgets.getString(widgets.getColumnIndex("attribute_id")))
                                cv.put("label", widgets.getString(widgets.getColumnIndex("label")))
                                cv.put("text_size", widgets.getFloat(widgets.getColumnIndex("text_size")))
                                cv.put("state_separator", widgets.getString(widgets.getColumnIndex("separator")))
                                cv.put("attribute_separator", " ")
                                contentValues.add(cv)
                            }
                            database.execSQL("DROP TABLE IF EXISTS `static_widget`")
                            database.execSQL("CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_ids` TEXT, `label` TEXT, `text_size` FLOAT NOT NULL DEFAULT '30', `state_separator` TEXT NOT NULL DEFAULT '', `attribute_separator` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
                            for (cv in contentValues) {
                                database.insert("static_widget", 0, cv)
                            }
                        } else {
                            database.execSQL("DROP TABLE IF EXISTS `static_widget`")
                            database.execSQL("CREATE TABLE IF NOT EXISTS `static_widget` (`id` INTEGER NOT NULL, `entity_id` TEXT NOT NULL, `attribute_ids` TEXT, `label` TEXT, `text_size` FLOAT NOT NULL DEFAULT '30', `state_separator` TEXT NOT NULL DEFAULT '', `attribute_separator` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
                        }
                    }
                    widgets.close()
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to migrate database version 5 to version 6", exception)
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("SELECT * FROM sensors")
                val sensors = mutableListOf<ContentValues>()
                var migrationSuccessful = false
                var migrationFailed = false
                if (cursor.moveToFirst()) {
                    try {
                        while (cursor.moveToNext()) {
                            sensors.add(ContentValues().also {
                                it.put("id", cursor.getString(cursor.getColumnIndex("unique_id")))
                                it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                                it.put(
                                    "registered",
                                    cursor.getInt(cursor.getColumnIndex("registered"))
                                )
                                it.put("state", "")
                                it.put("state_type", "")
                                it.put("type", "")
                                it.put("icon", "")
                                it.put("name", "")
                                it.put("device_class", "")
                            })
                        }
                        migrationSuccessful = true
                    } catch (e: Exception) {
                        migrationFailed = true
                        Log.e(TAG, "Unable to migrate, proceeding with recreating the table", e)
                    }
                }
                cursor.close()
                database.execSQL("DROP TABLE IF EXISTS `sensors`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))")
                if (migrationSuccessful) {
                    sensors.forEach {
                        database.insert("sensors", OnConflictStrategy.REPLACE, it)
                    }
                }
                if (migrationFailed)
                    notifyMigrationFailed()

                database.execSQL("CREATE TABLE IF NOT EXISTS `sensor_attributes` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`sensor_id`, `name`))")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sensor_attributes` ADD `value_type` TEXT NOT NULL DEFAULT 'string'")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sensors` ADD `state_changed` INTEGER NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("SELECT * FROM sensors")
                val sensors = mutableListOf<ContentValues>()
                var migrationSuccessful = false
                var migrationFailed = false
                if (cursor.moveToFirst()) {
                    try {
                        while (cursor.moveToNext()) {
                            sensors.add(ContentValues().also {
                                it.put("id", cursor.getString(cursor.getColumnIndex("id")))
                                it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                                it.put(
                                    "registered",
                                    cursor.getInt(cursor.getColumnIndex("registered"))
                                )
                                it.put("state", "")
                                it.put("last_sent_state", "")
                                it.put("state_type", "")
                                it.put("type", "")
                                it.put("icon", "")
                                it.put("name", "")
                            })
                        }
                        migrationSuccessful = true
                    } catch (e: Exception) {
                        migrationFailed = true
                        Log.e(TAG, "Unable to migrate, proceeding with recreating the table", e)
                    }
                }
                cursor.close()
                database.execSQL("DROP TABLE IF EXISTS `sensors`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `last_sent_state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))")
                if (migrationSuccessful) {
                    sensors.forEach {
                        database.insert("sensors", OnConflictStrategy.REPLACE, it)
                    }
                }
                if (migrationFailed)
                    notifyMigrationFailed()
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensor_settings` (`sensor_id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, `value_type` TEXT NOT NULL DEFAULT 'string', PRIMARY KEY(`sensor_id`, `name`))")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `mediaplayctrls_widgets` (`id` INTEGER NOT NULL, `entityId` TEXT NOT NULL, `label` TEXT, `showSkip` INTEGER NOT NULL, `showSeek` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                var notificationChannel =
                    notificationManager?.getNotificationChannel(channelId)
                if (notificationChannel == null) {
                    notificationChannel = NotificationChannel(
                        channelId, TAG, NotificationManager.IMPORTANCE_HIGH
                    )
                    notificationManager?.createNotificationChannel(notificationChannel)
                }
            }
        }

        private fun notifyMigrationFailed() {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_ic_notification)
                .setContentTitle(appContext.getString(R.string.database_migration_failed))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            with(NotificationManagerCompat.from(appContext)) {
                notify(NOTIFICATION_ID, notification)
            }
            runBlocking {
                try {
                    integrationRepository.fireEvent("mobile_app.migration_failed", mapOf())
                    Log.d(TAG, "Event sent to Home Assistant")
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send event to Home Assistant", e)
                    Toast.makeText(appContext, R.string.database_event_failure, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
