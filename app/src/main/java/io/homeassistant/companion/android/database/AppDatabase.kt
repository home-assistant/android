package io.homeassistant.companion.android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.widget.ButtonWidget
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidget
import io.homeassistant.companion.android.database.widget.StaticWidgetDao

@Database(
    entities = [
        Authentication::class,
        Sensor::class,
        ButtonWidget::class,
        StaticWidget::class
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao
    abstract fun sensorDao(): SensorDao
    abstract fun buttonWidgetDao(): ButtonWidgetDao
    abstract fun staticWidgetDao(): StaticWidgetDao

    companion object {
        private const val DATABASE_NAME = "HomeAssistantDB"
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }
        private fun buildDatabase(context: Context): AppDatabase {
            return Room
                .databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .allowMainThreadQueries()
                .build()
        }
    }
}
