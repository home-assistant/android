package io.homeassistant.companion.android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.database.authentication.Authentication

@Database(
    entities = [
        Authentication::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun authenticationDao(): AuthenticationDao

    companion object {
        private const val DATABASE_NAME = "HomeAssistantDB"
        @Volatile private var instance : AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance?: synchronized(this) {
                instance ?:buildDatabase(context).also { instance = it }
            }
        }
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
        }
    }
}
