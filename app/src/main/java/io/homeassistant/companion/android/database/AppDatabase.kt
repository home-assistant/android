package io.homeassistant.companion.android.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity

@Database(
    entities = [
        Attribute::class,
        Authentication::class,
        Sensor::class,
        ButtonWidgetEntity::class,
        StaticWidgetEntity::class,
        TemplateWidgetEntity::class
    ],
    version = 10
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao
    abstract fun sensorDao(): SensorDao
    abstract fun buttonWidgetDao(): ButtonWidgetDao
    abstract fun staticWidgetDao(): StaticWidgetDao
    abstract fun templateWidgetDao(): TemplateWidgetDao

    companion object {
        private const val DATABASE_NAME = "HomeAssistantDB"
        internal const val TAG = "AppDatabase"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
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
                    MIGRATION_9_10
                )
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
                while (cursor.moveToNext()) {
                    sensors.add(ContentValues().also {
                        it.put("id", cursor.getString(cursor.getColumnIndex("unique_id")))
                        it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                        it.put("registered", cursor.getInt(cursor.getColumnIndex("registered")))
                        it.put("state", "")
                        it.put("state_type", "")
                        it.put("type", "")
                        it.put("icon", "")
                        it.put("name", "")
                        it.put("device_class", "")
                    })
                }
                cursor.close()
                database.execSQL("DROP TABLE IF EXISTS `sensors`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))")
                sensors.forEach {
                    database.insert("sensors", OnConflictStrategy.REPLACE, it)
                }

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
                while (cursor.moveToNext()) {
                    sensors.add(ContentValues().also {
                        it.put("id", cursor.getString(cursor.getColumnIndex("id")))
                        it.put("enabled", cursor.getInt(cursor.getColumnIndex("enabled")))
                        it.put("registered", cursor.getInt(cursor.getColumnIndex("registered")))
                        it.put("state", "")
                        it.put("last_sent_state", "")
                        it.put("state_type", "")
                        it.put("type", "")
                        it.put("icon", "")
                        it.put("name", "")
                    })
                }
                cursor.close()
                database.execSQL("DROP TABLE IF EXISTS `sensors`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `sensors` (`id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `registered` INTEGER NOT NULL, `state` TEXT NOT NULL, `last_sent_state` TEXT NOT NULL, `state_type` TEXT NOT NULL, `type` TEXT NOT NULL, `icon` TEXT NOT NULL, `name` TEXT NOT NULL, `device_class` TEXT, `unit_of_measurement` TEXT, PRIMARY KEY(`id`))")
                sensors.forEach {
                    database.insert("sensors", OnConflictStrategy.REPLACE, it)
                }
            }
        }
    }
}
