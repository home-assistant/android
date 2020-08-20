package io.homeassistant.companion.android.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity

@Database(
    entities = [
        Authentication::class,
        Sensor::class,
        ButtonWidgetEntity::class,
        StaticWidgetEntity::class
    ],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao
    abstract fun sensorDao(): SensorDao
    abstract fun buttonWidgetDao(): ButtonWidgetDao
    abstract fun staticWidgetDao(): StaticWidgetDao

    companion object {
        private const val DATABASE_NAME = "HomeAssistantDB"

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
                    MIGRATION_4_5
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
                } catch (exception: SQLiteException) {
                    Log.e(exception.toString(), "SQLiteException in migrate from database version 4 to version 5")
                } catch (exception: Exception) {
                    Log.e(exception.toString(), "Failed to migrate database version 4 to version 5")
                }
            }
        }
    }
}
