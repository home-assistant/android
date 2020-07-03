package io.homeassistant.companion.android.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AuthenticationList::class], version = 1)
abstract class AppDataBase : RoomDatabase() {
    abstract fun authenticationDatabaseDao(): AuthenticationDataBaseDao
}
